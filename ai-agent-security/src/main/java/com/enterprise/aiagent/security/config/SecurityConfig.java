package com.enterprise.aiagent.security.config;

import com.enterprise.aiagent.security.filter.JwtAuditFilter;
import com.enterprise.aiagent.security.filter.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuditFilter  jwtAuditFilter;
    private final RateLimitFilter rateLimitFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── Autorisations ─────────────────────────────────────────
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

            // ── Filtre token de test (inline) ─────────────────────────
            .addFilterBefore(
                (request, response, chain) -> {
                    var req = (jakarta.servlet.http.HttpServletRequest) request;
                    String auth = req.getHeader("Authorization");

                    if (auth != null && auth.startsWith("Bearer test-token-")) {
                        String username = auth.replace("Bearer test-token-", "");

                        var roles = "admin-demo".equals(username)
                            ? java.util.List.of(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_AI_USER"),
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_AI_ADMIN"))
                            : java.util.List.of(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_AI_USER"));

                        var authentication =
                            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                username, null, roles);

                        org.springframework.security.core.context.SecurityContextHolder
                            .getContext().setAuthentication(authentication);
                    }
                    chain.doFilter(request, response);
                },
                UsernamePasswordAuthenticationFilter.class
            )

            // ── Gestion erreurs ───────────────────────────────────────
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, e) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("""
                        {"success":false,"errorCode":"UNAUTHORIZED",
                         "message":"Token manquant ou invalide."}
                        """);
                })
                .accessDeniedHandler((request, response, e) -> {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    response.getWriter().write("""
                        {"success":false,"errorCode":"FORBIDDEN",
                         "message":"Permissions insuffisantes."}
                        """);
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
                "http://localhost:3000",
                "https://*.vercel.app",
                "https://ai-agent-frontend-ashy.vercel.app"  // ← ton URL exacte
        ));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of(
                "X-Request-ID", "X-Rate-Limit-Remaining", "X-Processing-Time-Ms"
        ));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);  // ← changer /api/** en /**
        return source;
    }
}