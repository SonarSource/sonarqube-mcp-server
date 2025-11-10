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

import io.modelcontextprotocol.spec.McpSchema;
import com.github.tomakehurst.wiremock.http.Body;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Nested;
import org.sonarsource.sonarqube.mcp.harness.ReceivedRequest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.system.SystemApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertSchemaEquals;

class SystemHealthToolTests {

  @SonarQubeMcpServerTest
  void it_should_validate_output_schema(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient();

    var tool = mcpClient.listTools().stream().filter(t -> t.name().equals(SystemHealthTool.TOOL_NAME)).findFirst().orElseThrow();

    assertSchemaEquals(tool.outputSchema(), """
      {
         "$defs":{
            "Cause":{
               "type":"object",
               "properties":{
                  "message":{
                     "type":"string",
                     "description":"Description of the health issue"
                  }
               },
               "required":[
                  "message"
               ]
            }
         },
         "type":"object",
         "properties":{
            "causes":{
               "description":"List of health issues, if any",
               "type":"array",
               "items":{
                  "$ref":"#/$defs/Cause"
               }
            },
            "health":{
               "type":"string",
               "description":"Overall health status of the system"
            },
            "nodes":{
               "description":"List of cluster nodes with their health status",
               "type":"array",
               "items":{
                  "type":"object",
                  "properties":{
                     "causes":{
                        "description":"List of node-specific health issues",
                        "type":"array",
                        "items":{
                           "$ref":"#/$defs/Cause"
                        }
                     },
                     "health":{
                        "type":"string",
                        "description":"Health status of this node"
                     },
                     "host":{
                        "type":"string",
                        "description":"Host address"
                     },
                     "name":{
                        "type":"string",
                        "description":"Node name"
                     },
                     "port":{
                        "type":"integer",
                        "description":"Port number"
                     },
                     "startedAt":{
                        "type":"string",
                        "description":"Timestamp when the node started"
                     },
                     "type":{
                        "type":"string",
                        "description":"Node type (APPLICATION, SEARCH, etc.)"
                     }
                  },
                  "required":[
                     "health",
                     "host",
                     "name",
                     "port",
                     "startedAt",
                     "type"
                  ]
               }
            }
         },
         "required":[
            "health"
         ]
      }
      """);
  }

  @Nested
  class WithSonarCloudServer {

    @SonarQubeMcpServerTest
    void it_should_not_be_available_for_sonarcloud(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_CLOUD_URL", harness.getMockSonarQubeServer().baseUrl(),
        "SONARQUBE_ORG", "org"));

      var exception = assertThrows(io.modelcontextprotocol.spec.McpError.class, () -> mcpClient.callTool(SystemHealthTool.TOOL_NAME));

      assertThat(exception.getMessage()).isEqualTo("Unknown tool: invalid_tool_name");
    }

  }

  @Nested
  class WithSonarQubeServer {

    @SonarQubeMcpServerTest
    void it_should_show_error_when_request_fails(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(SystemApi.HEALTH_PATH).willReturn(aResponse().withStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR)));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(SystemHealthTool.TOOL_NAME);

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Error 500 on " + harness.getMockSonarQubeServer().baseUrl() + "/api/system/health", true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_system_health_status_green(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(SystemApi.HEALTH_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateGreenHealthPayload().getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(SystemHealthTool.TOOL_NAME);

      assertResultEquals(result, """
        {
          "health" : "GREEN"
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_system_health_status_red_with_details(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(SystemApi.HEALTH_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateRedHealthPayload().getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(SystemHealthTool.TOOL_NAME);

      assertResultEquals(result, """
        {
          "health" : "RED",
          "causes" : [ {
            "message" : "Application node app-1 is RED"
          } ],
          "nodes" : [ {
            "name" : "app-1",
            "type" : "APPLICATION",
            "host" : "192.168.1.1",
            "port" : 999,
            "startedAt" : "2015-08-13T23:34:59+0200",
            "health" : "RED",
            "causes" : [ {
              "message" : "foo"
            } ]
          }, {
            "name" : "app-2",
            "type" : "APPLICATION",
            "host" : "[2001:db8:abcd:1234::1]",
            "port" : 999,
            "startedAt" : "2015-08-13T23:34:59+0200",
            "health" : "YELLOW",
            "causes" : [ {
              "message" : "bar"
            } ]
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
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
