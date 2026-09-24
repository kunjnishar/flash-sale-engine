package com.flashsale.service.impl;

import com.flashsale.dto.OrderResponse;
import com.flashsale.dto.PurchaseRequest;
import com.flashsale.entity.Order;
import com.flashsale.entity.OrderStatus;
import com.flashsale.entity.Product;
import com.flashsale.exception.InsufficientStockException;
import com.flashsale.exception.ProductNotFoundException;
import com.flashsale.repository.OrderRepository;
import com.flashsale.repository.ProductRepository;
import com.flashsale.service.FlashSaleService;
import com.flashsale.service.RedisStockCacheService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class FlashSaleServiceImpl implements FlashSaleService {

    private static final int MAX_OPTIMISTIC_RETRY_ATTEMPTS = 5;
    private static final long DISTRIBUTED_LOCK_WAIT_SECONDS = 5L;
    private static final long DISTRIBUTED_LOCK_LEASE_SECONDS = 10L;

    private static final long REDIS_CACHE_MISS = -2L;
    private static final long REDIS_INSUFFICIENT_STOCK = -1L;

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final RedissonClient redissonClient;
    private final RedisStockCacheService redisStockCacheService;
    private final TransactionTemplate transactionTemplate;

    public FlashSaleServiceImpl(ProductRepository productRepository,
                                 OrderRepository orderRepository,
                                 RedissonClient redissonClient,
                                 RedisStockCacheService redisStockCacheService,
                                 PlatformTransactionManager transactionManager) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.redissonClient = redissonClient;
        this.redisStockCacheService = redisStockCacheService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // ---------------------------------------------------------------
    // STRATEGY 1: PESSIMISTIC LOCKING (SELECT ... FOR UPDATE)
    // ---------------------------------------------------------------
    @Override
    @Transactional
    public OrderResponse purchaseWithPessimisticLock(PurchaseRequest request) {
        long startTime = System.currentTimeMillis();

        Product product = productRepository.findByIdForUpdate(request.getProductId())
                .orElseThrow(() -> new ProductNotFoundException(
                        "Product not found with id: " + request.getProductId()));

        validateStock(product, request.getQuantity());

        product.setStockQuantity(product.getStockQuantity() - request.getQuantity());
        productRepository.save(product);

        Order order = buildAndSaveOrder(product, request, OrderStatus.CONFIRMED);

        long executionTimeMs = System.currentTimeMillis() - startTime;

        log.info("[PESSIMISTIC] Order {} confirmed for product {} in {} ms",
                order.getOrderTrackingNumber(), product.getId(), executionTimeMs);

        return buildOrderResponse(order, product.getTitle(),
                "Purchase confirmed via PESSIMISTIC_WRITE database lock.", executionTimeMs);
    }

    // ---------------------------------------------------------------
    // STRATEGY 2: OPTIMISTIC LOCKING (@Version + automatic retry)
    // ---------------------------------------------------------------
    @Override
    public OrderResponse purchaseWithOptimisticLock(PurchaseRequest request) {
        long startTime = System.currentTimeMillis();
        int attempt = 0;

        while (attempt < MAX_OPTIMISTIC_RETRY_ATTEMPTS) {
            attempt++;
            try {
                PurchaseResult result = transactionTemplate.execute(
                        status -> executeOptimisticPurchase(request));

                long executionTimeMs = System.currentTimeMillis() - startTime;

                log.info("[OPTIMISTIC] Order {} confirmed for product {} after {} attempt(s) in {} ms",
                        result.order().getOrderTrackingNumber(), request.getProductId(), attempt, executionTimeMs);

                return buildOrderResponse(result.order(), result.productTitle(),
                        "Purchase confirmed via OPTIMISTIC (@Version) lock after " + attempt + " attempt(s).",
                        executionTimeMs);

            } catch (ObjectOptimisticLockingFailureException ex) {
                log.warn("[OPTIMISTIC] Version conflict on attempt {} for product {}. Retrying...",
                        attempt, request.getProductId());

                if (attempt >= MAX_OPTIMISTIC_RETRY_ATTEMPTS) {
                    throw new IllegalStateException(
                            "Purchase failed after " + MAX_OPTIMISTIC_RETRY_ATTEMPTS
                                    + " optimistic lock retries due to high contention on product "
                                    + request.getProductId() + ". Please try again.", ex);
                }

                sleepBeforeRetry(attempt);
            }
        }

        throw new IllegalStateException("Purchase could not be completed for product " + request.getProductId());
    }

    private PurchaseResult executeOptimisticPurchase(PurchaseRequest request) {
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ProductNotFoundException(
                        "Product not found with id: " + request.getProductId()));

        validateStock(product, request.getQuantity());

        product.setStockQuantity(product.getStockQuantity() - request.getQuantity());
        productRepository.save(product);

        Order order = buildAndSaveOrder(product, request, OrderStatus.CONFIRMED);

        return new PurchaseResult(order, product.getTitle());
    }

    // ---------------------------------------------------------------
    // STRATEGY 3: REDISSON DISTRIBUTED LOCK
    // ---------------------------------------------------------------
    @Override
    public OrderResponse purchaseWithDistributedLock(PurchaseRequest request) {
        long startTime = System.currentTimeMillis();
        String lockKey = "lock:product:" + request.getProductId();
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired;
        try {
            acquired = lock.tryLock(DISTRIBUTED_LOCK_WAIT_SECONDS, DISTRIBUTED_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while waiting for distributed lock on product " + request.getProductId(), ex);
        }

        if (!acquired) {
            throw new IllegalStateException(
                    "Could not acquire distributed lock for product " + request.getProductId()
                            + " within " + DISTRIBUTED_LOCK_WAIT_SECONDS + " seconds. Please try again.");
        }

        try {
            PurchaseResult result = transactionTemplate.execute(status -> executeOptimisticPurchase(request));

            long executionTimeMs = System.currentTimeMillis() - startTime;

            log.info("[DISTRIBUTED] Order {} confirmed for product {} in {} ms",
                    result.order().getOrderTrackingNumber(), request.getProductId(), executionTimeMs);

            return buildOrderResponse(result.order(), result.productTitle(),
                    "Purchase confirmed via Redisson distributed lock.", executionTimeMs);

        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    // ---------------------------------------------------------------
    // STRATEGY 4: REDIS + LUA ATOMIC IN-MEMORY STOCK CHECK
    // ---------------------------------------------------------------
    @Override
    @Transactional
    public OrderResponse purchaseWithRedisLua(PurchaseRequest request) {
        long startTime = System.currentTimeMillis();
        Long productId = request.getProductId();
        Integer quantity = request.getQuantity();

        long result = redisStockCacheService.decrementStock(productId, quantity);

        if (result == REDIS_CACHE_MISS) {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + productId));
            redisStockCacheService.warmStock(product.getId(), product.getStockQuantity());
            result = redisStockCacheService.decrementStock(productId, quantity);

            if (result == REDIS_CACHE_MISS) {
                throw new IllegalStateException("Unable to warm Redis stock cache for product " + productId);
            }
        }

        if (result == REDIS_INSUFFICIENT_STOCK) {
            throw new InsufficientStockException(
                    "Insufficient stock (Redis cache) for product id " + productId + ". Requested: " + quantity);
        }

        // This request won the atomic Redis reservation — now persist it to Postgres.
        // If anything below fails, we give the reserved stock back to Redis in the catch block.
        try {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + productId));

            int updatedRows = productRepository.decrementStock(productId, quantity);
            if (updatedRows == 0) {
                throw new IllegalStateException(
                        "Stock desync detected between Redis cache and database for product " + productId);
            }

            Order order = buildAndSaveOrder(product, request, OrderStatus.CONFIRMED);
            long executionTimeMs = System.currentTimeMillis() - startTime;

            log.info("[REDIS_LUA] Order {} confirmed for product {} in {} ms (Redis remaining: {})",
                    order.getOrderTrackingNumber(), productId, executionTimeMs, result);

            return buildOrderResponse(order, product.getTitle(),
                    "Purchase confirmed via Redis Lua atomic stock deduction.", executionTimeMs);

        } catch (RuntimeException ex) {
            redisStockCacheService.compensateStock(productId, quantity);
            throw ex;
        }
    }

    // ---------------------------------------------------------------
    // SHARED HELPERS
    // ---------------------------------------------------------------
    private void validateStock(Product product, Integer requestedQuantity) {
        if (product.getStockQuantity() < requestedQuantity) {
            throw new InsufficientStockException(
                    "Insufficient stock for product '" + product.getTitle() + "'. Available: "
                            + product.getStockQuantity() + ", Requested: " + requestedQuantity);
        }
    }

    private Order buildAndSaveOrder(Product product, PurchaseRequest request, OrderStatus status) {
        BigDecimal totalAmount = product.getPrice().multiply(BigDecimal.valueOf(request.getQuantity()));

        Order order = Order.builder()
                .orderTrackingNumber(generateTrackingNumber())
                .userId(request.getUserId())
                .productId(product.getId())
                .quantity(request.getQuantity())
                .totalAmount(totalAmount)
                .status(status)
                .build();

        return orderRepository.save(order);
    }

    private String generateTrackingNumber() {
        return "ORD-" + UUID.randomUUID().toString().replace("-", "").toUpperCase().substring(0, 16);
    }

    private OrderResponse buildOrderResponse(Order order, String productTitle, String message, long executionTimeMs) {
        return OrderResponse.builder()
                .orderTrackingNumber(order.getOrderTrackingNumber())
                .productId(order.getProductId())
                .productName(productTitle)
                .quantity(order.getQuantity())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .message(message)
                .executionTimeMs(executionTimeMs)
                .build();
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(50L * attempt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private record PurchaseResult(Order order, String productTitle) {
    }
}