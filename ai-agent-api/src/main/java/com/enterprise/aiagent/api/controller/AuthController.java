package com.enterprise.aiagent.api.controller;

import com.enterprise.aiagent.api.dto.response.AgentApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Contrôleur d'authentification simplifié
 * Sans Keycloak — authentification par username/password fixe
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentification", description = "Login et gestion des tokens")
public class AuthController {

    // ── DTOs ─────────────────────────────────────────────────────────────

    @Data
    static class LoginRequest {
        @NotBlank(message = "Username obligatoire")
        private String username;

        @NotBlank(message = "Password obligatoire")
        private String password;
    }

    @Data
    static class RefreshRequest {
        @NotBlank
        private String refreshToken;
    }

    @Data
    static class LogoutRequest {
        private String refreshToken;
    }

    // ── POST /token — Login ───────────────────────────────────────────────

    @PostMapping("/token")
    @Operation(summary = "Se connecter avec username et password")
    public ResponseEntity<AgentApiResponse<Object>> getToken(
            @Valid @RequestBody LoginRequest request) {

        log.info("Tentative de login : {}", request.getUsername());

        // Vérification des comptes de démo
        boolean isUser  = "user-demo".equals(request.getUsername())
                       && "User@1234".equals(request.getPassword());

        boolean isAdmin = "admin-demo".equals(request.getUsername())
                       && "Admin@1234".equals(request.getPassword());

        if (!isUser && !isAdmin) {
            log.warn("Login échoué pour : {}", request.getUsername());
            return ResponseEntity.status(401)
                    .body(AgentApiResponse.error(
                            "INVALID_CREDENTIALS",
                            "Identifiants incorrects. Utilisez user-demo/User@1234 ou admin-demo/Admin@1234",
                            null));
        }

        log.info("Login réussi pour : {}", request.getUsername());

        // Retourner un token simple
        return ResponseEntity.ok(AgentApiResponse.success(
            Map.of(
                "access_token",  "test-token-" + request.getUsername(),
                "expires_in",    3600,
                "token_type",    "Bearer",
                "username",      request.getUsername(),
                "roles",         isAdmin
                    ? new String[]{"AI_USER", "AI_ADMIN"}
                    : new String[]{"AI_USER"}
            ), null));
    }

    // ── POST /refresh ─────────────────────────────────────────────────────

    @PostMapping("/refresh")
    @Operation(summary = "Rafraîchir le token")
    public ResponseEntity<AgentApiResponse<Object>> refreshToken(
            @Valid @RequestBody RefreshRequest request) {

        // Token simple — pas d'expiration réelle
        return ResponseEntity.ok(AgentApiResponse.success(
            Map.of(
                "access_token", request.getRefreshToken(),
                "expires_in",   3600,
                "token_type",   "Bearer"
            ), null));
    }

    // ── POST /logout ──────────────────────────────────────────────────────

    @PostMapping("/logout")
    @Operation(summary = "Se déconnecter")
    public ResponseEntity<AgentApiResponse<Object>> logout(
            @RequestBody(required = false) LogoutRequest request) {

        return ResponseEntity.ok(AgentApiResponse.success(
            Map.of("message", "Déconnexion réussie"), null));
    }

    // ── GET /me ───────────────────────────────────────────────────────────

    @GetMapping("/me")
    @Operation(summary = "Informations utilisateur courant")
    public ResponseEntity<AgentApiResponse<Object>> me(
            @RequestHeader(value = "Authorization", required = false) String auth) {

        if (auth == null || !auth.startsWith("Bearer test-token-")) {
            return ResponseEntity.status(401)
                    .body(AgentApiResponse.error("UNAUTHORIZED", "Token invalide", null));
        }

        String username = auth.replace("Bearer test-token-", "");
        boolean isAdmin = "admin-demo".equals(username);

        return ResponseEntity.ok(AgentApiResponse.success(
            Map.of(
                "sub",                username,
                "preferred_username", username,
                "email",              username + "@aiagent.com",
                "roles",              isAdmin
                    ? new String[]{"AI_USER", "AI_ADMIN"}
                    : new String[]{"AI_USER"}
            ), null));
    }
}