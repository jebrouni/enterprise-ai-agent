package com.enterprise.aiagent.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;

    @Value("${rate-limit.user-limit:30}")
    private int userLimit;

    @Value("${rate-limit.admin-limit:100}")
    private int adminLimit;

    @Value("${rate-limit.window-seconds:60}")
    private int windowSeconds;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Endpoints publics → pas de rate limiting
        if (isPublicEndpoint(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String key   = "rate_limit:" + extractClientKey(request);
            Long   count = redisTemplate.opsForValue().increment(key);

            if (count == 1) {
                redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
            }

            int limit = isAdminRequest(request) ? adminLimit : userLimit;
            long reset = System.currentTimeMillis() / 1000 + windowSeconds;

            response.setHeader("X-Rate-Limit-Limit",     String.valueOf(limit));
            response.setHeader("X-Rate-Limit-Remaining", String.valueOf(Math.max(0, limit - count)));
            response.setHeader("X-Rate-Limit-Reset",     String.valueOf(reset));

            if (count > limit) {
                log.warn("Rate limit dépassé pour : {}", key);
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write(
                    "{\"success\":false,\"errorCode\":\"RATE_LIMIT_EXCEEDED\"," +
                    "\"message\":\"Limite de requêtes dépassée. Réessayez dans 1 minute.\"}");
                return;
            }

        } catch (Exception e) {
            // Redis indisponible → fail-open (laisser passer)
            log.warn("Redis indisponible pour rate limiting → requête autorisée: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String extractClientKey(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer test-token-")) {
            // Token de test → extraire le username
            return "user:" + auth.replace("Bearer test-token-", "");
        }
        if (auth != null && auth.startsWith("Bearer ")) {
            try {
                // Token JWT → extraire le sub
                String token   = auth.substring(7);
                String payload = new String(java.util.Base64.getUrlDecoder()
                        .decode(token.split("\\.")[1]));
                com.fasterxml.jackson.databind.JsonNode node =
                        new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(payload);
                if (node.has("sub")) {
                    return "user:" + node.get("sub").asText();
                }
            } catch (Exception ignored) {}
        }
        // Fallback sur l'IP
        String ip = request.getHeader("X-Forwarded-For");
        return "ip:" + (ip != null ? ip.split(",")[0].trim() : request.getRemoteAddr());
    }

    private boolean isAdminRequest(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        return auth != null && auth.contains("admin-demo");
    }

    private boolean isPublicEndpoint(String uri) {
        return uri.contains("/health")
            || uri.contains("/swagger")
            || uri.contains("/api-docs")
            || uri.contains("/actuator")
            || uri.contains("/auth/token")
            || uri.contains("/auth/logout");
    }
}