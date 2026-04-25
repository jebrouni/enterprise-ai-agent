package com.enterprise.aiagent.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * ┌──────────────────────────────────────────────────────────────────────┐
 *  KEYCLOAK JWT CONVERTER
 *
 *  Keycloak structure ses JWT différemment du standard Spring Security.
 *  Ce converter extrait les rôles depuis la structure Keycloak :
 *
 *  {
 *    "realm_access": {
 *      "roles": ["AI_USER", "AI_ADMIN", "offline_access"]
 *    },
 *    "resource_access": {
 *      "ai-agent-client": {
 *        "roles": ["AI_USER"]
 *      }
 *    },
 *    "preferred_username": "john.doe",
 *    "sub": "uuid-de-l-utilisateur"
 *  }
 *
 *  → Traduit en GrantedAuthority : ROLE_AI_USER, ROLE_AI_ADMIN
 * └──────────────────────────────────────────────────────────────────────┘
 */
@Slf4j
@Component
public class KeycloakJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Value("${keycloak.resource:ai-agent-client}")
    private String keycloakClientId;

    private final JwtGrantedAuthoritiesConverter defaultConverter =
            new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // Combiner les authorities du converter standard + les rôles Keycloak
        Collection<GrantedAuthority> authorities = Stream.concat(
                defaultConverter.convert(jwt).stream(),
                extractKeycloakRoles(jwt).stream()
        ).collect(Collectors.toSet());

        String username = jwt.getClaimAsString("preferred_username");
        log.debug("🔑 JWT converti pour: {} | Rôles: {}", username,
                authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.joining(", ")));

        return new JwtAuthenticationToken(jwt, authorities, username);
    }

    /**
     * Extrait les rôles depuis realm_access ET resource_access (client-specific).
     */
    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractKeycloakRoles(Jwt jwt) {
        Set<GrantedAuthority> roles = new HashSet<>();

        // ── Rôles du realm (globaux) ────────────────────────────────────
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null) {
            List<String> realmRoles = (List<String>) realmAccess.get("roles");
            if (realmRoles != null) {
                realmRoles.stream()
                        .filter(role -> !role.startsWith("default-") && !role.startsWith("offline_"))
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                        .forEach(roles::add);
            }
        }

        // ── Rôles du client (spécifiques à l'application) ───────────────
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess != null) {
            Map<String, Object> clientAccess = (Map<String, Object>) resourceAccess.get(keycloakClientId);
            if (clientAccess != null) {
                List<String> clientRoles = (List<String>) clientAccess.get("roles");
                if (clientRoles != null) {
                    clientRoles.stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                            .forEach(roles::add);
                }
            }
        }

        return roles;
    }
}
