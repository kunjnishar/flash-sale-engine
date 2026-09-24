package com.flashsale.interceptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.util.StreamUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String RATE_LIMIT_KEY_PREFIX = "ratelimit:purchase:";
    private static final int MAX_REQUESTS = 5;
    private static final int WINDOW_SECONDS = 5;

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String clientKey = resolveClientKey(request);
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(RATE_LIMIT_KEY_PREFIX + clientKey);

        // Idempotent: only actually applies the first time this key is seen.
        rateLimiter.trySetRate(RateType.OVERALL, MAX_REQUESTS, WINDOW_SECONDS, RateIntervalUnit.SECONDS);

        boolean acquired = rateLimiter.tryAcquire(1);
        if (!acquired) {
            throw new RateLimitExceededException(
                    "Rate limit exceeded: max " + MAX_REQUESTS + " purchase requests per " + WINDOW_SECONDS
                            + " seconds per user. Please slow down and try again shortly.");
        }
        return true;
    }

    /**
     * Rate-limits by the userId inside the request body when present (the
     * realistic, per-customer semantic), falling back to client IP for any
     * request where the body can't be read or doesn't carry a userId.
     */
    private String resolveClientKey(HttpServletRequest request) {
        try {
            byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
            JsonNode node = objectMapper.readTree(body);
            if (node != null && node.has("userId")) {
                return "user:" + node.get("userId").asText();
            }
        } catch (IOException ex) {
            // Fall through to IP-based fallback below.
        }
        return "ip:" + request.getRemoteAddr();
    }
}