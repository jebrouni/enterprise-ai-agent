package com.enterprise.aiagent.core.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

/**
 * ┌──────────────────────────────────────────────────────────┐
 *  MODULE CORE — Objets du domaine métier
 *  Ces classes ne dépendent d'aucun framework Spring.
 * └──────────────────────────────────────────────────────────┘
 *
 * AgentQuery — Représente une requête adressée à l'agent IA.
 * Objet immuable (Value Object).
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentQuery {

    /** Identifiant unique de la requête (trace distribuée) */
    @Builder.Default
    UUID queryId = UUID.randomUUID();

    /** Identifiant de l'utilisateur authentifié (depuis JWT Keycloak) */
    String userId;

    /** Rôle principal de l'utilisateur */
    String userRole;

    /** Type de requête */
    QueryType type;

    /** Question ou code à traiter */
    @NotBlank
    @Size(max = 10_000)
    String content;

    /** Langage de programmation ciblé */
    String language;

    /** Contexte additionnel */
    @Size(max = 2000)
    String context;

    /** Type d'analyse (pour les requêtes de code) */
    String analysisType;

    /** Timestamp de création */
    @Builder.Default
    Instant createdAt = Instant.now();

    /** Priorité de traitement */
    @Builder.Default
    QueryPriority priority = QueryPriority.NORMAL;

    public enum QueryType {
        TECHNICAL_QUESTION,   // Question technique
        CODE_REVIEW,          // Analyse et correction de code
        CONVERSATION,         // Conversation multi-tours
        SECURITY_AUDIT        // Audit de sécurité
    }

    public enum QueryPriority {
        LOW, NORMAL, HIGH, CRITICAL
    }
}
