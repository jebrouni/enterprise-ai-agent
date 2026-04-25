package com.enterprise.aiagent.core;

import com.enterprise.aiagent.core.domain.AgentQuery;
import com.enterprise.aiagent.core.domain.AgentResult;
import com.enterprise.aiagent.core.exception.LlmException;
import com.enterprise.aiagent.core.exception.RateLimitException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Tests du module Core — Domaine métier")
class CoreDomainTest {

    // ═══════════════════════════════════════════════════════════════
    //  AgentQuery
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AgentQuery")
    class AgentQueryTests {

        @Test
        @DisplayName("Construction avec valeurs par défaut")
        void build_defaultValues_shouldBeSet() {
            AgentQuery query = AgentQuery.builder()
                    .userId("user-123")
                    .type(AgentQuery.QueryType.TECHNICAL_QUESTION)
                    .content("Qu'est-ce que JPA ?")
                    .build();

            assertThat(query.getQueryId()).isNotNull();
            assertThat(query.getCreatedAt()).isNotNull();
            assertThat(query.getPriority()).isEqualTo(AgentQuery.QueryPriority.NORMAL);
        }

        @Test
        @DisplayName("Chaque query a un UUID unique")
        void build_eachQuery_shouldHaveUniqueId() {
            AgentQuery q1 = AgentQuery.builder().content("Q1").build();
            AgentQuery q2 = AgentQuery.builder().content("Q2").build();

            assertThat(q1.getQueryId()).isNotEqualTo(q2.getQueryId());
        }

        @Test
        @DisplayName("Tous les types de requête sont définis")
        void queryType_allValues_shouldExist() {
            assertThat(AgentQuery.QueryType.values()).containsExactlyInAnyOrder(
                    AgentQuery.QueryType.TECHNICAL_QUESTION,
                    AgentQuery.QueryType.CODE_REVIEW,
                    AgentQuery.QueryType.CONVERSATION,
                    AgentQuery.QueryType.SECURITY_AUDIT
            );
        }

        @Test
        @DisplayName("Priorité HIGH explicitement définie")
        void build_withHighPriority_shouldBeHigh() {
            AgentQuery query = AgentQuery.builder()
                    .content("Bug critique en production !")
                    .priority(AgentQuery.QueryPriority.HIGH)
                    .build();

            assertThat(query.getPriority()).isEqualTo(AgentQuery.QueryPriority.HIGH);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  AgentResult
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("AgentResult")
    class AgentResultTests {

        @Test
        @DisplayName("Construction d'un résultat de succès")
        void build_successResult_shouldHaveCorrectStatus() {
            UUID queryId = UUID.randomUUID();

            AgentResult result = AgentResult.builder()
                    .queryId(queryId)
                    .answer("Voici la réponse de l'agent...")
                    .modelUsed("claude-sonnet-4-20250514")
                    .inputTokens(150)
                    .outputTokens(450)
                    .processingMs(1200L)
                    .status(AgentResult.ResultStatus.SUCCESS)
                    .build();

            assertThat(result.getQueryId()).isEqualTo(queryId);
            assertThat(result.getStatus()).isEqualTo(AgentResult.ResultStatus.SUCCESS);
            assertThat(result.getAnswer()).isNotBlank();
            assertThat(result.getInputTokens()).isEqualTo(150);
            assertThat(result.getOutputTokens()).isEqualTo(450);
            assertThat(result.getProcessingMs()).isEqualTo(1200L);
            assertThat(result.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("Résultat fallback en cas d'indisponibilité")
        void build_fallbackResult_shouldHaveFallbackStatus() {
            AgentResult result = AgentResult.builder()
                    .queryId(UUID.randomUUID())
                    .answer("Service temporairement indisponible")
                    .modelUsed("fallback")
                    .status(AgentResult.ResultStatus.FALLBACK)
                    .build();

            assertThat(result.getStatus()).isEqualTo(AgentResult.ResultStatus.FALLBACK);
            assertThat(result.getModelUsed()).isEqualTo("fallback");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Exceptions
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Hiérarchie d'exceptions")
    class ExceptionTests {

        @Test
        @DisplayName("LlmException retourne 503")
        void llmException_shouldReturn503() {
            LlmException ex = new LlmException("API Anthropic indisponible");

            assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(ex.getErrorCode()).isEqualTo("LLM_ERROR");
            assertThat(ex.getMessage()).contains("API Anthropic indisponible");
        }

        @Test
        @DisplayName("LlmException avec cause")
        void llmException_withCause_shouldPreserveCause() {
            RuntimeException cause = new RuntimeException("Connection timeout");
            LlmException ex = new LlmException("Timeout lors de l'appel", cause);

            assertThat(ex.getCause()).isEqualTo(cause);
        }

        @Test
        @DisplayName("RateLimitException retourne 429")
        void rateLimitException_shouldReturn429() {
            RateLimitException ex = new RateLimitException("user-abc");

            assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(ex.getErrorCode()).isEqualTo("RATE_LIMIT_EXCEEDED");
            assertThat(ex.getMessage()).contains("user-abc");
        }
    }
}
