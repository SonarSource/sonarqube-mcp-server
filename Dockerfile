FROM eclipse-temurin:21-jdk-alpine AS builder

RUN apk update &&  \
    apk add binutils

WORKDIR /app

ADD https://github.com/SonarSource/sonarqube-mcp-server/releases/download/1.0.1.1435/sonarqube-mcp-server-1.0.1.1435.jar ./sonarqube-mcp-server.jar

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

FROM alpine:3.21.3
ENV JAVA_HOME=/opt/jdk/jdk-21
ENV PATH="${JAVA_HOME}/bin:${PATH}"

COPY --from=builder /optimized-jdk-21 $JAVA_HOME

RUN apk add --no-cache \
        ca-certificates \
        nodejs=~22 \
        npm \
        sudo && \
        addgroup -S appgroup && adduser -S appuser -G appgroup && \
        mkdir -p /home/appuser/.sonarlint ./storage && \
        chown -R appuser:appgroup /home/appuser ./storage && \
        echo "appuser ALL=(ALL) NOPASSWD: /usr/sbin/update-ca-certificates" > /etc/sudoers.d/appuser && \
        chmod 0440 /etc/sudoers.d/appuser

COPY --from=builder --chown=appuser:appgroup --chmod=755 /app/sonarqube-mcp-server.jar /app/sonarqube-mcp-server.jar
COPY --chown=appuser:appgroup --chmod=755 scripts/install-certificates.sh /usr/local/bin/install-certificates

USER appuser
WORKDIR /app
ENV STORAGE_PATH=./storage
LABEL io.modelcontextprotocol.server.name="io.github.SonarSource/sonarqube-mcp-server"

ENTRYPOINT ["/bin/sh", "-c", "/usr/local/bin/install-certificates && exec java -jar /app/sonarqube-mcp-server.jar"]
