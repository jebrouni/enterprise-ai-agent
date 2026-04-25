package com.enterprise.aiagent.api.controller;

import com.enterprise.aiagent.api.dto.request.AskRequest;
import com.enterprise.aiagent.api.dto.request.ChatRequest;
import com.enterprise.aiagent.api.dto.request.CodeReviewRequest;
import com.enterprise.aiagent.service.conversation.ConversationService;
import com.enterprise.aiagent.api.dto.response.AgentApiResponse;
import com.enterprise.aiagent.core.domain.AgentQuery;
import com.enterprise.aiagent.core.domain.AgentResult;
import com.enterprise.aiagent.service.agent.EnterpriseAgentService;
import com.enterprise.aiagent.service.audit.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * ┌──────────────────────────────────────────────────────────────────────┐
 *  CONTRÔLEUR REST ENTERPRISE — Agent IA avec Sécurité Keycloak
 *
 *  Tous les endpoints (sauf /health) nécessitent un Bearer Token JWT
 *  émis par Keycloak. Les rôles requis sont documentés dans Swagger.
 *
 *  Swagger UI : http://localhost:8080/swagger-ui.html
 *  OpenAPI JSON: http://localhost:8080/v3/api-docs
 * └──────────────────────────────────────────────────────────────────────┘
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
@Tag(name = "AI Agent", description = "Agent IA pour questions techniques et revue de code")
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Token JWT émis par Keycloak. Obtenir via POST /api/v1/auth/token"
)
public class EnterpriseAgentController {

    private final EnterpriseAgentService agentService;
    private final ConversationService    conversationService;
    private final AuditService           auditService;

    // ── POST /ask — Question technique ──────────────────────────────────

