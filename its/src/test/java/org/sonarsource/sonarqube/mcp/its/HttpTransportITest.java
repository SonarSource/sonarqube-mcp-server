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

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class HttpTransportITest {

  private static final int MCP_HTTP_PORT = 8888;

  @Container
  private static final GenericContainer<?> httpServerContainer = createHttpServerContainer();

  @Test
  void should_start_server_with_http_transport() {
    assertThat(httpServerContainer.isRunning()).isTrue();

    assertThat(httpServerContainer.getLogs()).contains("Created HTTP transport provider");
  }

  @Test
  void should_provide_accessible_http_endpoint() throws Exception {
    var result = httpServerContainer.execInContainer(
      "wget", "-qO-", "http://localhost:" + MCP_HTTP_PORT + "/mcp"
    );

    // 6=auth required
    assertThat(result.getExitCode()).isEqualTo(6);
  }

  @Test
  void should_handle_http_security_warnings() {
    var logs = httpServerContainer.getLogs();

    assertThat(logs)
      .contains("SECURITY WARNING: MCP HTTP server is configured to bind to all network interfaces")
      .contains("SECURITY WARNING: MCP server is using HTTP without SSL/TLS encryption");
  }

  private static GenericContainer<?> createHttpServerContainer() {
    return McpServerTestContainers.builder()
      .withExposedPort(MCP_HTTP_PORT)
      .withEnv("SONARQUBE_TRANSPORT", "http")
      .withEnv("SONARQUBE_HTTP_PORT", String.valueOf(MCP_HTTP_PORT))
      .withEnv("SONARQUBE_HTTP_HOST", "0.0.0.0")
      .withAdditionalApkPackages("wget", "git", "nodejs", "npm")
      .withWaitLogMessage(".*started.*")
      .withLogPrefix("HTTP-Container")
      .build();
  }

}
