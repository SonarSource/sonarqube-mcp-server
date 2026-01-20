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
package org.sonarsource.sonarqube.mcp.tools.qualitygates;

import io.modelcontextprotocol.spec.McpSchema;
import com.github.tomakehurst.wiremock.http.Body;
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
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertSchemaEquals;

class ListQualityGatesToolTests {

  @SonarQubeMcpServerTest
  void it_should_validate_output_schema_and_annotations(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient();

    var tool = mcpClient.listTools().stream().filter(t -> t.name().equals(ListQualityGatesTool.TOOL_NAME)).findFirst().orElseThrow();

    assertThat(tool.annotations()).isNotNull();
    assertThat(tool.annotations().readOnlyHint()).isTrue();
    assertThat(tool.annotations().idempotentHint()).isFalse();
    assertThat(tool.annotations().openWorldHint()).isTrue();
    assertThat(tool.annotations().destructiveHint()).isFalse();

    assertSchemaEquals(tool.outputSchema(), """
      {
         "type":"object",
         "properties":{
            "qualityGates":{
               "description":"List of quality gates",
               "type":"array",
               "items":{
                  "type":"object",
                  "properties":{
                     "caycStatus":{
                        "type":"string",
                        "description":"Clean as You Code status"
                     },
                     "conditions":{
                        "description":"List of conditions",
                        "type":"array",
                        "items":{
                           "type":"object",
                           "properties":{
                              "error":{
                                 "type":"integer",
                                 "description":"Error threshold"
                              },
                              "metric":{
                                 "type":"string",
                                 "description":"Metric key"
                              },
                              "op":{
                                 "type":"string",
                                 "description":"Comparison operator"
                              }
                           },
                           "required":[
                              "error",
                              "metric",
                              "op"
                           ]
                        }
                     },
                     "hasMQRConditions":{
                        "type":"boolean",
                        "description":"Whether it has MQR conditions"
                     },
                     "hasStandardConditions":{
                        "type":"boolean",
                        "description":"Whether it has standard conditions"
                     },
                     "id":{
                        "type":"integer",
                        "description":"Quality gate ID"
                     },
                     "isAiCodeSupported":{
                        "type":"boolean",
                        "description":"Whether AI code is supported"
                     },
                     "isBuiltIn":{
                        "type":"boolean",
                        "description":"Whether this is a built-in quality gate"
                     },
                     "isDefault":{
                        "type":"boolean",
                        "description":"Whether this is the default quality gate"
                     },
                     "name":{
                        "type":"string",
                        "description":"Quality gate name"
                     }
                  },
                  "required":[
                     "isBuiltIn",
                     "isDefault",
                     "name"
                  ]
               }
            }
         },
         "required":[
            "qualityGates"
         ]
      }
      """);
  }

  @Nested
  class WithSonarCloudServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(ListQualityGatesTool.TOOL_NAME);

      assertThat(result.isError()).isTrue();
      var content = result.content().getFirst().toString();
      assertThat(content).contains("An error occurred during the tool execution:");
      assertThat(content).contains("Please verify your token is valid and the requested resource exists.");
    }

    @SonarQubeMcpServerTest
    void it_should_show_error_when_request_fails(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(QualityGatesApi.LIST_PATH + "?organization=org").willReturn(aResponse().withStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(ListQualityGatesTool.TOOL_NAME);

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true).addTextContent("An error occurred during the tool execution: SonarQube answered with Error 500 on " + harness.getMockSonarQubeServer().baseUrl() + "/api/qualitygates/list?organization=org").build());
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_quality_gates_list(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(QualityGatesApi.LIST_PATH + "?organization=org")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generatePayload().getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(ListQualityGatesTool.TOOL_NAME);

      assertResultEquals(result, """
        {
          "qualityGates" : [ {
            "id" : 8,
            "name" : "Sonar way",
            "isDefault" : true,
            "isBuiltIn" : true,
            "conditions" : [ {
              "metric" : "blocker_violations",
              "op" : "GT",
              "error" : 0
            }, {
              "metric" : "tests",
              "op" : "LT",
              "error" : 10
            } ]
          }, {
            "id" : 9,
            "name" : "Sonar way - Without Coverage",
            "isDefault" : false,
            "isBuiltIn" : false,
            "conditions" : [ ]
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_quality_gates_list_for_sonarcloud_format(SonarQubeMcpServerTestHarness harness) {
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

      assertResultEquals(result, """
        {
          "qualityGates" : [ {
            "name" : "Sonar way",
            "isDefault" : true,
            "isBuiltIn" : true,
            "caycStatus" : "compliant",
            "hasStandardConditions" : false,
            "hasMQRConditions" : false,
            "isAiCodeSupported" : false
          }, {
            "name" : "Sonar way - Without Coverage",
            "isDefault" : false,
            "isBuiltIn" : false,
            "caycStatus" : "non-compliant",
            "hasStandardConditions" : false,
            "hasMQRConditions" : false,
            "isAiCodeSupported" : false
          } ]
        }""");
    }
  }

  @Nested
  class WithSonarQubeServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(QualityGatesApi.LIST_PATH).willReturn(aResponse().withStatus(HttpStatus.SC_FORBIDDEN)));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(ListQualityGatesTool.TOOL_NAME);

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true).addTextContent("An error occurred during the tool execution: SonarQube answered with Forbidden. Please verify your token has the required permissions for this operation.").build());
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_quality_gates_list(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(QualityGatesApi.LIST_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generatePayload().getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(ListQualityGatesTool.TOOL_NAME);

      assertResultEquals(result, """
        {
          "qualityGates" : [ {
            "id" : 8,
            "name" : "Sonar way",
            "isDefault" : true,
            "isBuiltIn" : true,
            "conditions" : [ {
              "metric" : "blocker_violations",
              "op" : "GT",
              "error" : 0
            }, {
              "metric" : "tests",
              "op" : "LT",
              "error" : 10
            } ]
          }, {
            "id" : 9,
            "name" : "Sonar way - Without Coverage",
            "isDefault" : false,
            "isBuiltIn" : false,
            "conditions" : [ ]
          } ]
        }""");
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
