package com.flashsale.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Wraps only the purchase endpoints' requests in a CachedBodyRequestWrapper,
 * so RateLimitInterceptor can read the JSON body to extract userId without
 * breaking the controller's own @RequestBody deserialization later.
 */
@Component
@Order(1)
public class CachedBodyFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        if (request.getRequestURI().startsWith("/api/flash-sale/purchase")) {
            CachedBodyRequestWrapper wrappedRequest = new CachedBodyRequestWrapper(request);
            filterChain.doFilter(wrappedRequest, response);
        } else {
            filterChain.doFilter(request, response);
        }
    }
}