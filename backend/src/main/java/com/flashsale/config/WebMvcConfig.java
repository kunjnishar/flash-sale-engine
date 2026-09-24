package com.flashsale.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.interceptor.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(redissonClient, objectMapper))
                .addPathPatterns("/api/flash-sale/purchase/**");
    }
}