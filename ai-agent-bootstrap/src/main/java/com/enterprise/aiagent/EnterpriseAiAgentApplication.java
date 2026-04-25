package com.enterprise.aiagent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * ┌══════════════════════════════════════════════════════════════════════╗
 *  ENTERPRISE AI CODE AGENT — v2.0.0
 *
 *  Architecture multi-modules Spring Boot avec :
 *  ✅ Sécurité Keycloak OAuth2/OIDC (JWT)
 *  ✅ RBAC (ROLE_AI_USER, ROLE_AI_ADMIN)
 *  ✅ Rate Limiting Redis (30 req/min par utilisateur)
 *  ✅ Circuit Breaker Resilience4j (fallback automatique)
 *  ✅ Cache Redis distribué (réponses LLM)
 *  ✅ Audit asynchrone (traçabilité complète)
 *  ✅ Métriques Micrometer + Prometheus + Grafana
 *  ✅ OpenAPI 3 / Swagger UI
 *  ✅ Logs structurés avec MDC (corrélation)
 *
 *  Swagger UI   : http://localhost:8080/swagger-ui.html
 *  Health Check : http://localhost:8080/api/v1/agent/health
 *  Prometheus   : http://localhost:8080/actuator/prometheus
 *  Keycloak     : http://localhost:8180/realms/ai-agent-realm
 * ╚══════════════════════════════════════════════════════════════════════╝
 */
@Slf4j
@SpringBootApplication(scanBasePackages = "com.enterprise.aiagent")
@EnableAsync
@EnableCaching
public class EnterpriseAiAgentApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EnterpriseAiAgentApplication.class);
        app.run(args);

        log.info("""

                ╔══════════════════════════════════════════════════════╗
                ║       ENTERPRISE AI CODE AGENT — DÉMARRÉ ✅         ║
                ╠══════════════════════════════════════════════════════╣
                ║  API      : http://localhost:8080/api/v1/agent       ║
                ║  Swagger  : http://localhost:8080/swagger-ui.html    ║
                ║  Keycloak : http://localhost:8180                    ║
                ║  Redis    : localhost:6379                           ║
                ╚══════════════════════════════════════════════════════╝
                """);
    }
}
