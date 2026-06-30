package com.tradingagents.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 可选 API Key 鉴权 + 简单 IP 限流（仅 /api/*）。
 */
@Slf4j
@Component
@Order(1)
public class ApiSecurityFilter extends OncePerRequestFilter {

    @Value("${security.api-key:}")
    private String configuredApiKey;

    @Value("${security.rate-limit-per-minute:30}")
    private int rateLimitPerMinute;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (configuredApiKey != null && !configuredApiKey.isBlank()) {
            String provided = request.getHeader("X-API-Key");
            if (provided == null || !configuredApiKey.equals(provided)) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"Unauthorized: invalid or missing X-API-Key\"}");
                return;
            }
        }

        String clientKey = resolveClientKey(request);
        if (!allowRequest(clientKey)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Too many requests, please retry later\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean allowRequest(String clientKey) {
        long now = System.currentTimeMillis();
        Window window = windows.computeIfAbsent(clientKey, k -> new Window(now));
        synchronized (window) {
            if (now - window.startMs > 60_000) {
                window.startMs = now;
                window.count.set(0);
            }
            return window.count.incrementAndGet() <= rateLimitPerMinute;
        }
    }

    private static class Window {
        long startMs;
        final AtomicInteger count = new AtomicInteger(0);

        Window(long startMs) {
            this.startMs = startMs;
        }
    }
}
