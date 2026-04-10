FROM eclipse-temurin:25.0.2_10-jdk-alpine AS builder

RUN apk update &&  \
    apk add binutils

WORKDIR /app

ADD https://binaries.sonarsource.com/Distribution/sonarqube-mcp-server/sonarqube-mcp-server-1.15.1.2402.jar ./sonarqube-mcp-server.jar

RUN jdeps --ignore-missing-deps -q  \
    --recursive  \
    --multi-release 21  \
    --print-module-deps  \
    /app/sonarqube-mcp-server.jar > modules.txt

RUN "$JAVA_HOME"/bin/jlink \
         --verbose \
         --add-modules $(cat modules.txt) \
         --add-modules jdk.crypto.cryptoki,jdk.crypto.ec \
         --strip-debug \
         --no-man-pages \
         --no-header-files \
         --compress=2 \
         --output /optimized-jdk-21

FROM alpine:3.23.3
ENV JAVA_HOME=/opt/jdk/jdk-21
ENV PATH="${JAVA_HOME}/bin:${PATH}"

COPY --from=builder /optimized-jdk-21 $JAVA_HOME

RUN apk add --no-cache \
        ca-certificates \
        git \
        nodejs=~24 \
        npm \
        sudo && \
        addgroup -S appgroup && adduser -S appuser -G appgroup && \
        mkdir -p /home/appuser/.sonarlint /app/storage && \
        chown -R appuser:appgroup /home/appuser /app/storage && \
        echo "appuser ALL=(ALL) NOPASSWD: /usr/sbin/update-ca-certificates" > /etc/sudoers.d/appuser && \
        chmod 0440 /etc/sudoers.d/appuser

ARG TARGETARCH
ARG SONAR_CONTEXT_AUGMENTATION_VERSION=0.6.0.17

RUN case "$TARGETARCH" in \
        amd64) ARCH="x64" ;; \
        arm64) ARCH="arm64" ;; \
        *) echo "Unsupported architecture: $TARGETARCH" && exit 1 ;; \
    esac && \
    wget -qO- "https://binaries.sonarsource.com/Distribution/sonar-context-augmentation-alpine-${ARCH}/sonar-context-augmentation-alpine-${ARCH}-${SONAR_CONTEXT_AUGMENTATION_VERSION}.tar.gz" \
    | tar -xz -C /tmp && \
    install -m 755 /tmp/sonar-context-augmentation /usr/local/bin/sonar-context-augmentation && \
    rm -f /tmp/sonar-context-augmentation

USER appuser

COPY --from=builder --chown=appuser:appgroup --chmod=755 /app/sonarqube-mcp-server.jar /app/sonarqube-mcp-server.jar
COPY --chown=appuser:appgroup --chmod=755 scripts/install-certificates.sh /usr/local/bin/install-certificates
COPY --chown=appuser:appgroup --chmod=755 scripts/docker-entrypoint.sh /usr/local/bin/docker-entrypoint

WORKDIR /app
ENV STORAGE_PATH=/app/storage
LABEL io.modelcontextprotocol.server.name="io.github.SonarSource/sonarqube-mcp-server"

ENTRYPOINT ["/usr/local/bin/docker-entrypoint"]
