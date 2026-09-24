package com.flashsale.service;

import com.flashsale.entity.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisStockCacheService {

    public static final String STOCK_KEY_PREFIX = "flashsale:stock:";

    // Lua scripts run atomically inside Redis — no other command can interleave
    // between the GET and the DECRBY, which is exactly what makes this race-free
    // without needing any lock at all.
    private static final String DECREMENT_SCRIPT =
            "local current = redis.call('GET', KEYS[1]) " +
            "if current == false then return -2 end " +
            "current = tonumber(current) " +
            "local qty = tonumber(ARGV[1]) " +
            "if current < qty then return -1 end " +
            "return redis.call('DECRBY', KEYS[1], qty)";

    private static final String INCREMENT_SCRIPT = "return redis.call('INCRBY', KEYS[1], ARGV[1])";

    private final RedissonClient redissonClient;

    public static String stockKey(Long productId) {
        return STOCK_KEY_PREFIX + productId;
    }

    /**
     * Writes the current stock level into Redis as a plain numeric string,
     * using StringCodec so that native Redis commands (GET, DECRBY, INCRBY)
     * and our raw Lua scripts can all read/write it consistently.
     */
    public void warmStock(Long productId, Integer stockQuantity) {
        RBucket<String> bucket = redissonClient.getBucket(stockKey(productId), StringCodec.INSTANCE);
        bucket.set(String.valueOf(stockQuantity));
    }

    public void warmAll(List<Product> products) {
        products.forEach(p -> warmStock(p.getId(), p.getStockQuantity()));
        log.info("Warmed Redis stock cache for {} product(s).", products.size());
    }

    /**
     * Atomically checks and decrements cached stock in a single round trip to Redis.
     *
     * @return -2 if the key isn't cached yet (needs warming),
     *         -1 if there isn't enough cached stock,
     *         or the remaining stock (>= 0) if the decrement succeeded.
     */
    public long decrementStock(Long productId, Integer quantity) {
        RScript script = redissonClient.getScript(StringCodec.INSTANCE);
        Long result = script.eval(
                RScript.Mode.READ_WRITE,
                DECREMENT_SCRIPT,
                RScript.ReturnType.INTEGER,
                Collections.singletonList(stockKey(productId)),
                String.valueOf(quantity)
        );
        return result;
    }

    /**
     * Gives reserved stock back to Redis. Used only as compensation when a
     * request wins the Redis reservation but then fails to complete its
     * database write, so the cache doesn't end up permanently under-counting.
     */
    public void compensateStock(Long productId, Integer quantity) {
        RScript script = redissonClient.getScript(StringCodec.INSTANCE);
        script.eval(
                RScript.Mode.READ_WRITE,
                INCREMENT_SCRIPT,
                RScript.ReturnType.INTEGER,
                Collections.singletonList(stockKey(productId)),
                String.valueOf(quantity)
        );
    }
}