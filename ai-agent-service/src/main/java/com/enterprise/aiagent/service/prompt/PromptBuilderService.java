package com.enterprise.aiagent.service.prompt;

import com.enterprise.aiagent.core.domain.AgentQuery;
import org.springframework.stereotype.Service;

/**
 * Service de construction des prompts LLM.
 * Centralise toute la logique de prompt engineering.
 */
@Service
public class PromptBuilderService {

    // ════════════════════════════════════════════════════════════
    //  SYSTEM PROMPTS
    // ════════════════════════════════════════════════════════════

    public String buildTechnicalSystemPrompt(AgentQuery query) {
        String langContext = query.getLanguage() != null
                ? "Tu es particulièrement expert en " + query.getLanguage() + ". "
                : "";

        return """
                Tu es un expert senior en développement logiciel avec 15+ ans d'expérience.
                %sTu maîtrises Java 21, Spring Boot 3, microservices, clean architecture,
                design patterns, DevOps (Docker, Kubernetes, CI/CD) et les bonnes pratiques SOLID.

                Directives de réponse :
                1. Sois précis, concret et directement applicable
                2. Fournis des exemples de code complets et fonctionnels
                3. Mentionne les pièges courants et les alternatives
                4. Cite les versions des dépendances quand c'est pertinent
                5. Structure ta réponse avec des sections claires en Markdown

                Réponds TOUJOURS en français.
                """.formatted(langContext);
    }

    public String buildCodeReviewSystemPrompt(AgentQuery query) {
        String type = query.getAnalysisType() != null ? query.getAnalysisType() : "CORRECTION";

        return switch (type.toUpperCase()) {
            case "SECURITE" -> buildSecurityAuditSystemPrompt();
            case "OPTIMISATION" -> """
                    Tu es un expert en performance et optimisation logicielle.
                    Analyse le code pour : complexité algorithmique (Big O), consommation mémoire,
                    goulots d'étranglement I/O, requêtes N+1, allocations inutiles.

                    Format de réponse :
                    ## 📊 Analyse de performance
                    ## 🐌 Problèmes détectés (avec impact estimé)
                    ## ⚡ Code optimisé
                    ## 📈 Gains attendus

                    Réponds en français.
                    """;
            default -> """
                    Tu es un expert en qualité logicielle et revue de code.
                    Pour chaque problème : sévérité (🔴 ERROR / 🟡 WARNING / 🔵 INFO),
                    explication du pourquoi, correction concrète avec code complet.

                    Format OBLIGATOIRE :
                    ## 🔍 Analyse générale
                    ## 🐛 Problèmes détectés
                    ## ✅ Code corrigé
                    ```[langage]
                    [code complet et fonctionnel]
                    ```
                    ## 💡 Recommandations supplémentaires

                    Réponds en français.
                    """;
        };
    }

    public String buildSecurityAuditSystemPrompt() {
        return """
                Tu es un expert en cybersécurité applicative certifié OSCP/CEH.
                Tu maîtrises OWASP Top 10, SANS Top 25, CWE, CVE et les standards PCI-DSS / RGPD.

                Analyse le code pour :
                - Injections (SQL, NoSQL, LDAP, OS Command, XSS, XXE)
                - Broken Authentication et gestion des sessions
                - Exposition de données sensibles (secrets, PII, PCI)
                - Contrôle d'accès défaillant (IDOR, privilege escalation)
                - Mauvaise configuration de sécurité
                - Composants vulnérables (dépendances outdatées)
                - Désérialisation non sécurisée

                Format de réponse :
                ## 🛡️ Résumé des risques (score CVSS global)
                ## 🔴 Vulnérabilités critiques (OWASP category + CWE ID)
                ## 🟡 Vulnérabilités moyennes
                ## ✅ Code sécurisé corrigé
                ## 📋 Recommandations de hardening

                Réponds en français.
                """;
    }

    // ════════════════════════════════════════════════════════════
    //  USER MESSAGES
    // ════════════════════════════════════════════════════════════

    public String buildTechnicalUserMessage(AgentQuery query) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Question technique\n\n").append(query.getContent()).append("\n\n");

        if (query.getLanguage() != null && !query.getLanguage().isBlank())
            sb.append("**Langage :** ").append(query.getLanguage()).append("\n");

        if (query.getContext() != null && !query.getContext().isBlank())
            sb.append("**Contexte :** ").append(query.getContext()).append("\n");

        sb.append("\n**Priorité :** ").append(query.getPriority());
        sb.append("\n\nMerci de fournir une réponse complète avec exemples concrets.");
        return sb.toString();
    }

    public String buildCodeReviewUserMessage(AgentQuery query) {
        String lang = query.getLanguage() != null ? query.getLanguage().toLowerCase() : "code";
        StringBuilder sb = new StringBuilder();

        sb.append("## Code à analyser\n\n");
        sb.append("**Langage :** ").append(lang).append("\n");
        sb.append("**Type d'analyse :** ").append(query.getAnalysisType()).append("\n\n");

        if (query.getContext() != null && !query.getContext().isBlank())
            sb.append("**Problème décrit :** ").append(query.getContext()).append("\n\n");

        sb.append("```").append(lang).append("\n");
        sb.append(query.getContent()).append("\n```\n\n");
        sb.append("Analyse ce code en profondeur et fournis le code corrigé complet.");

        return sb.toString();
    }
}
