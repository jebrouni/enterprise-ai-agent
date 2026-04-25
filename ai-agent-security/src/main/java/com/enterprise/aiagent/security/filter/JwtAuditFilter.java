package com.enterprise.aiagent.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * ┌──────────────────────────────────────────────────────────────────────┐
 *  FILTRE D'AUDIT JWT
 *
 *  Ce filtre enrichit le contexte MDC (Mapped Diagnostic Context) avec :
 *  - requestId    : ID unique de la requête (UUID ou X-Request-ID header)
 *  - userId       : sub du token Keycloak
 *  - username     : preferred_username du token
 *  - userRoles    : rôles Keycloak de l'utilisateur
 *  - clientIp     : adresse IP du client
 *  - httpMethod   : GET/POST/etc.
 *  - requestPath  : URI de la requête
 *
 *  Tous les logs émis dans la chaîne de traitement incluront
 *  automatiquement ces informations (corrélation de logs).
 * └──────────────────────────────────────────────────────────────────────┘
 */
@Slf4j
@Component
public class JwtAuditFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         chain
    ) throws ServletException, IOException {

        long startTime = System.currentTimeMillis();

        // ── Génération/récupération du Request ID ────────────────────────
        String requestId = request.getHeader("X-Request-ID");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        try {
            // ── Enrichissement MDC ───────────────────────────────────────
            MDC.put("requestId",   requestId);
            MDC.put("clientIp",    extractClientIp(request));
            MDC.put("httpMethod",  request.getMethod());
            MDC.put("requestPath", request.getRequestURI());

            // Extraction des informations JWT (si authentifié)
            enrichMdcWithJwtInfo();

            // ── Propagation du Request ID dans la réponse ────────────────
            response.setHeader("X-Request-ID", requestId);

            // ── Logging de l'entrée ──────────────────────────────────────
            log.info("→ {} {} | IP: {} | RequestId: {}",
                    request.getMethod(), request.getRequestURI(),
                    MDC.get("clientIp"), requestId);

            // ── Exécution de la chaîne de filtres ────────────────────────
            chain.doFilter(request, response);

        } finally {
            // ── Logging de la sortie ─────────────────────────────────────
            long duration = System.currentTimeMillis() - startTime;
            response.setHeader("X-Processing-Time-Ms", String.valueOf(duration));

            log.info("← {} {} | Status: {} | {}ms",
                    request.getMethod(), request.getRequestURI(),
                    response.getStatus(), duration);

            // ── Nettoyage MDC (évite les fuites entre requêtes) ──────────
            MDC.clear();
        }
    }

    /** Extrait les informations utilisateur du JWT Keycloak pour le MDC */
    private void enrichMdcWithJwtInfo() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();

            MDC.put("userId",   jwt.getSubject());
            MDC.put("username", jwt.getClaimAsString("preferred_username"));
            MDC.put("userRoles", auth.getAuthorities().stream()
                    .map(Object::toString)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("none"));
        }
    }

    /** Extrait l'IP réelle du client (gère les proxies/load balancers) */
    private String extractClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim(); // Prend la première IP (client réel)
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) {
            return ip;
        }
        return request.getRemoteAddr();
    }
}
