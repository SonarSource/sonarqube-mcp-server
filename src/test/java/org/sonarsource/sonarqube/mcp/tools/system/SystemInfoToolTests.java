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

class SystemInfoToolTests {

  @Nested
  class MissingPrerequisite {
    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_sonarqube_token_is_missing(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of("SONARQUBE_URL", "fake.url"));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        SystemInfoTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Not connected to SonarQube Server, please provide valid credentials", true));
    }
  }

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
          SystemInfoTool.TOOL_NAME,
          Map.of()));
      });

      assertThat(exception.getMessage()).isEqualTo("Tool not found: " + SystemInfoTool.TOOL_NAME);
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
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      mockServer.stubFor(get(SystemApi.INFO_PATH).willReturn(aResponse().withStatus(HttpStatus.SC_FORBIDDEN)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_URL", mockServer.baseUrl(),
        "SONARQUBE_TOKEN", "token"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        SystemInfoTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Forbidden", true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_system_info(SonarQubeMcpServerTestHarness harness) {
      mockServer.stubFor(get(SystemApi.INFO_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateSystemInfoPayload().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_URL", mockServer.baseUrl(),
        "SONARQUBE_TOKEN", "token"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        SystemInfoTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          SonarQube Server System Information
          ===========================

          Health: GREEN

          System
          ------
          - Server ID: AAAAAAAA-BBBBBBBBBBBBBBBBBBBB
          - Version: 9.8
          - Edition: Enterprise

          Database
          --------
          - Database: PostgreSQL
          - Database Version: 12.10 (Debian 12.10-1.pgdg110+1)
          - Username: username

          Settings
          --------
          Total settings: 2
          (Use SonarQube Server Web UI to view detailed settings)""", false));
      assertThat(mockServer.getReceivedRequests())
        .containsExactly(new ReceivedRequest("Bearer token", ""));
    }
  }

  private static String generateSystemInfoPayload() {
    return """
      {
        "Health": "GREEN",
        "Health Causes": [],
        "System": {
          "Server ID": "AAAAAAAA-BBBBBBBBBBBBBBBBBBBB",
          "Version": "9.8",
          "Edition": "Enterprise"
        },
        "Database": {
          "Database": "PostgreSQL",
          "Database Version": "12.10 (Debian 12.10-1.pgdg110+1)",
          "Username": "username"
        },
        "Settings": {
          "sonar.core.id": "AAAAAAAA-BBBBBBBBBBBBBBBBBBBB",
          "sonar.forceAuthentication": "false"
        }
      }""";
  }

} 
