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

class SystemInfoToolTests {

  @SonarQubeMcpServerTest
  void it_should_validate_output_schema(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient();

    var tool = mcpClient.listTools().stream().filter(t -> t.name().equals(SystemInfoTool.TOOL_NAME)).findFirst().orElseThrow();

    assertSchemaEquals(tool.outputSchema(), """
      {
         "type":"object",
         "properties":{
            "sections":{
               "description":"List of system sections with configuration and status information",
               "type":"array",
               "items":{
                  "type":"object",
                  "properties":{
                     "attributes":{
                        "type":"object",
                        "description":"Key-value pairs of system information"
                     },
                     "name":{
                        "type":"string",
                        "description":"Section name"
                     }
                  },
                  "required":[
                     "attributes",
                     "name"
                  ]
               }
            }
         },
         "required":[
            "sections"
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

      var exception = assertThrows(io.modelcontextprotocol.spec.McpError.class, () -> mcpClient.callTool(SystemInfoTool.TOOL_NAME));

      assertThat(exception.getMessage()).isEqualTo("Unknown tool: invalid_tool_name");
    }
  }

  @Nested
  class WithSonarQubeServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(SystemApi.INFO_PATH).willReturn(aResponse().withStatus(HttpStatus.SC_FORBIDDEN)));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(SystemInfoTool.TOOL_NAME);

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Forbidden. Please verify your token has the required permissions for this operation.", true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_system_info(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(SystemApi.INFO_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateSystemInfoPayload().getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(SystemInfoTool.TOOL_NAME);

      assertResultEquals(result, """
        {
          "sections" : [ {
            "name" : "System",
            "attributes" : {
              "Server ID" : "AAAAAAAA-BBBBBBBBBBBBBBBBBBBB",
              "Version" : "9.8",
              "Edition" : "Enterprise"
            }
          }, {
            "name" : "Database",
            "attributes" : {
              "Database" : "PostgreSQL",
              "Database Version" : "12.10 (Debian 12.10-1.pgdg110+1)",
              "Username" : "username"
            }
          }, {
            "name" : "Settings",
            "attributes" : {
              "sonar.core.id" : "AAAAAAAA-BBBBBBBBBBBBBBBBBBBB",
              "sonar.forceAuthentication" : "false"
            }
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
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
