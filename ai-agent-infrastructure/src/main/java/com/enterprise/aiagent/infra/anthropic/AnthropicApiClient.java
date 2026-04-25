package com.enterprise.aiagent.infra.anthropic;

import com.enterprise.aiagent.core.domain.AgentQuery;
import com.enterprise.aiagent.core.domain.AgentResult;
import com.enterprise.aiagent.core.exception.LlmException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Client LLM — compatible Groq (format OpenAI)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnthropicApiClient {

    private final WebClient anthropicWebClient;
    private final MeterRegistry meterRegistry;

    @Value("${anthropic.model:llama3-8b-8192}")
    private String model;

    @Value("${anthropic.max-tokens:2048}")
    private int maxTokens;

    private static final String CB = "anthropicApi";
    private static final String FALLBACK_MSG =
        "Le service IA est temporairement indisponible. Veuillez réessayer.";

    // ── Appel simple ─────────────────────────────────────────────

    @CircuitBreaker(name = CB, fallbackMethod = "fallbackResponse")
    @Retry(name = CB)
    public AgentResult call(String systemPrompt, String userMessage, AgentQuery query) {
        Timer.Sample timer = Timer.start(meterRegistry);
        long start = System.currentTimeMillis();

        log.info("Appel Groq | QueryId: {} | Modèle: {}", query.getQueryId(), model);

        // Construire la requête format OpenAI
        Map<String, Object> request = Map.of(
            "model",      model,
            "max_tokens", maxTokens,
            "messages",   List.of(
                Map.of("role", "system", "content", systemPrompt != null ? systemPrompt : ""),
                Map.of("role", "user",   "content", userMessage)
            )
        );

        return callGroq(request, query, timer, start);
    }

    // ── Fallback ─────────────────────────────────────────────────

    public AgentResult fallbackResponse(String sp, String msg, AgentQuery query, Exception ex) {
        log.error("Fallback activé | QueryId: {} | Cause: {}", query.getQueryId(), ex.getMessage());
        meterRegistry.counter("groq.api.fallback").increment();

        return AgentResult.builder()
                .queryId(query.getQueryId())
                .answer(FALLBACK_MSG)
                .modelUsed("fallback")
                .processingMs(0)
                .completedAt(java.time.Instant.now().toString())
                .status(AgentResult.ResultStatus.FALLBACK)
                .build();
    }

    // ── Appel HTTP vers Groq ──────────────────────────────────────

    private AgentResult callGroq(Map<String, Object> request, AgentQuery query,
                                  Timer.Sample timer, long start) {
        try {
        	   log.info("URL : {}", anthropicWebClient);
               log.info("Request body : {}", request);
            Map response = anthropicWebClient.post()
                    .uri("/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            String answer = extractAnswer(response);
            long processingMs = System.currentTimeMillis() - start;

            timer.stop(Timer.builder("groq.api.call.duration")
                    .tag("model", model)
                    .tag("status", "success")
                    .register(meterRegistry));

            log.info("Réponse Groq | QueryId: {} | {}ms", query.getQueryId(), processingMs);

            return AgentResult.builder()
                    .queryId(query.getQueryId())
                    .answer(answer)
                    .modelUsed(model)
                    .processingMs(processingMs)
                    .completedAt(java.time.Instant.now().toString())
                    .status(AgentResult.ResultStatus.SUCCESS)
                    .build();

        } catch (Exception ex) {
        	 log.error("Erreur Groq DÉTAIL: {} | Type: {}",
        	            ex.getMessage(), ex.getClass().getSimpleName());
        	        if (ex instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
        	            var wcEx = (org.springframework.web.reactive.function.client.WebClientResponseException) ex;
        	            log.error("Status: {} | Body: {}", wcEx.getStatusCode(), wcEx.getResponseBodyAsString());
        	        }
        	        throw new LlmException("Erreur Groq: " + ex.getMessage(), ex);}
    }

    // ── Extraction de la réponse ──────────────────────────────────

    @SuppressWarnings("unchecked")
    private String extractAnswer(Map response) {
        try {
            List<Map> choices = (List<Map>) response.get("choices");
            Map message = (Map) choices.get(0).get("message");
            return (String) message.get("content");
        } catch (Exception e) {
            log.error("Erreur extraction réponse", e);
            return "Impossible d'extraire la réponse du modèle.";
        }
    }
}