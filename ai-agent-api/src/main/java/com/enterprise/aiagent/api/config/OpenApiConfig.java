package com.enterprise.aiagent.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * ┌──────────────────────────────────────────────────────────────────────┐
 *  CONFIGURATION OPENAPI 3 / SWAGGER UI
 *
 *  Swagger UI accessible sur : http://localhost:8080/swagger-ui.html
 *
 *  Authentification intégrée :
 *  Le bouton "Authorize" dans Swagger UI utilise le flux OAuth2
 *  Password directement vers Keycloak, puis injecte le Bearer Token
 *  dans toutes les requêtes suivantes.
 * └──────────────────────────────────────────────────────────────────────┘
 */
@Configuration
public class OpenApiConfig {

    @Value("${keycloak.auth-server-url:http://localhost:8180}")
    private String keycloakUrl;

    @Value("${keycloak.realm:ai-agent-realm}")
    private String realm;

    @Bean
    public OpenAPI openAPI() {

        String tokenUrl    = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";
        String authUrl     = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/auth";
        String refreshUrl  = keycloakUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        return new OpenAPI()
                .info(apiInfo())
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Serveur de développement"),
                        new Server().url("https://api.votredomaine.com").description("Production")
                ))
                .components(new Components()
                        // ── Schéma Bearer Token (saisie manuelle du JWT) ──────────
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Collez ici votre JWT Access Token obtenu via POST /api/v1/auth/token")
                        )
                        // ── OAuth2 Password Flow (login direct depuis Swagger UI) ──
                        .addSecuritySchemes("keycloakOAuth2",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.OAUTH2)
                                        .description("Authentification Keycloak OAuth2 — utilisez les comptes de démo")
                                        .flows(new OAuthFlows()
                                                .password(new OAuthFlow()
                                                        .tokenUrl(tokenUrl)
                                                        .refreshUrl(refreshUrl)
                                                        .scopes(new Scopes()
                                                                .addString("openid", "Identité OpenID")
                                                                .addString("profile", "Profil utilisateur")
                                                                .addString("email",   "Adresse email")
                                                        )
                                                )
                                                .authorizationCode(new OAuthFlow()
                                                        .authorizationUrl(authUrl)
                                                        .tokenUrl(tokenUrl)
                                                        .refreshUrl(refreshUrl)
                                                        .scopes(new Scopes()
                                                                .addString("openid",  "Identité OpenID")
                                                                .addString("profile", "Profil utilisateur")
                                                        )
                                                )
                                        )
                        )
                )
                // Sécurité globale : bearer ou OAuth2 requis sur tous les endpoints sécurisés
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .addSecurityItem(new SecurityRequirement().addList("keycloakOAuth2"));
    }

    private Info apiInfo() {
        return new Info()
                .title("Enterprise AI Code Agent API")
                .version("2.0.0")
                .description("""
                        ## Agent IA Enterprise — Spring Boot + Keycloak
                        
                        API REST sécurisée pour répondre aux questions techniques et analyser du code
                        via le modèle **Claude Sonnet** d'Anthropic.
                        
                        ### Authentification
                        1. Utilisez **POST /api/v1/auth/token** avec les comptes de démo ci-dessous
                        2. Copiez le `access_token` retourné
                        3. Cliquez sur **Authorize** et collez le token (sans "Bearer ")
                        
                        ### Comptes de démo Keycloak
                        | Utilisateur | Mot de passe | Rôle | Accès |
                        |-------------|-------------|------|-------|
                        | `user-demo` | `User@1234` | AI_USER | /ask, /review |
                        | `admin-demo` | `Admin@1234` | AI_ADMIN | Tout + /security-audit, /admin/stats |
                        
                        ### Rate Limiting
                        - AI_USER  : **30 requêtes / minute**
                        - AI_ADMIN : **100 requêtes / minute**
                        """)
                .contact(new Contact()
                        .name("Équipe AI Agent")
                        .email("ai-agent@enterprise.com")
                        .url("https://github.com/enterprise/ai-agent")
                )
                .license(new License()
                        .name("MIT")
                        .url("https://opensource.org/licenses/MIT")
                );
    }
}
