package com.enterprise.aiagent.service.conversation;

import com.enterprise.aiagent.core.domain.AgentQuery;
import com.enterprise.aiagent.core.domain.AgentResult;
import com.enterprise.aiagent.service.audit.AuditService;
import com.enterprise.aiagent.service.prompt.PromptBuilderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.MediaType;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.List;
import java.util.Map;

/**
 * Service de conversation multi-tours — utilise Groq (format OpenAI)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final WebClient anthropicWebClient;
    private final AuditService auditService;
    private final PromptBuilderService promptBuilder;
    private final MeterRegistry meterRegistry;

    @org.springframework.beans.factory.annotation.Value("${anthropic.model:llama3-8b-8192}")
    private String model;

    @org.springframework.beans.factory.annotation.Value("${anthropic.max-tokens:2048}")
    private int maxTokens;

    @PreAuthorize("hasAnyRole('AI_USER', 'AI_ADMIN')")
    public AgentResult chat(AgentQuery query, List<Map<String, String>> messages) {

        log.info("Chat | QueryId: {} | {} messages", query.getQueryId(), messages.size());

        validateMessages(messages);

        long start = System.currentTimeMillis();

        // Construire les messages au format OpenAI
        List<Map<String, String>> openAiMessages = new java.util.ArrayList<>();

        // Ajouter le system prompt
        openAiMessages.add(Map.of(
            "role",    "system",
            "content", buildSystemPrompt(query)
        ));

        // Ajouter l'historique
        openAiMessages.addAll(messages);

        // Construire la requête Groq
        Map<String, Object> request = Map.of(
            "model",      model,
            "max_tokens", maxTokens,
            "messages",   openAiMessages
        );

        try {
            Map response = anthropicWebClient.post()
                    .uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            String answer = extractAnswer(response);
            long processingMs = System.currentTimeMillis() - start;

            log.info("Réponse chat | QueryId: {} | {}ms", query.getQueryId(), processingMs);

            AgentResult result = AgentResult.builder()
                    .queryId(query.getQueryId())
                    .answer(answer)
                    .modelUsed(model)
                    .processingMs(processingMs)
                    .completedAt(java.time.Instant.now().toString())
                    .status(AgentResult.ResultStatus.SUCCESS)
                    .build();

            auditService.record(query, result);
            return result;

        } catch (Exception ex) {
            log.error("Erreur chat Groq: {}", ex.getMessage());
            return AgentResult.builder()
                    .queryId(query.getQueryId())
                    .answer("Service temporairement indisponible.")
                    .modelUsed("fallback")
                    .processingMs(0)
                    .completedAt(java.time.Instant.now().toString())
                    .status(AgentResult.ResultStatus.FALLBACK)
                    .build();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    private String buildSystemPrompt(AgentQuery query) {
        String base = "Tu es un assistant expert en développement logiciel. Réponds en français.";
        if (query.getLanguage() != null && !query.getLanguage().isBlank()) {
            base += " Tu es spécialisé en " + query.getLanguage() + ".";
        }
        if (query.getContext() != null && !query.getContext().isBlank()) {
            base += " Contexte : " + query.getContext();
        }
        return base;
    }

    private void validateMessages(List<Map<String, String>> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("L'historique ne peut pas être vide.");
        }
        String lastRole = messages.get(messages.size() - 1).get("role");
        if (!"user".equals(lastRole)) {
            throw new IllegalArgumentException("Le dernier message doit être du rôle 'user'.");
        }
    }

    @SuppressWarnings("unchecked")
    private String extractAnswer(Map response) {
        try {
            List<Map> choices = (List<Map>) response.get("choices");
            Map message = (Map) choices.get(0).get("message");
            return (String) message.get("content");
        } catch (Exception e) {
            log.error("Erreur extraction réponse chat", e);
            return "Impossible d'extraire la réponse.";
        }
    }
}