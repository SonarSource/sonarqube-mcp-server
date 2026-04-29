/*
 * SonarQube MCP Server
 * Copyright (C) SonarSource
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
 * - Is gracefully skipped when missing (no hang, clear error log)
 * - Discovers tools from the staging SonarCloud org (A3S is expected to be pre-enabled on
 *   the {@code sonarlint-it} org in the staging environment)
 * <p>
 * The sonar-context-augmentation binary is downloaded automatically by the
 * {@code downloadCagBinary} Gradle task before tests run.
 */
@Testcontainers
class ProxiedServerITest {

  private static final String BINARY_PATH = "binaries/sonar-context-augmentation";

  @Container
  private static final GenericContainer<?> stdioServerContainer = createStdioServerContainer();

  @Container
  private static final GenericContainer<?> missingBinaryContainer = createMissingBinaryContainer();

  @Test
  void should_successfully_connect_to_cag_proxied_server_when_given_enough_time() {
    assertThat(stdioServerContainer.isRunning()).isTrue();

    assertThat(stdioServerContainer.getLogs())
      .contains("Loading proxied MCP servers configuration")
      .contains("Successfully loaded 1 proxied MCP server(s)")
      .contains("Initializing 1 proxied MCP server(s)")
      .contains("Connecting to 'caas'")
      .containsPattern("Connected to 'caas' - discovered 10 tool\\(s\\)")
      .contains("MCP client manager initialization completed. 1/1 server(s) connected")
      .containsPattern("Loaded 10 proxied tool\\(s\\) from 1/1 server\\(s\\)")
      .contains("Forwarded instructions from 1 proxied server(s)")
      .contains("Proxied instructions: ")
      .contains("## Context Augmentation")
      .contains("All tools loaded: 34 tools after category filtering");
  }

  @Test
  void should_start_server_and_skip_missing_binary_without_hanging() {
    assertThat(missingBinaryContainer.isRunning()).isTrue();

    assertThat(missingBinaryContainer.getLogs())
      .contains("Binary '/app/binaries/this-binary-does-not-exist' not found or not executable, skipping proxied server 'missing-server'")
      .contains("No proxied server binaries are reachable")
      .contains("SonarQube MCP Server Started");
  }

  private static GenericContainer<?> createStdioServerContainer() {
    return McpServerTestContainers.builder()
      .withProxiedServersConfig("proxied-mcp-servers-its.json")
      .withCopyFileToContainer(BINARY_PATH, "/app/binaries/sonar-context-augmentation", 0755)
      .withWorkspaceMount()
      .withStartupTimeout(Duration.ofMinutes(3))
      .withLogPrefix("STDIO-Container")
      .build();
  }

  private static GenericContainer<?> createMissingBinaryContainer() {
    return McpServerTestContainers.builder()
      .withProxiedServersConfig("proxied-mcp-servers-missing-binary-its.json")
      .withStartupTimeout(Duration.ofMinutes(3))
      .withLogPrefix("MISSING-BINARY-Container")
      .build();
  }

}
