FROM eclipse-temurin:21-jdk-alpine AS builder

RUN apk update &&  \
    apk add binutils

ARG APP_VERSION=

WORKDIR /app

COPY build/libs/sonar-mcp-server-${APP_VERSION}.jar /app/sonar-mcp-server.jar

RUN jdeps --ignore-missing-deps -q  \
    --recursive  \
    --multi-release 21  \
    --print-module-deps  \
    /app/sonar-mcp-server.jar > modules.txt

RUN "$JAVA_HOME"/bin/jlink \
         --verbose \
         --add-modules $(cat modules.txt) \
         --strip-debug \
         --no-man-pages \
         --no-header-files \
         --compress=2 \
         --output /optimized-jdk-21

FROM alpine:3.21.3
ENV JAVA_HOME=/opt/jdk/jdk-21
ENV PATH="${JAVA_HOME}/bin:${PATH}"

COPY --from=builder /optimized-jdk-21 $JAVA_HOME

ARG APP_VERSION=

RUN addgroup -S appgroup && adduser -S appuser -G appgroup && \
    mkdir -p /home/appuser/.sonarlint /data/storage && \
    chown -R appuser:appgroup /home/appuser /data/storage

WORKDIR /app

COPY --chown=appuser:appgroup --chmod=755 build/libs/sonar-mcp-server-${APP_VERSION}.jar /app/sonar-mcp-server.jar

USER appuser

ENV STORAGE_PATH=/data/storage
ENV PLUGIN_PATH=/app/plugins
ENV SONARQUBE_CLOUD_TOKEN=
ENV SONARQUBE_CLOUD_ORG=

ENTRYPOINT ["java", "-jar", "/app/sonar-mcp-server.jar"]
