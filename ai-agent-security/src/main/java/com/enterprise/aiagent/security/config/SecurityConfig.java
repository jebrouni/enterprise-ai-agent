package com.enterprise.aiagent.security.config;

import com.enterprise.aiagent.security.filter.JwtAuditFilter;
import com.enterprise.aiagent.security.filter.RateLimitFilter;
import com.enterprise.aiagent.security.service.KeycloakJwtConverter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final KeycloakJwtConverter keycloakJwtConverter;
    private final JwtAuditFilter       jwtAuditFilter;
    private final RateLimitFilter      rateLimitFilter;

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/agent/health",
            "/api/v1/auth/**",
            "/actuator/health",
            "/actuator/info",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/api-docs/**"
    };

    private static final String[] ADMIN_ENDPOINTS = {
            "/api/v1/admin/**",
            "/actuator/metrics",
            "/actuator/prometheus",
            "/api/v1/audit/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                .requestMatchers(ADMIN_ENDPOINTS).hasRole("AI_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/agent/ask")
                    .hasAnyRole("AI_USER", "AI_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/agent/review")
                    .hasAnyRole("AI_USER", "AI_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/agent/chat")
                    .hasAnyRole("AI_USER", "AI_ADMIN")
                .anyRequest().authenticated()
            )

            // ── Filtre token de test ──────────────────────────────────
            .addFilterBefore(
            	    (servletRequest, servletResponse, chain) -> {
            	        var httpRequest =
            	            (jakarta.servlet.http.HttpServletRequest) servletRequest;
            	        String authHeader = httpRequest.getHeader("Authorization");

            	        if (authHeader != null
            	                && authHeader.startsWith("Bearer test-token-")) {

            	            String username = authHeader
            	                .replace("Bearer test-token-", "");

            	            boolean admin = "admin-demo".equals(username);

            	            var authorities = admin
            	                ? java.util.List.of(
            	                    new SimpleGrantedAuthority("ROLE_AI_USER"),
            	                    new SimpleGrantedAuthority("ROLE_AI_ADMIN"))
            	                : java.util.List.of(
            	                    new SimpleGrantedAuthority("ROLE_AI_USER"));

            	            var authToken =
            	                new UsernamePasswordAuthenticationToken(
            	                    username, null, authorities);

            	            SecurityContextHolder
            	                .getContext()
            	                .setAuthentication(authToken);
            	        }

            	        chain.doFilter(servletRequest, servletResponse);
            	    },
            	    UsernamePasswordAuthenticationFilter.class
            	)

            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, e) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write(
                        "{\"success\":false,\"errorCode\":\"UNAUTHORIZED\"," +
                        "\"message\":\"Token manquant ou invalide.\"}");
                })
                .accessDeniedHandler((request, response, e) -> {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    response.getWriter().write(
                        "{\"success\":false,\"errorCode\":\"FORBIDDEN\"," +
                        "\"message\":\"Permissions insuffisantes.\"}");
                })
            )

            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(jwtAuditFilter,   UsernamePasswordAuthenticationFilter.class);

        log.info("✅ Sécurité configurée avec succès");
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOriginPatterns(List.of(
                "http://localhost:4200",
                "https://*.vercel.app",
                "https://ai-agent-frontend-ashy.vercel.app"
        ));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of(
                "X-Request-ID", "X-Rate-Limit-Remaining", "X-Processing-Time-Ms"
        ));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}