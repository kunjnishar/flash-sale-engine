package com.flashsale.service.impl;

import com.flashsale.dto.ProductResponse;
import com.flashsale.entity.Product;
import com.flashsale.exception.ProductNotFoundException;
import com.flashsale.repository.ProductRepository;
import com.flashsale.service.ProductService;
import com.flashsale.service.RedisStockCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private static final Map<String, Integer> DEFAULT_STOCK_LEVELS = Map.of(
            "Flagship Smartphone", 10,
            "Wireless Earbuds", 50
    );

    private static final String PRODUCT_LOCK_KEY_PATTERN = "lock:product:*";

    private final ProductRepository productRepository;
    private final RedissonClient redissonClient;
    private final RedisStockCacheService redisStockCacheService;

    @Override
    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ProductResponse getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found with id: " + id));
        return toResponse(product);
    }

    @Override
    @Transactional
    public List<ProductResponse> resetInventory() {
        List<Product> products = productRepository.findAll();

        for (Product product : products) {
            Integer defaultStock = DEFAULT_STOCK_LEVELS.get(product.getTitle());
            if (defaultStock != null) {
                product.setStockQuantity(defaultStock);
            }
        }

        List<Product> savedProducts = productRepository.saveAll(products);

        long deletedLockKeys = redissonClient.getKeys().deleteByPattern(PRODUCT_LOCK_KEY_PATTERN);
        redisStockCacheService.warmAll(savedProducts);

        log.info("Inventory reset complete. Restored {} product(s) to default stock, flushed {} Redis lock key(s), "
                        + "and re-warmed the Redis stock cache.",
                savedProducts.size(), deletedLockKeys);

        return savedProducts.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .title(product.getTitle())
                .description(product.getDescription())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .build();
    }
}