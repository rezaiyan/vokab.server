# Multi-stage build for optimized Docker image

# Stage 1: Build
FROM gradle:8.5-jdk21 AS builder

WORKDIR /app

# Copy Gradle files
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle gradle

# Download dependencies (cached layer)
RUN gradle build --no-daemon || return 0

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

# Note: Firebase service account should be provided via Render Secret Files
# It will be mounted at /etc/secrets/firebase-service-account.json
# See RENDER_DEPLOYMENT.md for setup instructions

# Change ownership
RUN chown -R spring:spring /app

# Switch to non-root user
USER spring:spring

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/v1/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]

