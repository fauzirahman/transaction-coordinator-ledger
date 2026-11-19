package com.fauzi.ledger.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int limit;
    private final long windowMillis;

    public ApiRateLimitFilter(
            @Value("${app.rate-limit.requests:60}") int limit,
            @Value("${app.rate-limit.window:PT1M}") Duration window
    ) {
        if (limit < 1 || window.isNegative() || window.isZero() || window.toMillis() < 1) {
            throw new IllegalArgumentException("Rate limit and window must be positive");
        }
        this.limit = limit;
        this.windowMillis = window.toMillis();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String client = request.getRemoteAddr();
        long now = System.currentTimeMillis();
        boolean[] allowed = {false};
        windows.compute(client, (key, current) -> {
            if (current == null || now - current.startedAt() >= windowMillis) {
                allowed[0] = true;
                return new Window(now, 1);
            }
            if (current.count() >= limit) {
                return current;
            }
            allowed[0] = true;
            return new Window(current.startedAt(), current.count() + 1);
        });

        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= windowMillis);
        }
        if (!allowed[0]) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Rate limit exceeded\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private record Window(long startedAt, int count) {
    }
}
