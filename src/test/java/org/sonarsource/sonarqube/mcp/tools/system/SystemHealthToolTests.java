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

class SystemHealthToolTests {

  @Nested
  class MissingPrerequisite {
    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_sonarqube_token_is_missing(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of("SONARQUBE_URL", "fake.url"));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        SystemHealthTool.TOOL_NAME,
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
          SystemHealthTool.TOOL_NAME,
          Map.of()));
      });

      assertThat(exception.getMessage()).isEqualTo("Tool not found: " + SystemHealthTool.TOOL_NAME);
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
    void it_should_show_error_when_request_fails(SonarQubeMcpServerTestHarness harness) {
      mockServer.stubFor(get(SystemApi.HEALTH_PATH).willReturn(aResponse().withStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_URL", mockServer.baseUrl(),
        "SONARQUBE_TOKEN", "token"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        SystemHealthTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Error 500 on " + mockServer.baseUrl() + "/api" +
          "/system/health", true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_system_health_status_green(SonarQubeMcpServerTestHarness harness) {
      mockServer.stubFor(get(SystemApi.HEALTH_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateGreenHealthPayload().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_URL", mockServer.baseUrl(),
        "SONARQUBE_TOKEN", "token"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        SystemHealthTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("SonarQube Server Health Status: GREEN", false));
      assertThat(mockServer.getReceivedRequests())
        .containsExactly(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_system_health_status_red_with_details(SonarQubeMcpServerTestHarness harness) {
      mockServer.stubFor(get(SystemApi.HEALTH_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateRedHealthPayload().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_URL", mockServer.baseUrl(),
        "SONARQUBE_TOKEN", "token"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        SystemHealthTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          SonarQube Server Health Status: RED

          Causes:
          - Application node app-1 is RED

          Nodes:

          app-1 (APPLICATION) - RED
            Host: 192.168.1.1:999
            Started: 2015-08-13T23:34:59+0200
            Causes:
            - foo

          app-2 (APPLICATION) - YELLOW
            Host: [2001:db8:abcd:1234::1]:999
            Started: 2015-08-13T23:34:59+0200
            Causes:
            - bar""", false));
      assertThat(mockServer.getReceivedRequests())
        .containsExactly(new ReceivedRequest("Bearer token", ""));
    }
  }

  private static String generateGreenHealthPayload() {
    return """
      {
        "health": "GREEN",
        "causes": []
      }""";
  }

  private static String generateRedHealthPayload() {
    return """
      {
        "health": "RED",
        "causes": [
          {
            "message": "Application node app-1 is RED"
          }
        ],
        "nodes": [
          {
            "name": "app-1",
            "type": "APPLICATION",
            "host": "192.168.1.1",
            "port": 999,
            "startedAt": "2015-08-13T23:34:59+0200",
            "health": "RED",
            "causes": [
              {
                "message": "foo"
              }
            ]
          },
          {
            "name": "app-2",
            "type": "APPLICATION",
            "host": "[2001:db8:abcd:1234::1]",
            "port": 999,
            "startedAt": "2015-08-13T23:34:59+0200",
            "health": "YELLOW",
            "causes": [
              {
                "message": "bar"
              }
            ]
          }
        ]
      }""";
  }

} 
