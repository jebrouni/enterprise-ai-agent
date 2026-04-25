package com.enterprise.aiagent.api.controller;

import com.enterprise.aiagent.api.dto.response.AgentApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * ┌──────────────────────────────────────────────────────────────────────┐
 *  CONTRÔLEUR D'AUTHENTIFICATION
 *
 *  Proxy vers Keycloak pour obtenir un JWT Access Token.
 *  Simplifie l'accès Swagger UI — évite d'appeler Keycloak directement.
 *
 *  POST /api/v1/auth/token      → obtenir un token (username + password)
 *  POST /api/v1/auth/refresh    → rafraîchir un token expiré
 *  POST /api/v1/auth/logout     → invalider un token (blacklist Keycloak)
 *  GET  /api/v1/auth/me         → informations de l'utilisateur courant
 * └──────────────────────────────────────────────────────────────────────┘
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Obtenir et gérer les tokens JWT Keycloak")
public class AuthController {

    private final RestTemplate restTemplate;

    @Value("${keycloak.auth-server-url:http://localhost:8180}")
    private String keycloakUrl;

    @Value("${keycloak.realm:ai-agent-realm}")
    private String realm;

    @Value("${keycloak.resource:ai-agent-client}")
    private String clientId;

    @Value("${keycloak.client-secret:ai-agent-secret-change-in-production}")
    private String clientSecret;

    // ── DTOs internes ────────────────────────────────────────────────────

    @Data
    static class LoginRequest {
        @NotBlank(message = "Le nom d'utilisateur est obligatoire")
        private String username;

        @NotBlank(message = "Le mot de passe est obligatoire")
        private String password;
    }

    @Data
    static class RefreshRequest {
        @NotBlank(message = "Le refresh_token est obligatoire")
        private String refreshToken;
    }

    @Data
    static class LogoutRequest {
        @NotBlank(message = "Le refresh_token est obligatoire")
        private String refreshToken;
    }

    // ── POST /token — Obtenir un JWT Access Token ─────────────────────

    @PostMapping("/token")
    @Operation(
        summary     = "Obtenir un JWT Access Token depuis Keycloak",
        description = "Comptes de démo : user-demo/User@1234 (AI_USER) — admin-demo/Admin@1234 (AI_ADMIN)"
    )
    public ResponseEntity<AgentApiResponse<Object>> getToken(
            @Valid @RequestBody LoginRequest request) {

        log.info("🔑 Demande de token pour: {}", request.getUsername());

        String tokenUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type",    "password");
        params.add("client_id",     clientId);
        params.add("client_secret", clientSecret);
        params.add("username",      request.getUsername());
        params.add("password",      request.getPassword());
        params.add("scope",         "openid profile email");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    tokenUrl,
                    new HttpEntity<>(params, headers),
                    Map.class
            );

            log.info("✅ Token émis pour: {}", request.getUsername());
            return ResponseEntity.ok(AgentApiResponse.success(response.getBody(), null));

        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("❌ Authentification échouée pour: {}", request.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AgentApiResponse.error("INVALID_CREDENTIALS",
                            "Identifiants incorrects. Vérifiez votre username et mot de passe.", null));

        } catch (Exception e) {
            log.error("❌ Erreur lors de l'obtention du token", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(AgentApiResponse.error("KEYCLOAK_UNAVAILABLE",
                            "Le serveur d'authentification Keycloak est indisponible.", null));
        }
    }

    // ── POST /refresh — Rafraîchir un token expiré ────────────────────

    @PostMapping("/refresh")
    @Operation(summary = "Rafraîchir un Access Token avec le Refresh Token")
    public ResponseEntity<AgentApiResponse<Object>> refreshToken(
            @Valid @RequestBody RefreshRequest request) {

        String tokenUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type",    "refresh_token");
        params.add("client_id",     clientId);
        params.add("client_secret", clientSecret);
        params.add("refresh_token", request.getRefreshToken());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    tokenUrl,
                    new HttpEntity<>(params, headers),
                    Map.class
            );
            return ResponseEntity.ok(AgentApiResponse.success(response.getBody(), null));

        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AgentApiResponse.error("INVALID_REFRESH_TOKEN",
                            "Refresh token invalide ou expiré. Reconnectez-vous.", null));
        }
    }

    // ── POST /logout — Invalider un token ────────────────────────────

    @PostMapping("/logout")
    @Operation(summary = "Invalider le token (logout Keycloak)")
    public ResponseEntity<AgentApiResponse<Object>> logout(
            @Valid @RequestBody LogoutRequest request) {

        String logoutUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/logout";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("client_id",     clientId);
        params.add("client_secret", clientSecret);
        params.add("refresh_token", request.getRefreshToken());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            restTemplate.postForEntity(logoutUrl, new HttpEntity<>(params, headers), Void.class);
            log.info("🚪 Logout réussi");
            return ResponseEntity.ok(AgentApiResponse.success(
                    Map.of("message", "Déconnexion réussie"), null));

        } catch (Exception e) {
            log.warn("⚠️ Erreur lors du logout: {}", e.getMessage());
            return ResponseEntity.ok(AgentApiResponse.success(
                    Map.of("message", "Déconnexion effectuée localement"), null));
        }
    }

    // ── GET /me — Informations utilisateur courant ────────────────────

    @GetMapping("/me")
    @Operation(summary = "Informations de l'utilisateur authentifié")
    public ResponseEntity<AgentApiResponse<Object>> me(
            @RequestHeader("Authorization") String authHeader) {

        String userInfoUrl = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/userinfo";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    userInfoUrl,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class
            );
            return ResponseEntity.ok(AgentApiResponse.success(response.getBody(), null));

        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(AgentApiResponse.error("UNAUTHORIZED", "Token invalide ou expiré.", null));
        }
    }
}
