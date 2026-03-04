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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
      .contains("Connecting to 'caas'")
      .contains("Connected to 'caas' - discovered 1 tool(s)")
      .contains("MCP client manager initialization completed. 1/1 server(s) connected")
      .contains("Loaded 1 proxied tool(s) from 1/1 server(s)");
  }

  private static GenericContainer<?> createStdioServerContainer() {
    return McpServerTestContainers.builder()
      .withProxiedServersConfig("proxied-mcp-servers-its.json")
      .withCopyFileToContainer(BINARY_PATH, "/app/binaries/sonar-code-context-mcp", 0755)
      .withStartupTimeout(Duration.ofMinutes(3))
      .withLogPrefix("STDIO-Container")
      .build();
  }

}
