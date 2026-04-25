package com.enterprise.aiagent.service.agent;

import com.enterprise.aiagent.core.domain.AgentQuery;
import com.enterprise.aiagent.core.domain.AgentResult;
import com.enterprise.aiagent.core.exception.RateLimitException;
import com.enterprise.aiagent.infra.anthropic.AnthropicApiClient;
import com.enterprise.aiagent.service.audit.AuditService;
import com.enterprise.aiagent.service.prompt.PromptBuilderService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * ┌──────────────────────────────────────────────────────────────────────┐
 *  SERVICE PRINCIPAL DE L'AGENT IA — Orchestration Enterprise
 *
 *  Responsabilités :
 *  1. Validation des permissions (RBAC via @PreAuthorize)
 *  2. Résolution du contexte utilisateur (JWT Keycloak)
 *  3. Construction des prompts LLM
 *  4. Appel du client Anthropic (avec Circuit Breaker)
 *  5. Mise en cache des réponses (Redis)
 *  6. Audit de chaque interaction
 *  7. Métriques Micrometer
 * └──────────────────────────────────────────────────────────────────────┘
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnterpriseAgentService {

    private final AnthropicApiClient   anthropicApiClient;
    private final PromptBuilderService promptBuilder;
    private final AuditService         auditService;
    private final MeterRegistry        meterRegistry;

    // ── Répondre à une question technique ───────────────────────────────

    @PreAuthorize("hasAnyRole('AI_USER', 'AI_ADMIN')")
    @Cacheable(value = "agent-responses",
               key  = "#query.userId + ':ask:' + T(org.springframework.util.DigestUtils).md5DigestAsHex(#query.content.bytes)",
               condition = "#query.content.length() < 500")
    public AgentResult answerTechnicalQuestion(AgentQuery query) {
        log.info("🤖 [ASK] QueryId: {} | User: {} | Lang: {}",
                query.getQueryId(), query.getUserId(), query.getLanguage());

        meterRegistry.counter("agent.requests",
                "type", "technical_question",
                "userId", query.getUserId()).increment();

        String systemPrompt = promptBuilder.buildTechnicalSystemPrompt(query);
        String userMessage  = promptBuilder.buildTechnicalUserMessage(query);

        AgentResult result = anthropicApiClient.call(systemPrompt, userMessage, query);

        auditService.record(query, result);
        return result;
    }

    // ── Analyser et corriger du code ─────────────────────────────────────

    @PreAuthorize("hasAnyRole('AI_USER', 'AI_ADMIN')")
    public AgentResult reviewCode(AgentQuery query) {
        log.info("🔍 [REVIEW] QueryId: {} | User: {} | Type: {}",
                query.getQueryId(), query.getUserId(), query.getAnalysisType());

        meterRegistry.counter("agent.requests",
                "type", "code_review",
                "analysisType", query.getAnalysisType() != null ? query.getAnalysisType() : "CORRECTION").increment();

        String systemPrompt = promptBuilder.buildCodeReviewSystemPrompt(query);
        String userMessage  = promptBuilder.buildCodeReviewUserMessage(query);

        AgentResult result = anthropicApiClient.call(systemPrompt, userMessage, query);

        auditService.record(query, result);
        return result;
    }

    // ── Audit de sécurité ────────────────────────────────────────────────

    @PreAuthorize("hasRole('AI_ADMIN')")
    public AgentResult performSecurityAudit(AgentQuery query) {
        log.info("🛡️ [SECURITY AUDIT] QueryId: {} | Admin: {}",
                query.getQueryId(), query.getUserId());

        meterRegistry.counter("agent.requests",
                "type", "security_audit",
                "userId", query.getUserId()).increment();

        String systemPrompt = promptBuilder.buildSecurityAuditSystemPrompt();
        String userMessage  = promptBuilder.buildCodeReviewUserMessage(query);

        AgentResult result = anthropicApiClient.call(systemPrompt, userMessage, query);

        auditService.record(query, result);
        return result;
    }

    // ── Extraire le contexte utilisateur depuis le JWT ───────────────────

    public String resolveCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return "anonymous";
        return auth.getName(); // preferred_username depuis le JWT Keycloak
    }
}
