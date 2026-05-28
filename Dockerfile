FROM eclipse-temurin:25-jdk AS builder
WORKDIR /workspace

ENV GRADLE_USER_HOME=/root/.gradle

COPY gradlew gradle/ ./
COPY gradle/ ./gradle/
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY backend/ ./backend/
COPY frontend/ ./frontend/

RUN chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle ./gradlew buildAll --no-daemon --console=plain -x test

# ---------------------------------------------------

FROM eclipse-temurin:25-jre

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Copy the built jar from builder stage
COPY --from=builder /workspace/backend/build/libs/*-all.jar /app/app.jar

# Runtime configuration
ENV JAVA_OPTS="-Xms256m -Xmx768m -Dmicronaut.environments=prod"
ENV SECURE_FILE_UPLOAD_STORAGE_DIR="/data/uploads"
EXPOSE 8080

# Create a non-root user
RUN useradd -m -u 1000 appuser || true
RUN mkdir -p /data/uploads && chown -R 1000:1000 /data
VOLUME ["/data/uploads"]
USER 1000

# Use exec form so signals are forwarded
ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