    @PostMapping("/ask")
    @PreAuthorize("hasAnyRole('AI_USER', 'AI_ADMIN')")
    @Operation(
            summary     = "Poser une question technique à l'agent IA",
            description = "Génère une réponse intelligente basée sur Claude Sonnet. Nécessite ROLE_AI_USER.",
            security    = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Réponse générée avec succès"),
            @ApiResponse(responseCode = "401", description = "Token JWT manquant ou expiré"),
            @ApiResponse(responseCode = "403", description = "Rôle insuffisant (AI_USER requis)"),
            @ApiResponse(responseCode = "429", description = "Limite de requêtes dépassée"),
            @ApiResponse(responseCode = "503", description = "Service LLM temporairement indisponible")
    })
    public ResponseEntity<AgentApiResponse<AgentResult>> askQuestion(
            @Valid @RequestBody AskRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        AgentQuery query = AgentQuery.builder()
                .userId(jwt.getSubject())
                .userRole(extractPrimaryRole(jwt))
                .type(AgentQuery.QueryType.TECHNICAL_QUESTION)
                .content(request.getQuestion())
                .language(request.getLanguage())
                .context(request.getContext())
                .priority(request.getPriority() != null
                        ? AgentQuery.QueryPriority.valueOf(request.getPriority())
                        : AgentQuery.QueryPriority.NORMAL)
                .build();

        AgentResult result = agentService.answerTechnicalQuestion(query);
        return ResponseEntity.ok(AgentApiResponse.success(result, jwt));
    }

    // ── POST /review — Revue de code ────────────────────────────────────

    @PostMapping("/review")
    @PreAuthorize("hasAnyRole('AI_USER', 'AI_ADMIN')")
    @Operation(
            summary  = "Analyser et corriger du code source",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<AgentApiResponse<AgentResult>> reviewCode(
            @Valid @RequestBody CodeReviewRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        AgentQuery query = AgentQuery.builder()
                .userId(jwt.getSubject())
                .userRole(extractPrimaryRole(jwt))
                .type(AgentQuery.QueryType.CODE_REVIEW)
                .content(request.getCode())
                .language(request.getLanguage())
                .context(request.getProblemDescription())
                .analysisType(request.getAnalysisType())
                .build();

        AgentResult result = agentService.reviewCode(query);
        return ResponseEntity.ok(AgentApiResponse.success(result, jwt));
    }

    // ── POST /chat — Conversation multi-tours ───────────────────────────

    @PostMapping("/chat")
    @PreAuthorize("hasAnyRole('AI_USER', 'AI_ADMIN')")
    @Operation(
            summary  = "Conversation multi-tours avec l'agent IA",
            description = "Envoyez l'historique complet à chaque tour. Le dernier message doit avoir role='user'.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<AgentApiResponse<AgentResult>> chat(
            @Valid @RequestBody ChatRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        AgentQuery query = AgentQuery.builder()
                .userId(jwt.getSubject())
                .userRole(extractPrimaryRole(jwt))
                .type(AgentQuery.QueryType.CONVERSATION)
                .language(request.getLanguage())
                .context(request.getSystemPrompt())
                .build();

        java.util.List<java.util.Map<String, String>> messages = request.getMessages().stream()
                .map(m -> java.util.Map.of("role", m.getRole(), "content", m.getContent()))
                .collect(java.util.stream.Collectors.toList());

        AgentResult result = conversationService.chat(query, messages);
        return ResponseEntity.ok(AgentApiResponse.success(result, jwt));
    }

    // ── POST /security-audit — Audit de sécurité (Admin seulement) ──────

    @PostMapping("/security-audit")
    @PreAuthorize("hasRole('AI_ADMIN')")
    @Operation(
            summary     = "Audit de sécurité complet (ADMIN uniquement)",
            description = "Analyse OWASP Top 10 + CVSS scoring. Nécessite ROLE_AI_ADMIN.",
            security    = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<AgentApiResponse<AgentResult>> securityAudit(
            @Valid @RequestBody CodeReviewRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        AgentQuery query = AgentQuery.builder()
                .userId(jwt.getSubject())
                .userRole("AI_ADMIN")
                .type(AgentQuery.QueryType.SECURITY_AUDIT)
                .content(request.getCode())
                .language(request.getLanguage())
                .context(request.getProblemDescription())
                .analysisType("SECURITE")
                .build();

        AgentResult result = agentService.performSecurityAudit(query);
        return ResponseEntity.ok(AgentApiResponse.success(result, jwt));
    }

    // ── GET /admin/stats — Statistiques d'usage (Admin) ─────────────────

    @GetMapping("/admin/stats")
    @PreAuthorize("hasRole('AI_ADMIN')")
    @Operation(
            summary  = "Statistiques quotidiennes d'usage (ADMIN)",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<AgentApiResponse<AuditService.AuditStats>> getDailyStats(
            @RequestParam(defaultValue = "") String date,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String targetDate = date.isBlank() ? LocalDate.now().toString() : date;
        AuditService.AuditStats stats = auditService.getDailyStats(targetDate);
        return ResponseEntity.ok(AgentApiResponse.success(stats, jwt));
    }

    // ── GET /health — Statut de l'agent (Public) ─────────────────────────

    @GetMapping("/health")
    @Operation(summary = "Vérifier le statut de l'agent (public)")
    public ResponseEntity<AgentApiResponse<Object>> health() {
        var status = new java.util.LinkedHashMap<String, Object>();
        status.put("status",    "UP");
        status.put("service",   "Enterprise AI Agent");
        status.put("version",   "2.0.0");
        status.put("security",  "Keycloak OAuth2/OIDC");
        status.put("timestamp", java.time.LocalDateTime.now().toString());
        return ResponseEntity.ok(AgentApiResponse.success(status, null));
    }

    // ── Helper ────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String extractPrimaryRole(Jwt jwt) {
        try {
            var realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null) {
                var roles = (java.util.List<String>) realmAccess.get("roles");
                if (roles != null && !roles.isEmpty()) {
                    return roles.stream()
                            .filter(r -> r.startsWith("AI_"))
                            .findFirst()
                            .orElse(roles.get(0));
                }
            }
        } catch (Exception ignored) {}
        return "AI_USER";
    }
}
