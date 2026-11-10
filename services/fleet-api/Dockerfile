# ---- build ----
# A jar is the same on every architecture, so the build stage runs once, on the
# build machine's own architecture ($BUILDPLATFORM), even for an arm64 image.
# Only the runtime stage below is built per target platform. See ADR-0005.
#
# The builder is a plain JDK, and Maven comes from the wrapper (mvnw and
# .mvn/wrapper/maven-wrapper.properties), the same one CI runs. With a maven:*
# image the image was built with whatever Maven that image shipped, while CI used
# the wrapper's version; the two had already drifted apart (3.9.x and 3.10.0)
# when this changed. See EL-019.
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build
WORKDIR /build

# Wrapper and descriptor first, so the Maven download and dependency resolution
# are cached separately from the source: editing a Java file then does not
# re-download the world. `-v` prints the Maven version into the build log, which
# is how you check which Maven built an image. The wrapper downloads Maven with
# curl or wget if the image has them and falls back to Java otherwise, so the
# JDK image needs nothing extra installed.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -v && ./mvnw -B -ntp dependency:go-offline

COPY src ./src
RUN ./mvnw -B -ntp -DskipTests package

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
