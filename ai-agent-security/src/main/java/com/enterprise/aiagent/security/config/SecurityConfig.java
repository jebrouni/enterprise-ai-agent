package com.enterprise.aiagent.security.config;

import com.enterprise.aiagent.security.filter.JwtAuditFilter;
import com.enterprise.aiagent.security.filter.RateLimitFilter;
import com.enterprise.aiagent.security.service.KeycloakJwtConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * ┌──────────────────────────────────────────────────────────────────────┐
 *  CONFIGURATION SÉCURITÉ KEYCLOAK — Spring Security OAuth2 Resource Server
 *
 *  Flux d'authentification :
 *  Client → Keycloak (login) → JWT Access Token
 *  Client → Spring Boot API (avec Bearer Token) → Validation JWT → Accès
 *
 *  Rôles Keycloak utilisés :
 *  - ROLE_AI_USER     → Accès aux endpoints /ask et /review
 *  - ROLE_AI_ADMIN    → Accès admin + métriques + audit
 *  - ROLE_AI_READONLY → Accès en lecture seule (health uniquement)
 * └──────────────────────────────────────────────────────────────────────┘
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final KeycloakJwtConverter keycloakJwtConverter;
    private final JwtAuditFilter       jwtAuditFilter;
    private final RateLimitFilter      rateLimitFilter;

    // ── Endpoints publics (sans authentification) ────────────────────────
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/agent/health",
            "/api/v1/auth/token",
            "/actuator/health",
            "/actuator/info",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/api-docs/**"
    };

    // ── Endpoints admin uniquement ───────────────────────────────────────
    private static final String[] ADMIN_ENDPOINTS = {
            "/api/v1/admin/**",
            "/actuator/metrics",
            "/actuator/prometheus",
            "/api/v1/audit/**"
    };

    /**
     * Chaîne de filtres de sécurité principale.
     *
     * Architecture des filtres (ordre d'exécution) :
     * 1. RateLimitFilter       → vérifie la limite de requêtes (Redis)
     * 2. JwtAuditFilter        → journalise les accès avec MDC
     * 3. OAuth2ResourceServer  → valide le JWT Keycloak
     * 4. Authorization         → vérifie les rôles/permissions
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            // ── Désactiver CSRF (API REST stateless) ─────────────────────
            .csrf(AbstractHttpConfigurer::disable)

            // ── CORS ─────────────────────────────────────────────────────
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // ── Stateless : aucune session HTTP ──────────────────────────
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── Autorisations par URL ─────────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                // Endpoints publics — aucun token requis
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()

                // Endpoints admin — rôle ADMIN obligatoire
                .requestMatchers(ADMIN_ENDPOINTS)
                    .hasRole("AI_ADMIN")

                // Question technique — rôle USER ou ADMIN
                .requestMatchers(HttpMethod.POST, "/api/v1/agent/ask")
                    .hasAnyRole("AI_USER", "AI_ADMIN")

                // Revue de code — rôle USER ou ADMIN
                .requestMatchers(HttpMethod.POST, "/api/v1/agent/review")
                    .hasAnyRole("AI_USER", "AI_ADMIN")

                // Chat multi-tours — rôle USER ou ADMIN
                .requestMatchers(HttpMethod.POST, "/api/v1/agent/chat")
                    .hasAnyRole("AI_USER", "AI_ADMIN")

                // Tout le reste requiert une authentification
                .anyRequest().authenticated()
            )

            // ── OAuth2 Resource Server avec JWT Keycloak ─────────────────
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(keycloakJwtConverter)
                )
                .authenticationEntryPoint((request, response, ex) -> {
                    log.warn("🔒 Accès non autorisé: {} {}", request.getMethod(), request.getRequestURI());
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("""
                        {
                          "success": false,
                          "errorCode": "UNAUTHORIZED",
                          "message": "Token JWT manquant ou invalide. Authentifiez-vous via Keycloak.",
                          "hint": "POST /api/v1/auth/token avec vos credentials"
                        }
                        """);
                })
            )

            // ── Gestionnaire d'accès refusé ───────────────────────────────
            .exceptionHandling(ex -> ex
                .accessDeniedHandler((request, response, denied) -> {
                    log.warn("🚫 Accès refusé: {} {} — Rôle insuffisant",
                             request.getMethod(), request.getRequestURI());
                    response.setStatus(403);
                    response.setContentType("application/json");
                    response.getWriter().write("""
                        {
                          "success": false,
                          "errorCode": "FORBIDDEN",
                          "message": "Vous n'avez pas les permissions nécessaires pour cette ressource.",
                          "requiredRoles": ["ROLE_AI_USER", "ROLE_AI_ADMIN"]
                        }
                        """);
                })
            )

            // ── Filtres personnalisés ─────────────────────────────────────
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(jwtAuditFilter, UsernamePasswordAuthenticationFilter.class);

        log.info("✅ Chaîne de sécurité Keycloak configurée avec succès");
        return http.build();
    }

    /**
     * Configuration CORS pour les clients frontend (React, Angular, etc.)
     * À restreindre avec les domaines exacts en production.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // En production, remplacer par les domaines exacts
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:3000",      // React dev
                "http://localhost:4200",      // Angular dev
                "https://*.votredomaine.com"  // Production
        ));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "X-Request-ID",
                "X-Correlation-ID", "Accept-Language"
        ));
        config.setExposedHeaders(List.of(
                "X-Request-ID", "X-Rate-Limit-Remaining", "X-Processing-Time-Ms"
        ));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
