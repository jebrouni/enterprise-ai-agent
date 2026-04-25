package com.enterprise.aiagent.api.advice;

import com.enterprise.aiagent.api.dto.response.AgentApiResponse;
import com.enterprise.aiagent.core.exception.AgentBaseException;
import com.enterprise.aiagent.core.exception.LlmException;
import com.enterprise.aiagent.core.exception.RateLimitException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gestionnaire global des exceptions — retourne des réponses JSON standardisées
 * avec codes d'erreur métier pour faciliter le débogage côté client.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Erreurs de validation @Valid */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AgentApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getAllErrors().stream()
                .filter(e -> e instanceof FieldError)
                .map(e -> (FieldError) e)
                .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage,
                        (a, b) -> a));

        log.warn("⚠️ Validation échouée: {}", errors);
        return ResponseEntity.badRequest()
                .body(AgentApiResponse.error("VALIDATION_ERROR", "Données invalides", errors));
    }

    /** Erreurs métier de l'agent (LlmException, RateLimitException, etc.) */
    @ExceptionHandler(AgentBaseException.class)
    public ResponseEntity<AgentApiResponse<Object>> handleAgentException(AgentBaseException ex) {
        log.error("❌ Erreur métier [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus())
                .body(AgentApiResponse.error(ex.getErrorCode(), ex.getMessage(), null));
    }

    /** Token JWT invalide ou expiré */
    @ExceptionHandler(InvalidBearerTokenException.class)
    public ResponseEntity<AgentApiResponse<Object>> handleInvalidToken(InvalidBearerTokenException ex) {
        log.warn("🔒 Token JWT invalide: {}", ex.getMessage());
        return ResponseEntity.status(401)
                .body(AgentApiResponse.error("INVALID_TOKEN",
                        "Token JWT invalide ou expiré. Reconnectez-vous via Keycloak.", null));
    }

    /** Accès refusé (rôle insuffisant) */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<AgentApiResponse<Object>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("🚫 Accès refusé: {}", ex.getMessage());
        return ResponseEntity.status(403)
                .body(AgentApiResponse.error("FORBIDDEN",
                        "Permissions insuffisantes pour cette opération.", null));
    }

    /** Erreur inattendue */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<AgentApiResponse<Object>> handleGeneric(Exception ex) {
        log.error("💥 Erreur inattendue", ex);
        return ResponseEntity.internalServerError()
                .body(AgentApiResponse.error("INTERNAL_ERROR",
                        "Erreur interne. Notre équipe a été notifiée.", null));
    }
}
