# ==============================================================================
# Multi-Stage Dockerfile for The Archive // APEX Console
# ==============================================================================
# Stage 1: Build Frontend (React + Vite + Tailwind + Dockview)
# Stage 2: Build Backend (Spring Boot 4 + Gradle 9 + Java 21)
# Stage 3: Runtime Container (Temurin JRE 21 + Headless Chromium + Dumb-Init)
# ==============================================================================

# --- Stage 1: Frontend Build ---
FROM node:20-alpine AS frontend-builder
WORKDIR /app/frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

# --- Stage 2: Backend Build ---
FROM eclipse-temurin:21-jdk AS backend-builder
WORKDIR /app

# Cache Gradle wrapper and dependencies
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/
RUN chmod +x ./gradlew && ./gradlew dependencies --no-daemon

# Copy backend source
COPY src/ src/

# Embed compiled React frontend assets into Spring Boot's static classpath directory
COPY --from=frontend-builder /app/frontend/dist src/main/resources/static/

# Build standalone executable Spring Boot fat JAR
RUN ./gradlew bootJar --no-daemon -x test

# --- Stage 3: Runtime Environment ---
FROM eclipse-temurin:21-jre AS runner

# Install headless Chromium, Chromedriver, system fonts, and dumb-init
RUN apt-get update && apt-get install -y --no-install-recommends \
    chromium \
    chromium-driver \
    fonts-liberation \
    dumb-init \
    curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
RUN mkdir -p /app/data

# Environment configuration
ENV CHROME_BIN=/usr/bin/chromium \
    CHROMEDRIVER_PATH=/usr/bin/chromedriver \
    SPRING_DATASOURCE_URL=jdbc:sqlite:/app/data/archive_vault.db \
    ARCHIVE_STORAGE_VAULT_PATH=/app/data/archive_vault \
    PORT=61069

# Copy built JAR from builder stage
COPY --from=backend-builder /app/build/libs/*.jar /app/app.jar

# Expose APEX Console port
EXPOSE 61069

# Persistent volume for SQLite database and downloaded media
VOLUME ["/app/data"]

# Use dumb-init to properly forward signals and reap zombie Chrome processes
ENTRYPOINT ["dumb-init", "--"]
CMD ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
