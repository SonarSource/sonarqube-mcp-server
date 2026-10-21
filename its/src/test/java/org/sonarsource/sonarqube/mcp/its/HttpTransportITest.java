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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

  @ParameterizedTest
  @ValueSource(strings = {"Authorization", "SONARQUBE_TOKEN"})
  void it_should_preserve_existing_header_token_clients_with_automatic_authentication(String header) throws Exception {
    var endpoint = URI.create("http://" + httpServerContainer.getHost() + ":" + httpServerContainer.getMappedPort(MCP_HTTP_PORT) + "/mcp");
    var credential = header.equals("Authorization") ? "Bearer existing-token" : "existing-token";
    var request = HttpRequest.newBuilder(endpoint).header(header, credential)
      .header("SONARQUBE_ORG", "existing-org").header("SONARQUBE_READ_ONLY", "true").header("SONARQUBE_TOOLSETS", "projects")
      .header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream")
      .POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}")).build();

    try (var client = HttpClient.newHttpClient()) {
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).contains("\"result\":{}");
    }
  }

  private static GenericContainer<?> createHttpServerContainer() {
    return McpServerTestContainers.builder()
      .withExposedPort(MCP_HTTP_PORT)
      .withEnv("SONARQUBE_TRANSPORT", "http")
      .withEnv("SONARQUBE_ORG", "")
      .withEnv("SONARQUBE_HTTP_PORT", String.valueOf(MCP_HTTP_PORT))
      .withEnv("SONARQUBE_HTTP_HOST", "0.0.0.0")
      .withAdditionalApkPackages("wget", "nodejs")
      .withWaitLogMessage(".*started.*")
      .withLogPrefix("HTTP-Container")
      .build();
  }

}
