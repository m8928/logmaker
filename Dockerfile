# ============================================================
# LogMaker Docker Image — Multi-stage build
# Stage 1: Build frontend (Node) + backend (Maven)
# Stage 2: Minimal JRE runtime
#
# Build: docker buildx build --platform linux/amd64 -t logmaker .
# Run:   docker run -p 19999:19999 logmaker
# ============================================================

# ── Stage 1: Build ──────────────────────────────────────────
FROM --platform=$BUILDPLATFORM eclipse-temurin:17-jdk AS builder

# Install Node.js for frontend build
RUN apt-get update && apt-get install -y curl \
    && curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y nodejs \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy Maven wrapper and pom files first (cache dependencies)
COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn .mvn
COPY plugin-api/pom.xml plugin-api/
COPY core/pom.xml core/
COPY default-plugin/pom.xml default-plugin/

RUN chmod +x mvnw && ./mvnw dependency:go-offline -B 2>/dev/null || true

# Copy source
COPY plugin-api plugin-api
COPY core core
COPY default-plugin default-plugin
COPY ui ui

# Build frontend
WORKDIR /app/ui
RUN npm ci && npm run build

# Build backend (includes frontend static files)
WORKDIR /app
RUN ./mvnw clean package -DskipTests -B

# ── Stage 2: Runtime ────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="LogMaker" \
      description="LogMaker — Log Generation & Delivery Platform"

WORKDIR /app

# Copy built artifacts
COPY --from=builder /app/core/target/logmaker-core-*-exec.jar app.jar
COPY --from=builder /app/default-plugin/target/default-plugin-*-jar-with-dependencies.jar plugins/

# Create data directory
RUN mkdir -p /app/data /app/plugins

# Environment
ENV JAVA_OPTS="-Xms256m -Xmx512m"

EXPOSE 19999

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --data.root=/app/data --plugin.root=/app/plugins"]
