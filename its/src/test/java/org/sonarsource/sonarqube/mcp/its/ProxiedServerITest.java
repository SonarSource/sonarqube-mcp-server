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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for proxied MCP server binary validation.
 * <p>
 * These tests validate that the proxied server binary:
 * - Can be deployed to a container
 * - Has correct permissions
 * - Can execute in the target environment (Alpine Linux)
 * <p>
 * Note: Tests are only enabled when the sonar-code-context-mcp binary exists in resources.
 */
@Testcontainers
@Disabled("Waiting for a way to download the binary")
class ProxiedServerITest {
  
  private static final String BINARY_PATH = "binaries/sonar-code-context-mcp";

  @Container
  private static final GenericContainer<?> stdioServerContainer = createStdioServerContainer();

  @Test
  void should_successfully_connect_to_cag_proxied_server_when_given_enough_time() {
    assertThat(stdioServerContainer.isRunning()).isTrue();

    assertThat(stdioServerContainer.getLogs())
      .contains("Loading proxied MCP servers configuration")
      .contains("Successfully loaded 1 proxied MCP server(s)")
      .contains("Initializing 1 proxied MCP server(s)")
      .contains("Connecting to 'caas' (namespace: context)")
      .contains("Connected to 'caas' - discovered 1 tool(s)")
      .contains("MCP client manager initialization completed. 1/1 server(s) connected")
      .contains("Loaded 1 proxied tool(s) from 1/1 server(s)");
  }

  private static GenericContainer<?> createStdioServerContainer() {
    var jarPath = System.getProperty("sonarqube.mcp.jar.path");
    if (jarPath == null || jarPath.isEmpty()) {
      throw new IllegalStateException("sonarqube.mcp.jar.path system property not set");
    }

    var sonarqubeToken = System.getenv("SONARCLOUD_IT_TOKEN");
    if (sonarqubeToken == null || sonarqubeToken.isEmpty()) {
      throw new IllegalStateException("SONARCLOUD_IT_TOKEN must be set");
    }

    var container = new GenericContainer<>("eclipse-temurin:21-jre-alpine")
      .withCopyFileToContainer(MountableFile.forHostPath(jarPath), "/app/server.jar")
        .withCopyFileToContainer(
          MountableFile.forClasspathResource(BINARY_PATH, 0755),
          "/app/binaries/sonar-code-context-mcp"
        )
        .withCopyFileToContainer(
          MountableFile.forClasspathResource("proxied-mcp-servers-its.json"),
          "/app/proxied-mcp-servers.json"
        )
        .withEnv("STORAGE_PATH", "/app/storage")
      .withEnv("SONARQUBE_TOKEN", sonarqubeToken)
      .withEnv("SONARQUBE_ORG", "sonarlint-it")
      .withEnv("SONARQUBE_CLOUD_URL", "https://sc-staging.io")
        .withCommand("sh", "-c",
          "apk add --no-cache git nodejs npm && " +
            "mkdir -p /app/storage && " +
            "tail -f /dev/null | java -Dproxied.mcp.servers.config.path=/app/proxied-mcp-servers.json -jar /app/server.jar"
        )
        .withStartupTimeout(Duration.ofMinutes(3))
        .waitingFor(Wait.forLogMessage(".*SonarQube MCP Server Started.*", 1).withStartupTimeout(Duration.ofMinutes(3)));

      container.withLogConsumer(outputFrame -> System.out.print("[STDIO-Container] " + outputFrame.getUtf8String()));
      return container;
    }

}
