package com.flashsale;

import com.flashsale.entity.Product;
import com.flashsale.repository.ProductRepository;
import com.flashsale.service.RedisStockCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@SpringBootApplication
public class FlashSaleApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlashSaleApplication.class, args);
    }

    @Bean
    CommandLineRunner seedDatabase(ProductRepository productRepository,
                                    RedisStockCacheService redisStockCacheService) {
        return args -> {
            if (productRepository.count() == 0) {
                log.info("No products found in database. Seeding initial flash-sale inventory...");

                Product smartphone = Product.builder()
                        .title("Flagship Smartphone")
                        .description("Latest generation flagship smartphone with high-refresh display.")
                        .price(new BigDecimal("699.99"))
                        .stockQuantity(10)
                        .build();

                Product earbuds = Product.builder()
                        .title("Wireless Earbuds")
                        .description("Noise-cancelling true wireless earbuds with charging case.")
                        .price(new BigDecimal("79.99"))
                        .stockQuantity(50)
                        .build();

                productRepository.save(smartphone);
                productRepository.save(earbuds);

                log.info("Seeded 2 products: '{}' (stock={}) and '{}' (stock={})",
                        smartphone.getTitle(), smartphone.getStockQuantity(),
                        earbuds.getTitle(), earbuds.getStockQuantity());
            } else {
                log.info("Products already exist in database ({} found). Skipping seed.", productRepository.count());
            }

            List<Product> allProducts = productRepository.findAll();
            redisStockCacheService.warmAll(allProducts);
        };
    }
}