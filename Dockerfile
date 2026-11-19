FROM node:24.22.3-alpine3.24 AS node-runtime

FROM eclipse-temurin:21.0.13_9-jdk-alpine-3.24 AS builder

RUN apk update &&  \
    apk add binutils curl

ARG TARGETARCH
# Install the x64 security update until it is available in the base image.
RUN set -eu; \
    case "$TARGETARCH" in \
        amd64) \
            curl --fail --location --proto '=https' --proto-redir '=https' \
                --output /tmp/openjdk.tar.gz \
                'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13.1%2B2/OpenJDK21U-jdk_x64_alpine-linux_hotspot_21.0.13.1_2.tar.gz'; \
            printf '%s  %s\n' \
                '87af8e1557222db2a29c9d055c34a317916b4ddbdb91474d04b35229d337193e' \
                /tmp/openjdk.tar.gz | sha256sum --check --status; \
            rm -rf "$JAVA_HOME"; \
            mkdir -p "$JAVA_HOME"; \
            tar -xzf /tmp/openjdk.tar.gz --strip-components=1 --no-same-owner -C "$JAVA_HOME"; \
            rm /tmp/openjdk.tar.gz \
            ;; \
        arm64) ;; \
        *) echo "Unsupported architecture: $TARGETARCH" >&2; exit 1 ;; \
    esac; \
    java -version

WORKDIR /app

ARG MCP_SERVER_VERSION=1.32.0.5118
ADD https://binaries.sonarsource.com/Distribution/sonarqube-mcp-server/sonarqube-mcp-server-${MCP_SERVER_VERSION}.jar ./sonarqube-mcp-server.jar

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

FROM alpine:3.24.1
ENV JAVA_HOME=/opt/jdk/jdk-21
ENV PATH="${JAVA_HOME}/bin:${PATH}"

COPY --from=builder /optimized-jdk-21 $JAVA_HOME
COPY --from=node-runtime /usr/local/bin/node /usr/local/bin/node

RUN apk upgrade --no-cache && \
    apk add --no-cache \
        ca-certificates \
        libstdc++ \
        sudo && \
        addgroup -S appgroup && adduser -S appuser -G appgroup && \
        mkdir -p /app/storage && \
        chown -R appuser:appgroup /home/appuser /app/storage && \
        echo "appuser ALL=(ALL) NOPASSWD: /usr/sbin/update-ca-certificates" > /etc/sudoers.d/appuser && \
        chmod 0440 /etc/sudoers.d/appuser

ARG TARGETARCH
# Keep in sync with sonarContextAugmentationVersion in gradle.properties
ARG SONAR_CONTEXT_AUGMENTATION_VERSION=0.27.0.7571

RUN case "$TARGETARCH" in \
        amd64) ARCH="x64" ;; \
        arm64) ARCH="arm64" ;; \
        *) echo "Unsupported architecture: $TARGETARCH" && exit 1 ;; \
    esac && \
    wget -qO- "https://binaries.sonarsource.com/Distribution/sonar-context-augmentation-linux-${ARCH}/sonar-context-augmentation-linux-${ARCH}-${SONAR_CONTEXT_AUGMENTATION_VERSION}.tar.gz" \
    | tar -xz -C /tmp && \
    install -m 755 /tmp/sonar-context-augmentation /usr/local/bin/sonar-context-augmentation && \
    rm -f /tmp/sonar-context-augmentation

USER appuser

COPY --from=builder --chown=appuser:appgroup --chmod=755 /app/sonarqube-mcp-server.jar /app/sonarqube-mcp-server.jar
COPY --chown=appuser:appgroup --chmod=755 scripts/install-certificates.sh /usr/local/bin/install-certificates
COPY --chown=appuser:appgroup --chmod=755 scripts/docker-entrypoint.sh /usr/local/bin/docker-entrypoint

WORKDIR /app
ENV STORAGE_PATH=/app/storage
ENV SONARQUBE_MCP_IN_CONTAINER=true
LABEL io.modelcontextprotocol.server.name="io.github.SonarSource/sonarqube-mcp-server"

ENTRYPOINT ["/usr/local/bin/docker-entrypoint"]
