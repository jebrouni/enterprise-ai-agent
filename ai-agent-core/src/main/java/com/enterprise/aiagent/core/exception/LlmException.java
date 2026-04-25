package com.enterprise.aiagent.core.exception;

import org.springframework.http.HttpStatus;

// ── Erreur LLM (API Anthropic indisponible, timeout, etc.) ──────────────────
public class LlmException extends AgentBaseException {
    public LlmException(String message, Throwable cause) {
        super(message, "LLM_ERROR", HttpStatus.SERVICE_UNAVAILABLE, cause);
    }
    public LlmException(String message) {
        super(message, "LLM_ERROR", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
