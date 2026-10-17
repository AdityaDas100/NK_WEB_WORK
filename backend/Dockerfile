# syntax=docker/dockerfile:1.7
# ---------------------------------------------------------------------------
# Spring Boot intake API.
#
#   docker build -t nk-ethical/pharma-inquiry-service:1.0.0 .
#
# Two stages: the build stage resolves dependencies before copying the sources
# (so a source-only change reuses the dependency layer) and the runtime stage
# contains nothing but a JRE and the fat jar, running as a non-root user.
# ---------------------------------------------------------------------------

# --- build -----------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /workspace

# Dependency layer: changes only when pom.xml changes.
COPY pom.xml ./
RUN mvn -B -ntp -q dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests package

# --- runtime ---------------------------------------------------------------
FROM eclipse-temurin:25-jre-noble AS runtime

# curl is only needed for the container health check.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system --gid 1001 pharma \
 && useradd --system --uid 1001 --gid pharma --home /app --shell /usr/sbin/nologin pharma

WORKDIR /app

COPY --from=build /workspace/target/pharma-inquiry-service.jar ./app.jar

# The export directory must be writable by the application user. Mount a volume
# over it in production (see docker-compose.yml).
RUN mkdir -p /app/data/exports /app/logs \
 && chown -R pharma:pharma /app

USER pharma

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -XX:+UseG1GC"

HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=12 \
    CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
