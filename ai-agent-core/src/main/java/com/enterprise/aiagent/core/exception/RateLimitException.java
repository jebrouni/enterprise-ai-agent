package com.enterprise.aiagent.core.exception;

import org.springframework.http.HttpStatus;

/** Limite de taux dépassée */
public class RateLimitException extends AgentBaseException {
    public RateLimitException(String userId) {
        super("Limite de requêtes dépassée pour l'utilisateur: " + userId,
              "RATE_LIMIT_EXCEEDED", HttpStatus.TOO_MANY_REQUESTS);
    }
}
