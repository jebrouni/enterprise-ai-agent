package com.enterprise.aiagent.core.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

/**
 * AgentResult — Résultat produit par l'agent IA.
 * Objet immuable retourné après traitement d'une AgentQuery.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentResult {

    UUID queryId;
    String answer;
    String modelUsed;
    int inputTokens;
    int outputTokens;
    long processingMs;

    @Builder.Default
    String completedAt = Instant.now().toString();

    ResultStatus status;

    public enum ResultStatus {
        SUCCESS, PARTIAL, FALLBACK, ERROR
    }
}
