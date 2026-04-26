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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    // ── POST /ask ────────────────────────────────────────────────────────

    @PostMapping("/ask")
    @PreAuthorize("hasAnyRole('AI_USER', 'AI_ADMIN')")
    @Operation(summary = "Poser une question technique",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Réponse générée"),
            @ApiResponse(responseCode = "401", description = "Token manquant"),
            @ApiResponse(responseCode = "403", description = "Rôle insuffisant"),
            @ApiResponse(responseCode = "429", description = "Limite dépassée")
    })
    public ResponseEntity<AgentApiResponse<AgentResult>> askQuestion(
            @Valid @RequestBody AskRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Object principal) {

        AgentQuery query = AgentQuery.builder()
                .userId(extractUserId(principal))
                .userRole(extractPrimaryRole(principal))
                .type(AgentQuery.QueryType.TECHNICAL_QUESTION)
                .content(request.getQuestion())
                .language(request.getLanguage())
                .context(request.getContext())
                .priority(request.getPriority() != null
                        ? AgentQuery.QueryPriority.valueOf(request.getPriority())
                        : AgentQuery.QueryPriority.NORMAL)
                .build();

        AgentResult result = agentService.answerTechnicalQuestion(query);
        return ResponseEntity.ok(AgentApiResponse.success(result,
                principal instanceof Jwt jwt ? jwt : null));
    }

    // ── POST /review ─────────────────────────────────────────────────────

    @PostMapping("/review")
    @PreAuthorize("hasAnyRole('AI_USER', 'AI_ADMIN')")
    @Operation(summary = "Analyser du code source",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<AgentApiResponse<AgentResult>> reviewCode(
            @Valid @RequestBody CodeReviewRequest request,
            @AuthenticationPrincipal Object principal) {

        AgentQuery query = AgentQuery.builder()
                .userId(extractUserId(principal))
                .userRole(extractPrimaryRole(principal))
                .type(AgentQuery.QueryType.CODE_REVIEW)
                .content(request.getCode())
                .language(request.getLanguage())
                .context(request.getProblemDescription())
                .analysisType(request.getAnalysisType())
                .build();

        AgentResult result = agentService.reviewCode(query);
        return ResponseEntity.ok(AgentApiResponse.success(result,
                principal instanceof Jwt jwt ? jwt : null));
    }

    // ── POST /chat ───────────────────────────────────────────────────────

    @PostMapping("/chat")
    @PreAuthorize("hasAnyRole('AI_USER', 'AI_ADMIN')")
    @Operation(summary = "Conversation multi-tours",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<AgentApiResponse<AgentResult>> chat(
            @Valid @RequestBody ChatRequest request,
            @AuthenticationPrincipal Object principal) {

        AgentQuery query = AgentQuery.builder()
                .userId(extractUserId(principal))
                .userRole(extractPrimaryRole(principal))
                .type(AgentQuery.QueryType.CONVERSATION)
                .language(request.getLanguage())
                .context(request.getSystemPrompt())
                .build();

        List<Map<String, String>> messages = request.getMessages().stream()
                .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                .collect(Collectors.toList());

        AgentResult result = conversationService.chat(query, messages);
        return ResponseEntity.ok(AgentApiResponse.success(result,
                principal instanceof Jwt jwt ? jwt : null));
    }

    // ── POST /security-audit ─────────────────────────────────────────────

    @PostMapping("/security-audit")
    @PreAuthorize("hasRole('AI_ADMIN')")
    @Operation(summary = "Audit sécurité OWASP (ADMIN)",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<AgentApiResponse<AgentResult>> securityAudit(
            @Valid @RequestBody CodeReviewRequest request,
            @AuthenticationPrincipal Object principal) {

        AgentQuery query = AgentQuery.builder()
                .userId(extractUserId(principal))
                .userRole("AI_ADMIN")
                .type(AgentQuery.QueryType.SECURITY_AUDIT)
                .content(request.getCode())
                .language(request.getLanguage())
                .context(request.getProblemDescription())
                .analysisType("SECURITE")
                .build();

        AgentResult result = agentService.performSecurityAudit(query);
        return ResponseEntity.ok(AgentApiResponse.success(result,
                principal instanceof Jwt jwt ? jwt : null));
    }

    // ── GET /admin/stats ─────────────────────────────────────────────────

    @GetMapping("/admin/stats")
    @PreAuthorize("hasRole('AI_ADMIN')")
    @Operation(summary = "Statistiques d'usage (ADMIN)",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<AgentApiResponse<AuditService.AuditStats>> getDailyStats(
            @RequestParam(defaultValue = "") String date,
            @AuthenticationPrincipal Object principal) {

        String targetDate = date.isBlank() ? LocalDate.now().toString() : date;
        AuditService.AuditStats stats = auditService.getDailyStats(targetDate);
        return ResponseEntity.ok(AgentApiResponse.success(stats,
                principal instanceof Jwt jwt ? jwt : null));
    }

    // ── GET /health ───────────────────────────────────────────────────────

    @GetMapping("/health")
    @Operation(summary = "Statut de l'agent (public)")
    public ResponseEntity<AgentApiResponse<Object>> health() {
        var status = new java.util.LinkedHashMap<String, Object>();
        status.put("status",    "UP");
        status.put("service",   "Enterprise AI Agent");
        status.put("version",   "2.0.0");
        status.put("security",  "Keycloak OAuth2/OIDC");
        status.put("timestamp", java.time.LocalDateTime.now().toString());
        return ResponseEntity.ok(AgentApiResponse.success(status, null));
    }

    // ── Helpers privés ────────────────────────────────────────────────────

    private String extractUserId(Object principal) {
        if (principal instanceof Jwt jwt) return jwt.getSubject();
        return principal != null ? principal.toString() : "anonymous";
    }

    private String extractUsername(Object principal) {
        if (principal instanceof Jwt jwt)
            return jwt.getClaimAsString("preferred_username");
        return principal != null ? principal.toString() : "anonymous";
    }

    @SuppressWarnings("unchecked")
    private String extractPrimaryRole(Object principal) {
        if (principal instanceof Jwt jwt) {
            try {
                var realmAccess = jwt.getClaimAsMap("realm_access");
                if (realmAccess != null) {
                    var roles = (List<String>) realmAccess.get("roles");
                    if (roles != null && !roles.isEmpty()) {
                        return roles.stream()
                                .filter(r -> r.startsWith("AI_"))
                                .findFirst()
                                .orElse(roles.get(0));
                    }
                }
            } catch (Exception ignored) {}
        }
        return "AI_USER";
    }
}