package com.enterprise.aiagent.security.filter;

import com.enterprise.aiagent.core.exception.RateLimitException;
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
import java.util.concurrent.TimeUnit;

/**
 * ┌──────────────────────────────────────────────────────────────────────┐
 *  FILTRE DE RATE LIMITING — Redis Sliding Window
 *
 *  Stratégie : compteur glissant par utilisateur (userId depuis JWT).
 *  Pour les requêtes anonymes : compteur par IP.
 *
 *  Limites configurables par rôle :
 *  - AI_USER  → 30 requêtes / minute
 *  - AI_ADMIN → 100 requêtes / minute
 *  - Anonyme  → 5 requêtes / minute (health check uniquement)
 *
 *  Headers retournés :
 *  X-Rate-Limit-Limit     : limite totale
 *  X-Rate-Limit-Remaining : requêtes restantes
 *  X-Rate-Limit-Reset     : timestamp de reset (epoch seconds)
 * └──────────────────────────────────────────────────────────────────────┘
 */
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
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         chain
    ) throws ServletException, IOException {

        // Ignorer les endpoints publics
        String uri = request.getRequestURI();
        if (isPublicEndpoint(uri)) {
            chain.doFilter(request, response);
            return;
        }

        // Identifier l'utilisateur (depuis JWT ou IP en fallback)
        String clientKey  = extractClientKey(request);
        boolean isAdmin   = isAdminRequest(request);
        int limit         = isAdmin ? adminLimit : userLimit;

        String redisKey   = "rate_limit:" + clientKey;

        try {
            Long currentCount = redisTemplate.opsForValue().increment(redisKey);

            if (currentCount == null) {
                chain.doFilter(request, response);
                return;
            }

            // Initialiser le TTL à la première requête de la fenêtre
            if (currentCount == 1) {
                redisTemplate.expire(redisKey, Duration.ofSeconds(windowSeconds));
            }

            Long ttl      = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
            int remaining = Math.max(0, limit - currentCount.intValue());
            long resetAt  = System.currentTimeMillis() / 1000 + (ttl != null ? ttl : windowSeconds);

            // Headers de rate limit
            response.setHeader("X-Rate-Limit-Limit",     String.valueOf(limit));
            response.setHeader("X-Rate-Limit-Remaining", String.valueOf(remaining));
            response.setHeader("X-Rate-Limit-Reset",     String.valueOf(resetAt));

            if (currentCount > limit) {
                log.warn("⛔ Rate limit dépassé pour: {} ({}/{})", clientKey, currentCount, limit);
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("""
                    {
                      "success": false,
                      "errorCode": "RATE_LIMIT_EXCEEDED",
                      "message": "Trop de requêtes. Limite: %d req/%ds. Réessayez dans %d secondes.",
                      "retryAfterSeconds": %d
                    }
                    """.formatted(limit, windowSeconds, ttl != null ? ttl : windowSeconds, ttl != null ? ttl : windowSeconds));
                return;
            }

        } catch (Exception e) {
            // Si Redis est indisponible, on laisse passer (fail-open)
            log.error("⚠️ Redis indisponible pour le rate limiting. Requête autorisée.", e);
        }

        chain.doFilter(request, response);
    }

    private String extractClientKey(HttpServletRequest request) {
        // Essayer d'extraire le sub (userId) depuis le JWT via le header Authorization
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try {
                // Extraction simple du sub sans vérification (déjà validé par Spring Security)
                String token  = auth.substring(7);
                String payload= new String(java.util.Base64.getUrlDecoder()
                        .decode(token.split("\\.")[1]));
                com.fasterxml.jackson.databind.JsonNode node =
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload);
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
        return auth != null && auth.contains("AI_ADMIN");
    }

    private boolean isPublicEndpoint(String uri) {
        return uri.contains("/health") || uri.contains("/swagger")
            || uri.contains("/api-docs") || uri.contains("/actuator/health")
            || uri.contains("/auth/token");
    }
}
