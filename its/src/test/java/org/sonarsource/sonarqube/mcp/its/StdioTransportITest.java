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
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

@Testcontainers
class StdioTransportITest {

  @Container
  private static final GenericContainer<?> stdioServerContainer = createStdioServerContainer();

  @Test
  void should_start_server_with_stdio_transport() {
    assertThat(stdioServerContainer.isRunning()).isTrue();

    assertThat(stdioServerContainer.getLogs()).contains("Transport: stdio", "SonarQube MCP Server Started:");
  }

  @Test
  void should_start_server_and_download_analyzers() {
    assertThat(stdioServerContainer.isRunning()).isTrue();

    await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(stdioServerContainer.getLogs())
      .contains("Transport: stdio", "SonarQube MCP Server Started:")
      .contains("Found 12 plugins")
      .contains("Backend restarted with new analyzers"));
  }

  private static GenericContainer<?> createStdioServerContainer() {
    return McpServerTestContainers.builder()
      .withLogPrefix("STDIO-Container")
      .build();
  }

}
