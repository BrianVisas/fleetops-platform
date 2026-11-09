# ---- build ----
# A jar is the same on every architecture, so the Maven stage runs once, on the
# build machine's own architecture ($BUILDPLATFORM), even for an arm64 image.
# Only the runtime stage below is built per target platform. See ADR-0005.
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /build

# Copy the descriptor first so dependency resolution is cached separately from
# the source: editing a Java file then does not re-download the world.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

# ---- run ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# curl is only here so compose can health-check the container. In Kubernetes the
# probes are HTTP requests made by the kubelet, so this layer can go away later -
# at the cost of a few more packages for Trivy to scan in the meantime.
#
# Upgrade first: the base image tag lags Ubuntu security updates (apt itself had
# CVE-2026-109518 on the first clean scan). Same reason the Go images run apk upgrade.
RUN apt-get update \
 && apt-get upgrade -y \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# Run as a normal user: nothing in this service needs root, and Phase 8 enforces
# non-root containers with a Kyverno policy.
RUN useradd --system --uid 10001 --create-home fleetops
USER 10001

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080

# Container-aware heap settings; without them the JVM sizes itself from the host.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"

HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
  CMD curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
