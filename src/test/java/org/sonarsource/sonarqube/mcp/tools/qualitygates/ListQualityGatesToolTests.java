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
import org.junit.jupiter.api.Nested;
import org.sonarsource.sonarqube.mcp.harness.ReceivedRequest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.qualitygates.QualityGatesApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;

class ListQualityGatesToolTests {

  @Nested
  class WithSonarCloudServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(ListQualityGatesTool.TOOL_NAME);

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: Make sure your token is valid.", true));
    }

    @SonarQubeMcpServerTest
    void it_should_show_error_when_request_fails(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(QualityGatesApi.LIST_PATH + "?organization=org").willReturn(aResponse().withStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(ListQualityGatesTool.TOOL_NAME);

      assertThat(result)
        .isEqualTo(
          new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Error 500 on " + harness.getMockSonarQubeServer().baseUrl() + "/api" +
            "/qualitygates/list?organization=org", true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_quality_gates_list(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(QualityGatesApi.LIST_PATH + "?organization=org")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generatePayload().getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(ListQualityGatesTool.TOOL_NAME);

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          Quality Gates:
          
          Sonar way [Default] [Built-in] (ID: 8)
          Conditions:
          - blocker_violations GT 0
          - tests LT 10
          
          Sonar way - Without Coverage (ID: 9)
          No conditions""", false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_quality_gates_list_for_sonacloud_format(SonarQubeMcpServerTestHarness harness) {
      String cloudPayload = """
        {
          "qualitygates": [
            {
              "name": "Sonar way",
              "isDefault": true,
              "isBuiltIn": true,
              "actions": {
                "rename": false,
                "setAsDefault": false,
                "copy": true,
                "associateProjects": false,
                "delete": false,
                "manageConditions": false,
                "delegate": false,
                "manageAiCodeAssurance": false
              },
              "caycStatus": "compliant",
              "hasStandardConditions": false,
              "hasMQRConditions": false,
              "isAiCodeSupported": false
            },
            {
              "name": "Sonar way - Without Coverage",
              "isDefault": false,
              "isBuiltIn": false,
              "actions": {
                "rename": true,
                "setAsDefault": true,
                "copy": true,
                "associateProjects": true,
                "delete": true,
                "manageConditions": true,
                "delegate": true,
                "manageAiCodeAssurance": true
              },
              "caycStatus": "non-compliant",
              "hasStandardConditions": false,
              "hasMQRConditions": false,
              "isAiCodeSupported": false
            }
          ],
          "actions": {
            "create": true
          }
        }
      """;
      harness.getMockSonarQubeServer().stubFor(get(QualityGatesApi.LIST_PATH + "?organization=org")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(cloudPayload.getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(ListQualityGatesTool.TOOL_NAME);

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          Quality Gates:

          Sonar way [Default] [Built-in]
          Status: compliant
          Standard Conditions: false
          MQR Conditions: false
          AI Code Supported: false

          Sonar way - Without Coverage
          Status: non-compliant
          Standard Conditions: false
          MQR Conditions: false
          AI Code Supported: false""", false));
    }
  }

  @Nested
  class WithSonarQubeServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(QualityGatesApi.LIST_PATH).willReturn(aResponse().withStatus(HttpStatus.SC_FORBIDDEN)));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(ListQualityGatesTool.TOOL_NAME);

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Forbidden", true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_quality_gates_list(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(QualityGatesApi.LIST_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generatePayload().getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(ListQualityGatesTool.TOOL_NAME);

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          Quality Gates:
          
          Sonar way [Default] [Built-in] (ID: 8)
          Conditions:
          - blocker_violations GT 0
          - tests LT 10
          
          Sonar way - Without Coverage (ID: 9)
          No conditions""", false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
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
