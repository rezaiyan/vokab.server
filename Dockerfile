# Multi-stage build for optimized Docker image

# Stage 1: Build
FROM gradle:8.5-jdk21 AS builder

WORKDIR /app

# Set Gradle user home to avoid buildkit processing /home/gradle/.gradle
ENV GRADLE_USER_HOME=/app/.gradle

# Copy Gradle files
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle gradle

# Download dependencies (cached layer) — ignore failure due to missing sources
RUN gradle build --no-daemon || true

# Copy source code
COPY src src

# Build application
RUN gradle clean build --no-daemon -x test

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring

# Copy built JAR from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Create keys directory for JWT RSA keys (will be mounted as volume)
RUN mkdir -p /app/keys && chown -R spring:spring /app/keys

# Create avatar uploads directory (will be mounted as volume)
RUN mkdir -p /var/www/uploads/avatars && chown -R spring:spring /var/www/uploads

# Firebase service account is provided via a secure path or secret at runtime

# Change ownership
RUN chown -R spring:spring /app

# Switch to non-root user
USER spring:spring

# Expose default port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD sh -c 'PORT=${PORT:-8080}; wget --no-verbose --tries=1 --spider "http://localhost:${PORT}/api/v1/health" || exit 1'

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]

