# 🏢 Enterprise AI Code Agent — v2.0.0

Agent IA **production-ready** intégré dans une architecture Spring Boot multi-modules avec sécurité **Keycloak OAuth2/OIDC**, cache **Redis**, résilience **Resilience4j** et observabilité complète.

---

## 🏗️ Architecture Multi-Modules

```
enterprise-ai-agent/                    ← Parent POM (BOM centralisé)
│
├── ai-agent-core/                      ← Domaine métier pur (aucune dépendance Spring)
│   └── domain/AgentQuery, AgentResult
│   └── exception/ (hiérarchie d'exceptions)
│
├── ai-agent-security/                  ← Sécurité Keycloak complète
│   └── config/SecurityConfig.java      ← Spring Security + OAuth2
│   └── service/KeycloakJwtConverter    ← Extraction des rôles JWT
│   └── filter/JwtAuditFilter           ← Logs MDC + corrélation
│   └── filter/RateLimitFilter          ← Rate limiting Redis
│
├── ai-agent-infrastructure/            ← Couche technique
│   └── anthropic/AnthropicApiClient    ← Client HTTP + Circuit Breaker
│   └── config/InfrastructureConfig     ← WebClient, Redis, Cache
│
├── ai-agent-service/                   ← Logique métier
│   └── agent/EnterpriseAgentService    ← Orchestration + @PreAuthorize
│   └── prompt/PromptBuilderService     ← Prompt engineering
│   └── audit/AuditService              ← Audit asynchrone Redis
│
├── ai-agent-api/                       ← Couche présentation REST
│   └── controller/                     ← Endpoints + Swagger
│   └── dto/request, response/          ← DTOs + AgentApiResponse<T>
│   └── advice/GlobalExceptionHandler   ← Erreurs standardisées
│
├── ai-agent-bootstrap/                 ← 🚀 Point d'entrée exécutable
│   └── application.yml                 ← Configuration complète
│
├── keycloak/
│   └── ai-agent-realm.json             ← Realm pré-configuré (import auto)
│
├── docker/
│   ├── prometheus.yml
│   └── grafana-datasources.yml
│
├── docker-compose.yml                  ← Stack complète
└── Dockerfile                          ← Multi-stage build optimisé
```

---

## 🔐 Architecture Sécurité Keycloak

```
   ┌────────────┐     1. Login          ┌─────────────────┐
   │   Client   │ ─────────────────────▶│    Keycloak     │
   │ (Frontend) │                       │  :8180          │
   │            │ ◀─────────────────────│ ai-agent-realm  │
   └────────────┘     2. JWT Token      └─────────────────┘
         │
         │  3. Bearer Token
         ▼
   ┌────────────────────────────────────────────────────────┐
   │              AI Agent API :8080                        │
   │                                                        │
   │  RateLimitFilter  →  JwtAuditFilter  →  SecurityConfig │
   │       Redis              MDC            JWT Validation  │
   │                                                        │
   │  ┌─────────────────────────────────────┐              │
   │  │  KeycloakJwtConverter               │              │
   │  │  realm_access.roles → ROLE_AI_USER  │              │
   │  │  realm_access.roles → ROLE_AI_ADMIN │              │
   │  └─────────────────────────────────────┘              │
   └────────────────────────────────────────────────────────┘
```

### Rôles Keycloak

| Rôle | Endpoints autorisés | Limite |
|------|--------------------|---------| 
| `AI_USER` | `/ask`, `/review`, `/chat` | 30 req/min |
| `AI_ADMIN` | Tout + `/security-audit`, `/admin/stats` | 100 req/min |
| `AI_READONLY` | `/health` uniquement | 5 req/min |

---

## 🚀 Démarrage rapide

### Prérequis
- Docker Desktop 4.x+
- Java 21+ (pour développement local)
- Maven 3.9+

### 1. Cloner et configurer

```bash
# Configurer la clé API Anthropic
export ANTHROPIC_API_KEY=sk-ant-api03-xxxxxxx
```

### 2. Démarrer la stack complète

```bash
docker-compose up -d
```

Attendre ~60 secondes que Keycloak démarre, puis vérifier :

```bash
# ✅ API Spring Boot
curl http://localhost:8080/api/v1/agent/health

# ✅ Keycloak
curl http://localhost:8180/health/ready
```

### 3. Obtenir un token JWT Keycloak

```bash
# Token pour user-demo (ROLE_AI_USER)
TOKEN=$(curl -s -X POST \
  "http://localhost:8180/realms/ai-agent-realm/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=ai-agent-client" \
  -d "client_secret=ai-agent-secret-change-in-production" \
  -d "username=user-demo" \
  -d "password=User@1234" \
  | jq -r '.access_token')

echo "Token: $TOKEN"
```

### 4. Appeler l'API avec authentification

```bash
# Question technique
curl -X POST http://localhost:8080/api/v1/agent/ask \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "Comment implémenter le pattern CQRS avec Spring Boot ?",
    "language": "java",
    "context": "Application Spring Boot 3.2 avec PostgreSQL"
  }'

# Revue de code
curl -X POST http://localhost:8080/api/v1/agent/review \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "code": "String q = \"SELECT * FROM users WHERE id = \" + id;",
    "language": "java",
    "analysisType": "SECURITE"
  }'
```

---

## 📊 Monitoring

| URL | Service | Credentials |
|-----|---------|-------------|
| http://localhost:8080/swagger-ui.html | Swagger UI | JWT Keycloak |
| http://localhost:8180 | Keycloak Admin | admin / admin |
| http://localhost:9090 | Prometheus | - |
| http://localhost:3000 | Grafana | admin / admin |
| http://localhost:8080/actuator/metrics | Spring Metrics | - |

---

## ⚙️ Patterns Enterprise implémentés

| Pattern | Implémentation |
|---------|---------------|
| **Multi-modules Maven** | 6 modules avec BOM centralisé |
| **OAuth2/OIDC** | Keycloak + Spring Security Resource Server |
| **RBAC** | `@PreAuthorize("hasRole('AI_ADMIN')")` |
| **Circuit Breaker** | Resilience4j sur client Anthropic |
| **Retry + Exponential Backoff** | 3 tentatives, délai x2 |
| **Rate Limiting** | Redis sliding window par userId |
| **Cache distribué** | Redis avec TTL par type de données |
| **Audit asynchrone** | `@Async` + Redis + logs MDC |
| **Observabilité** | Micrometer + Prometheus + Grafana |
| **Containerisation** | Docker multi-stage, user non-root |
| **API First** | OpenAPI 3 + Swagger UI avec auth Keycloak |
| **Gestion des erreurs** | Hiérarchie d'exceptions + codes métier |

---

## 🔑 Comptes de démo Keycloak

| Utilisateur | Mot de passe | Rôle |
|-------------|-------------|------|
| `user-demo` | `User@1234` | AI_USER |
| `admin-demo` | `Admin@1234` | AI_USER + AI_ADMIN |

**Console Keycloak Admin** : http://localhost:8180 → admin / admin
