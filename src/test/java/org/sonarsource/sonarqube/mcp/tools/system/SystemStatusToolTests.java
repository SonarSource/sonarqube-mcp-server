/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.sonarqube.mcp.tools.system;

import com.github.tomakehurst.wiremock.http.Body;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.sonarsource.sonarqube.mcp.harness.MockWebServer;
import org.sonarsource.sonarqube.mcp.harness.ReceivedRequest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.system.SystemApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SystemStatusToolTests {

  @Nested
  class WithSonarCloudServer {
    @SonarQubeMcpServerTest
    void it_should_not_be_available_for_sonarcloud(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_URL", "https://sonarcloud.io",
        "SONARQUBE_TOKEN", "token",
        "SONARQUBE_ORG", "org"
      ));

      var exception = assertThrows(io.modelcontextprotocol.spec.McpError.class, () -> {
        mcpClient.callTool(new McpSchema.CallToolRequest(
          SystemStatusTool.TOOL_NAME,
          Map.of()));
      });

      assertThat(exception.getMessage()).isEqualTo("Tool not found: " + SystemStatusTool.TOOL_NAME);
    }
  }

  @Nested
  class WithSonarQubeServer {
    private final MockWebServer mockServer = new MockWebServer();

    @BeforeEach
    void setup() {
      mockServer.start();
    }

    @AfterEach
    void teardown() {
      mockServer.stop();
    }

    @SonarQubeMcpServerTest
    void it_should_return_status_without_authentication(SonarQubeMcpServerTestHarness harness) {
      mockServer.stubFor(get(SystemApi.STATUS_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateUpStatusPayload().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_URL", mockServer.baseUrl()
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        SystemStatusTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          SonarQube Server System Status
          =======================

          Status: UP
          Description: SonarQube Server instance is up and running

          ID: 20150504120436
          Version: 5.1""", false));
      assertThat(mockServer.getReceivedRequests())
        .containsExactly(new ReceivedRequest(null, ""));
    }

    @SonarQubeMcpServerTest
    void it_should_show_error_when_request_fails(SonarQubeMcpServerTestHarness harness) {
      mockServer.stubFor(get(SystemApi.STATUS_PATH).willReturn(aResponse().withStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_URL", mockServer.baseUrl(),
        "SONARQUBE_TOKEN", "token"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        SystemStatusTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Error 500 on " + mockServer.baseUrl() + "/api" +
          "/system/status", true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_up_status(SonarQubeMcpServerTestHarness harness) {
      mockServer.stubFor(get(SystemApi.STATUS_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateUpStatusPayload().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_URL", mockServer.baseUrl(),
        "SONARQUBE_TOKEN", "token"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        SystemStatusTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          SonarQube Server System Status
          =======================

          Status: UP
          Description: SonarQube Server instance is up and running

          ID: 20150504120436
          Version: 5.1""", false));
      assertThat(mockServer.getReceivedRequests())
        .containsExactly(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_starting_status(SonarQubeMcpServerTestHarness harness) {
      mockServer.stubFor(get(SystemApi.STATUS_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateStartingStatusPayload().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_URL", mockServer.baseUrl(),
        "SONARQUBE_TOKEN", "token"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        SystemStatusTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          SonarQube Server System Status
          =======================

          Status: STARTING
          Description: SonarQube Server Web Server is up and serving some Web Services but initialization is still ongoing

          ID: 20150504120436
          Version: 5.1""", false));
      assertThat(mockServer.getReceivedRequests())
        .containsExactly(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_db_migration_needed_status(SonarQubeMcpServerTestHarness harness) {
      mockServer.stubFor(get(SystemApi.STATUS_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateDbMigrationNeededStatusPayload().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_URL", mockServer.baseUrl(),
        "SONARQUBE_TOKEN", "token"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        SystemStatusTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          SonarQube Server System Status
          =======================

          Status: DB_MIGRATION_NEEDED
          Description: Database migration is required

          ID: 20150504120436
          Version: 5.1""", false));
      assertThat(mockServer.getReceivedRequests())
        .containsExactly(new ReceivedRequest("Bearer token", ""));
    }
  }

  private static String generateUpStatusPayload() {
    return """
      {
        "id": "20150504120436",
        "version": "5.1",
        "status": "UP"
      }""";
  }

  private static String generateStartingStatusPayload() {
    return """
      {
        "id": "20150504120436",
        "version": "5.1",
        "status": "STARTING"
      }""";
  }

  private static String generateDbMigrationNeededStatusPayload() {
    return """
      {
        "id": "20150504120436",
        "version": "5.1",
        "status": "DB_MIGRATION_NEEDED"
      }""";
  }

}
