package com.timecraft.security;

import com.timecraft.common.util.RequestUtils;
import com.timecraft.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sliding-window rate limiter protecting sensitive authentication endpoints
 * (customer auth APIs and the admin login form).
 */
@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/resend-verification",
            "/admin/login"
    );

    private final AppProperties properties;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.rateLimit().enabled()) {
            return true;
        }
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return !PROTECTED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String ip = RequestUtils.clientIp(request);
        String key = (ip != null ? ip : "unknown") + ":" + request.getRequestURI();
        long windowMillis = properties.rateLimit().authWindow().toMillis();
        int capacity = properties.rateLimit().authCapacity();

        long now = System.currentTimeMillis();
        WindowCounter counter = counters.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStart >= windowMillis) {
                return new WindowCounter(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });

        if (counter.count.get() > capacity) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(Math.max(1, windowMillis / 1000)));
            if (request.getRequestURI().startsWith("/api/")) {
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"timestamp\":\"" + Instant.now()
                        + "\",\"status\":429,\"error\":\"Too Many Requests\",\"code\":\"RATE_LIMITED\","
                        + "\"message\":\"Too many attempts. Please wait a minute and try again.\",\"path\":\""
                        + request.getRequestURI() + "\"}");
            } else {
                response.sendRedirect("/admin/login?rateLimited");
            }
            return;
        }

        if (counters.size() > 10_000) {
            counters.entrySet().removeIf(e -> now - e.getValue().windowStart >= windowMillis);
        }
        filterChain.doFilter(request, response);
    }

    public void reset() {
        counters.clear();
    }

    private record WindowCounter(long windowStart, AtomicInteger count) {
    }
}
