# Multi-stage build for optimized Docker image

# Stage 1: Build
FROM gradle:8.5-jdk21 AS builder
WORKDIR /app

# (Optional but better for caching) copy wrapper & gradle files first
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew

# Warm dependency cache (no source yet)
RUN ./gradlew --no-daemon dependencies || true

# Copy source and build
COPY src src
RUN ./gradlew clean build --no-daemon -x test

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring

# Copy only the runnable Spring Boot jar (won’t match the -plain.jar)
COPY --from=builder /app/build/libs/*-SNAPSHOT.jar /app/app.jar

RUN chown -R spring:spring /app
USER spring:spring

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "/app/app.jar"]
