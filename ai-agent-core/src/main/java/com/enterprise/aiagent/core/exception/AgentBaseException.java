package com.enterprise.aiagent.core.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * ┌──────────────────────────────────────────────────────────┐
 *  HIÉRARCHIE D'EXCEPTIONS MÉTIER
 *  Toutes les exceptions de l'application héritent de
 *  AgentBaseException pour un traitement unifié.
 * └──────────────────────────────────────────────────────────┘
 */

/** Exception racine de l'application */
@Getter
public abstract class AgentBaseException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus httpStatus;

    protected AgentBaseException(String message, String errorCode, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    protected AgentBaseException(String message, String errorCode, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}
