package com.flashsale.service;

import com.flashsale.dto.PurchaseRequest;
import com.flashsale.entity.Product;
import com.flashsale.exception.InsufficientStockException;
import com.flashsale.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spins up throwaway Postgres and Redis containers (via Testcontainers) just for
 * this test class, so it never touches your real dev database and behaves
 * identically whether run locally or in CI. Fires 50 truly concurrent purchase
 * attempts at a product seeded with only 10 units of stock, and asserts that
 * exactly 10 succeed, exactly 40 are correctly rejected, and the final stock
 * in the database is exactly zero — proving no overselling occurred.
 */
@Testcontainers
@SpringBootTest
class ConcurrencyIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("flashdb")
            .withUsername("flashuser")
            .withPassword("flashpass");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("redisson.address", () -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
    }

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private FlashSaleService flashSaleService;

    private static final int INITIAL_STOCK = 10;
    private static final int CONCURRENT_REQUESTS = 50;

    private Long testProductId;

    @BeforeEach
    void setUp() {
        Product product = Product.builder()
                .title("Concurrency Test Product")
                .description("Used only by ConcurrencyIntegrationTest")
                .price(new BigDecimal("9.99"))
                .stockQuantity(INITIAL_STOCK)
                .build();
        testProductId = productRepository.save(product).getId();
    }

    @Test
    void fiftyConcurrentPurchases_shouldNeverOversell_underOptimisticLock() throws InterruptedException {
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_REQUESTS);

        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            final long userId = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    // Every thread waits here until ALL threads are ready, so they
                    // all fire as close to simultaneously as the JVM allows —
                    // this is what actually creates real contention in the test.
                    startLatch.await();

                    PurchaseRequest request = PurchaseRequest.builder()
                            .userId(userId)
                            .productId(testProductId)
                            .quantity(1)
                            .build();

                    flashSaleService.purchaseWithOptimisticLock(request);
                    successCount.incrementAndGet();
                } catch (InsufficientStockException | IllegalStateException ex) {
                    rejectedCount.incrementAndGet();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(10, TimeUnit.SECONDS);
        startLatch.countDown();
        boolean completedInTime = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completedInTime, "All 50 threads should finish within the timeout.");
        assertEquals(INITIAL_STOCK, successCount.get(),
                "Exactly as many purchases should succeed as there was stock available.");
        assertEquals(CONCURRENT_REQUESTS - INITIAL_STOCK, rejectedCount.get(),
                "The remaining requests should be correctly rejected as out of stock.");

        Product finalProduct = productRepository.findById(testProductId).orElseThrow();
        assertEquals(0, finalProduct.getStockQuantity(),
                "Final stock must be exactly zero — proof that no overselling occurred.");
    }
}