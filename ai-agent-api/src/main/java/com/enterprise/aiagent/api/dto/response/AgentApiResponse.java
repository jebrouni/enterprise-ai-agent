package com.enterprise.aiagent.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDateTime;

/**
 * Enveloppe standard de toutes les réponses API.
 * Inclut les informations de l'utilisateur authentifié depuis le JWT Keycloak.
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentApiResponse<T> {

    boolean success;
    T       data;
    String  message;

    // ── Contexte utilisateur (depuis JWT Keycloak) ───────────────────────
    String userId;
    String username;
    String userRole;

    // ── Métadonnées de la réponse ────────────────────────────────────────
    @Builder.Default
    LocalDateTime timestamp = LocalDateTime.now();

    // ── Erreur (uniquement en cas d'échec) ───────────────────────────────
    String errorCode;
    Object details;

    /** Factory method pour une réponse de succès. */
    public static <T> AgentApiResponse<T> success(T data, Jwt jwt) {
        AgentApiResponse.AgentApiResponseBuilder<T> builder = AgentApiResponse.<T>builder()
                .success(true)
                .data(data)
                .message("Traitement réussi");

        if (jwt != null) {
            builder
                .userId(jwt.getSubject())
                .username(jwt.getClaimAsString("preferred_username"));
        }

        return builder.build();
    }

    /** Factory method pour une réponse d'erreur. */
    public static <T> AgentApiResponse<T> error(String errorCode, String message, Object details) {
        return AgentApiResponse.<T>builder()
                .success(false)
                .errorCode(errorCode)
                .message(message)
                .details(details)
                .build();
    }
}
