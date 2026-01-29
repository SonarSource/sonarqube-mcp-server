/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource Sàrl.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.sonarqube.mcp.its;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/**
 * Utility class for creating and configuring MCP Server test containers.
 */
class McpServerTestContainers {

  private static final String BASE_IMAGE = "eclipse-temurin:21-jre-alpine";
  private static final String JAR_PATH_PROPERTY = "sonarqube.mcp.jar.path";
  private static final String TOKEN_ENV_VAR = "SONARCLOUD_IT_TOKEN";
  private static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofMinutes(4);

  private McpServerTestContainers() {
    // Utility class
  }

  static ContainerBuilder builder() {
    return new ContainerBuilder();
  }

  static String getJarPath() {
    var jarPath = System.getProperty(JAR_PATH_PROPERTY);
    if (jarPath == null || jarPath.isEmpty()) {
      throw new IllegalStateException(JAR_PATH_PROPERTY + " system property must be set");
    }
    return jarPath;
  }

  static String getSonarCloudToken() {
    var token = System.getenv(TOKEN_ENV_VAR);
    if (token == null || token.isEmpty()) {
      throw new IllegalStateException(TOKEN_ENV_VAR + " must be set");
    }
    return token;
  }

  static class ContainerBuilder {
    private final Map<String, String> envVars = new HashMap<>();
    private final Map<MountableFile, String> additionalFiles = new HashMap<>();
    private String proxiedServersConfigResource = "empty-proxied-mcp-servers-its.json";
    private String command;
    private String logPrefix = "MCP-Container";
    private String waitLogMessage = ".*SonarQube MCP Server Started.*";
    private Duration startupTimeout = DEFAULT_STARTUP_TIMEOUT;
    private Integer exposedPort;

    private ContainerBuilder() {
      envVars.put("STORAGE_PATH", "/app/storage");
      envVars.put("SONARQUBE_TOKEN", getSonarCloudToken());
      envVars.put("SONARQUBE_ORG", "sonarlint-it");
      envVars.put("SONARQUBE_CLOUD_URL", "https://sc-staging.io");
    }

    ContainerBuilder withProxiedServersConfig(String resourcePath) {
      this.proxiedServersConfigResource = resourcePath;
      return this;
    }

    ContainerBuilder withEnv(String key, String value) {
      this.envVars.put(key, value);
      return this;
    }

    ContainerBuilder withLogPrefix(String prefix) {
      this.logPrefix = prefix;
      return this;
    }

    ContainerBuilder withWaitLogMessage(String logMessage) {
      this.waitLogMessage = logMessage;
      return this;
    }

    ContainerBuilder withStartupTimeout(Duration timeout) {
      this.startupTimeout = timeout;
      return this;
    }

    ContainerBuilder withExposedPort(int port) {
      this.exposedPort = port;
      return this;
    }

    ContainerBuilder withAdditionalApkPackages(String... packages) {
      String apkInstall = packages.length > 0 ? "apk add --no-cache " + String.join(" ", packages) + " && " : "";
      this.command = apkInstall +
        "mkdir -p /app/storage && " +
        "tail -f /dev/null | java -Dproxied.mcp.servers.config.path=/app/proxied-mcp-servers.json -jar /app/server.jar";
      return this;
    }

    ContainerBuilder withCopyFileToContainer(String classpathResourcePath, String containerPath, int fileMode) {
      this.additionalFiles.put(MountableFile.forClasspathResource(classpathResourcePath, fileMode), containerPath);
      return this;
    }

    @SuppressWarnings("resource")
    GenericContainer<?> build() {
      if (command == null) {
        // Default packages: git, nodejs, npm
        withAdditionalApkPackages("git", "nodejs", "npm");
      }

      var container = new GenericContainer<>(BASE_IMAGE)
        .withCopyFileToContainer(MountableFile.forHostPath(getJarPath()), "/app/server.jar")
        .withCopyFileToContainer(
          MountableFile.forClasspathResource(proxiedServersConfigResource),
          "/app/proxied-mcp-servers.json"
        )
        .withCommand("sh", "-c", command)
        .withStartupTimeout(startupTimeout)
        .waitingFor(Wait.forLogMessage(waitLogMessage, 1).withStartupTimeout(startupTimeout));

      // Copy additional files
      additionalFiles.forEach(container::withCopyFileToContainer);

      // Add environment variables
      envVars.forEach(container::withEnv);

      // Expose port if specified
      if (exposedPort != null) {
        container.withExposedPorts(exposedPort);
      }

      // Add log consumer
      container.withLogConsumer(outputFrame -> System.out.print("[" + logPrefix + "] " + outputFrame.getUtf8String()));

      return container;
    }
  }
}
