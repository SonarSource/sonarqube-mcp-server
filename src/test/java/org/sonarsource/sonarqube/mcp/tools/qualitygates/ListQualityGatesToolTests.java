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
package org.sonarsource.sonarqube.mcp.tools.qualitygates;

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
import org.sonarsource.sonarqube.mcp.serverapi.qualitygates.QualityGatesApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;

class ListQualityGatesToolTests {

  @Nested
  class MissingPrerequisite {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_sonarqube_token_is_missing(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        ListQualityGatesTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Not connected to SonarQube, please provide valid credentials", true));
    }

  }

  @Nested
  class WithSonarCloudServer {

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
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_URL", mockServer.baseUrl(),
        "SONARQUBE_TOKEN", "token",
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        ListQualityGatesTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: Make sure your token is valid.", true));
    }

    @SonarQubeMcpServerTest
    void it_should_show_error_when_request_fails(SonarQubeMcpServerTestHarness harness) {
      mockServer.stubFor(get(QualityGatesApi.LIST_PATH + "?organization=org").willReturn(aResponse().withStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_URL", mockServer.baseUrl(),
        "SONARQUBE_TOKEN", "token",
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        ListQualityGatesTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Error 500 on " + mockServer.baseUrl() + "/api" +
          "/qualitygates/list?organization=org", true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_quality_gates_list(SonarQubeMcpServerTestHarness harness) {
      mockServer.stubFor(get(QualityGatesApi.LIST_PATH + "?organization=org")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generatePayload().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_URL", mockServer.baseUrl(),
        "SONARQUBE_TOKEN", "token",
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        ListQualityGatesTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          Quality Gates:

          Sonar way (ID: 8) [Default] [Built-in]
          Conditions:
          - blocker_violations GT 0
          - tests LT 10

          Sonar way - Without Coverage (ID: 9)
          No conditions""", false));
      assertThat(mockServer.getReceivedRequests())
        .containsExactly(new ReceivedRequest("Bearer token", ""));
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
      mockServer.stubFor(get(QualityGatesApi.LIST_PATH).willReturn(aResponse().withStatus(HttpStatus.SC_FORBIDDEN)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_URL", mockServer.baseUrl(),
        "SONARQUBE_TOKEN", "token"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        ListQualityGatesTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Forbidden", true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_quality_gates_list(SonarQubeMcpServerTestHarness harness) {
      mockServer.stubFor(get(QualityGatesApi.LIST_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generatePayload().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_URL", mockServer.baseUrl(),
        "SONARQUBE_TOKEN", "token"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        ListQualityGatesTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          Quality Gates:

          Sonar way (ID: 8) [Default] [Built-in]
          Conditions:
          - blocker_violations GT 0
          - tests LT 10

          Sonar way - Without Coverage (ID: 9)
          No conditions""", false));
      assertThat(mockServer.getReceivedRequests())
        .containsExactly(new ReceivedRequest("Bearer token", ""));
    }
  }

  private static String generatePayload() {
    return """
      {
         "qualitygates": [
           {
             "id": 8,
             "name": "Sonar way",
             "isDefault": true,
             "isBuiltIn": true,
             "actions": {
               "rename": false,
               "setAsDefault": false,
               "copy": true,
               "associateProjects": false,
               "delete": false,
               "manageConditions": false
             },
             "conditions": [
               {
                 "id": 1,
                 "metric": "blocker_violations",
                 "op": "GT",
                 "error": "0"
               },
               {
                 "id": 2,
                 "metric": "tests",
                 "op": "LT",
                 "error": "10"
               }
             ]
           },
           {
             "id": 9,
             "name": "Sonar way - Without Coverage",
             "isDefault": false,
             "isBuiltIn": false,
             "actions": {
               "rename": true,
               "setAsDefault": true,
               "copy": true,
               "associateProjects": true,
               "delete": true,
               "manageConditions": true
             },
             "conditions": []
           }
         ],
         "default": 8,
         "actions": {
           "create": true
         }
       }""";
  }
} 
