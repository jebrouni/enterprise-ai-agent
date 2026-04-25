package com.enterprise.aiagent.api.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * DTO pour les conversations multi-tours.
 * Le client maintient et envoie l'historique complet à chaque tour.
 */
@Data
public class ChatRequest {

    /** Instruction de comportement de l'agent (optionnel — un défaut est utilisé si absent) */
    @Size(max = 2000)
    private String systemPrompt;

    /** Historique complet de la conversation (alterné user/assistant) */
    @NotEmpty(message = "L'historique de messages ne peut pas être vide")
    @Valid
    private List<ChatMessage> messages;

    /** Langage ou contexte de programmation ciblé */
    private String language;

    @Data
    public static class ChatMessage {
        @NotBlank(message = "Le rôle est obligatoire (user ou assistant)")
        private String role;       // "user" | "assistant"

        @NotBlank(message = "Le contenu du message est obligatoire")
        @Size(max = 5000)
        private String content;
    }
}
