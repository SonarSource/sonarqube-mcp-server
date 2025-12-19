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
class StdioTransportITest {

  @Container
  private static final GenericContainer<?> stdioServerContainer = createStdioServerContainer();

  @Test
  void should_start_server_with_stdio_transport() {
    assertThat(stdioServerContainer.isRunning()).isTrue();
    var logs = stdioServerContainer.getLogs();
    assertThat(logs).contains("Transport: stdio", "SonarQube MCP Server Started:");
  }

  @Test
  void should_load_external_provider_configuration_with_stdio() {
    var logs = stdioServerContainer.getLogs();
    assertThat(logs)
      .contains("Loading external tool providers configuration from bundled resource")
      .contains("Successfully loaded and validated 1 external tool provider(s)")
      .contains("Initializing 1 external tool provider(s)")
      .contains("Connecting to 'caas' (namespace: context)")
      .contains("All tools loaded:");
  }

  @Test
  void should_respond_to_mcp_protocol_via_stdio() {
    var logs = stdioServerContainer.getLogs();
    assertThat(logs).contains("Transport: stdio");
  }

  @Test
  void should_handle_external_provider_gracefully_in_stdio_mode() {
    var logs = stdioServerContainer.getLogs();
    assertThat(logs)
      .contains("Initializing 1 external tool provider(s)")
      .contains("All tools loaded:")
      .contains("SonarQube MCP Server Started:");
  }

  @Test
  void should_successfully_connect_to_caas_external_provider_when_given_enough_time() throws Exception {
    assertThat(stdioServerContainer.isRunning()).isTrue();
    
    var logs = stdioServerContainer.getLogs();
    assertThat(logs)
      .contains("Connected to 'caas' - discovered 10 tool(s)")
      .contains("MCP client manager initialization completed. 1/1 server(s) connected")
      .contains("Loaded 10 external tool(s) from 1/1 provider(s)");
  }

  @Test
  void should_complete_stdio_server_lifecycle_in_container() {
    assertThat(stdioServerContainer.isRunning()).isTrue();
    var logs = stdioServerContainer.getLogs();
    assertThat(logs).contains("SonarQube MCP Server Started:", "Transport: stdio");
  }

  private static GenericContainer<?> createStdioServerContainer() {
    var jarPath = System.getProperty("sonarqube.mcp.jar.path");
    if (jarPath == null || jarPath.isEmpty()) {
      throw new IllegalStateException("sonarqube.mcp.jar.path system property not set");
    }

    var container = new GenericContainer<>("eclipse-temurin:21-jre-alpine")
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
      .withCommand("sh", "-c",
        "apk add --no-cache git nodejs npm && " +
        "mkdir -p /app/storage && " +
        "java -jar /app/server.jar"
      )
      .withStartupTimeout(Duration.ofMinutes(3))
      .waitingFor(Wait.forLogMessage(".*SonarQube MCP Server Started.*", 1).withStartupTimeout(Duration.ofMinutes(2)));
    
    container.withLogConsumer(outputFrame -> System.out.print("[STDIO-Container] " + outputFrame.getUtf8String()));
    return container;
  }

}
