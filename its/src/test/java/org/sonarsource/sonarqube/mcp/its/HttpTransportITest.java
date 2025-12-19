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

@Testcontainers
class HttpTransportITest {

  private static final int MCP_HTTP_PORT = 8888;
  
  @Container
  private static final GenericContainer<?> httpServerContainer = createHttpServerContainer();

  @Test
  void should_start_server_with_http_transport() {
    assertThat(httpServerContainer.isRunning()).isTrue();
    var logs = httpServerContainer.getLogs();
    assertThat(logs).contains("Created HTTP transport provider", "Transport: HTTP");
  }

  @Test
  void should_load_external_provider_configuration_with_http() {
    var logs = httpServerContainer.getLogs();
    assertThat(logs)
      .contains("Loading external tool providers configuration from bundled resource")
      .contains("Successfully loaded and validated 1 external tool provider(s)")
      .contains("Initializing 1 external tool provider(s)")
      .contains("Connecting to 'caas' (namespace: context)")
      .contains("All tools loaded:");
  }

  @Test
  void should_provide_accessible_http_endpoint() throws Exception {
    var result = httpServerContainer.execInContainer(
      "wget", "-qO-", "http://localhost:" + MCP_HTTP_PORT + "/mcp"
    );
    assertThat(result.getExitCode()).isIn(0, 6); // 0=success, 6=auth required
  }

  @Test
  void should_handle_http_security_warnings() {
    var logs = httpServerContainer.getLogs();
    assertThat(logs)
      .contains("SECURITY WARNING: MCP HTTP server is configured to bind to all network interfaces")
      .contains("SECURITY WARNING: MCP server is using HTTP without SSL/TLS encryption");
  }

  @Test
  void should_successfully_connect_to_caas_external_provider_when_given_enough_time() throws Exception {
    assertThat(httpServerContainer.isRunning()).isTrue();
    
    var logs = httpServerContainer.getLogs();
    assertThat(logs)
      .contains("Connected to 'caas' - discovered 10 tool(s)")
      .contains("MCP client manager initialization completed. 1/1 server(s) connected")
      .contains("Loaded 10 external tool(s) from 1/1 provider(s)");
  }

  @Test
  void should_complete_http_server_lifecycle() {
    assertThat(httpServerContainer.isRunning()).isTrue();
    var logs = httpServerContainer.getLogs();
    assertThat(logs).contains("SonarQube MCP Server Started:", "Transport: HTTP");
  }

  private static GenericContainer<?> createHttpServerContainer() {
    var jarPath = System.getProperty("sonarqube.mcp.jar.path");
    if (jarPath == null || jarPath.isEmpty()) {
      throw new IllegalStateException("sonarqube.mcp.jar.path system property not set");
    }

    var container = new GenericContainer<>("eclipse-temurin:21-jre-alpine")
      .withExposedPorts(MCP_HTTP_PORT)
      .withCopyFileToContainer(MountableFile.forHostPath(jarPath), "/app/server.jar")
      .withCopyFileToContainer(
        MountableFile.forClasspathResource("binaries/sonar-code-context-mcp", 0755),
        "/app/binaries/sonar-code-context-mcp"
      )
      .withCopyFileToContainer(
        MountableFile.forClasspathResource("external-tool-providers-its.json"),
        "/app/external-tool-providers.json"
      )
      .withEnv("STORAGE_PATH", "/app/storage")
      .withEnv("SONARQUBE_TOKEN", "test-token")
      .withEnv("SONARQUBE_ORG", "test-org")
      .withEnv("SONARQUBE_URL", "https://sonarcloud.io")
      .withEnv("SONARQUBE_TRANSPORT", "http")
      .withEnv("SONARQUBE_HTTP_PORT", String.valueOf(MCP_HTTP_PORT))
      .withEnv("SONARQUBE_HTTP_HOST", "0.0.0.0")
      .withCommand("sh", "-c",
        "apk add --no-cache wget git nodejs npm && " +
        "mkdir -p /app/storage && " +
        "java -jar /app/server.jar"
      )
      .withStartupTimeout(Duration.ofMinutes(3))
      .waitingFor(Wait.forLogMessage(".*started.*", 1).withStartupTimeout(Duration.ofMinutes(2)));
    
    container.withLogConsumer(outputFrame -> System.out.print("[HTTP-Container] " + outputFrame.getUtf8String()));
    return container;
  }
}

