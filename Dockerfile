# ══════════════════════════════════════
# Stage 1 — Build
# ══════════════════════════════════════
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /build

# Installer Maven
RUN apk add --no-cache maven

# Copier les POM en premier (cache Docker)
COPY pom.xml .
COPY ai-agent-core/pom.xml           ai-agent-core/
COPY ai-agent-security/pom.xml       ai-agent-security/
COPY ai-agent-infrastructure/pom.xml ai-agent-infrastructure/
COPY ai-agent-service/pom.xml        ai-agent-service/
COPY ai-agent-api/pom.xml            ai-agent-api/
COPY ai-agent-bootstrap/pom.xml      ai-agent-bootstrap/

# Copier les sources
COPY ai-agent-core/src           ai-agent-core/src
COPY ai-agent-security/src       ai-agent-security/src
COPY ai-agent-infrastructure/src ai-agent-infrastructure/src
COPY ai-agent-service/src        ai-agent-service/src
COPY ai-agent-api/src            ai-agent-api/src
COPY ai-agent-bootstrap/src      ai-agent-bootstrap/src

# Builder le JAR
RUN mvn clean package -DskipTests -B \
    -pl ai-agent-bootstrap -am

# ══════════════════════════════════════
# Stage 2 — Runtime
# ══════════════════════════════════════
FROM eclipse-temurin:17-jre-alpine AS runtime

WORKDIR /app

# Copier le JAR depuis le bon chemin
COPY --from=builder \
    /build/ai-agent-bootstrap/target/ai-agent-bootstrap-2.0.0.jar \
    app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD wget -q --spider http://localhost:8080/api/v1/agent/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]