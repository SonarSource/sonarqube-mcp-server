FROM eclipse-temurin:21-jdk-alpine AS builder

RUN apk update &&  \
    apk add binutils

WORKDIR /app

COPY sonarqube-mcp-server.jar ./sonarqube-mcp-server.jar

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
        python3=~3.12 \
        py3-pip \
        sudo && \
        addgroup -S appgroup && adduser -S appuser -G appgroup && \
        mkdir -p /home/appuser/.sonarlint /app/storage && \
        chown -R appuser:appgroup /home/appuser /app/storage && \
        echo "appuser ALL=(ALL) NOPASSWD: /usr/sbin/update-ca-certificates" > /etc/sudoers.d/appuser && \
        chmod 0440 /etc/sudoers.d/appuser

# Install Python dependencies for sonar-code-context
USER appuser
COPY --chown=appuser:appgroup requirements.txt /app/requirements.txt
RUN python3 -m pip install --no-cache-dir --break-system-packages --user -r /app/requirements.txt

# Copy sonar-code-context-mcp binary
COPY --chown=appuser:appgroup --chmod=755 sonar-code-context-mcp /home/appuser/.local/bin/sonar-code-context-mcp

# Ensure the installed binary is in PATH
ENV PATH="/home/appuser/.local/bin:${PATH}"

COPY --from=builder --chown=appuser:appgroup --chmod=755 /app/sonarqube-mcp-server.jar /app/sonarqube-mcp-server.jar
COPY --chown=appuser:appgroup --chmod=755 scripts/install-certificates.sh /usr/local/bin/install-certificates

WORKDIR /app
ENV STORAGE_PATH=/app/storage
ENV SONARQUBE_TOOLSETS=external
LABEL io.modelcontextprotocol.server.name="io.github.SonarSource/sonarqube-mcp-server"

ENTRYPOINT ["/bin/sh", "-c", "/usr/local/bin/install-certificates && exec java -jar /app/sonarqube-mcp-server.jar"]
