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

import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
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

class SystemLogsToolTests {

  @SonarQubeMcpServerTest
  void it_should_validate_output_schema_and_annotations(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient();

    var tool = mcpClient.listTools().stream().filter(t -> t.name().equals(SystemLogsTool.TOOL_NAME)).findFirst().orElseThrow();

    assertThat(tool.annotations()).isNotNull();
    assertThat(tool.annotations().readOnlyHint()).isTrue();
    assertThat(tool.annotations().openWorldHint()).isTrue();
    assertThat(tool.annotations().idempotentHint()).isFalse();
    assertThat(tool.annotations().destructiveHint()).isFalse();

    assertSchemaEquals(tool.outputSchema(), """
      {
         "type":"object",
         "properties":{
            "content":{
               "type":"string",
               "description":"The log content"
            },
            "logType":{
               "type":"string",
               "description":"The type of logs retrieved"
            }
         },
         "required":[
            "content",
            "logType"
         ]
      }
      """);
  }

  @Nested
  class WithSonarCloudServer {

    @SonarQubeMcpServerTest
    void it_should_not_be_available_for_sonarcloud(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_URL", harness.getMockSonarQubeServer().baseUrl(),
        "SONARQUBE_ORG", "org"));

      var exception = assertThrows(McpError.class, () -> mcpClient.callTool(SystemLogsTool.TOOL_NAME));

      assertThat(exception.getMessage()).isEqualTo("Unknown tool: invalid_tool_name");
    }
  }

  @Nested
  class WithSonarQubeServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(SystemApi.LOGS_PATH).willReturn(aResponse().withStatus(HttpStatus.SC_FORBIDDEN)));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(SystemLogsTool.TOOL_NAME);

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true).addTextContent("An error occurred during the tool execution: SonarQube answered with Forbidden. Please verify your token has the required permissions for this operation.").build());
    }

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_property_is_invalid(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        SystemLogsTool.TOOL_NAME,
        Map.of(SystemLogsTool.NAME_PROPERTY, "foo"));

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true).addTextContent("Invalid log name. Possible values: access, app, ce, deprecation, es, web").build());
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_system_logs(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(SystemApi.LOGS_PATH)
        .willReturn(aResponse().withBody(generateAppLogsPayload())));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(SystemLogsTool.TOOL_NAME);

      assertResultEquals(result, """
        {
          "logType" : "app",
          "content" : "2023-01-01 10:00:01 INFO  o.s.s.a.WebServer Starting SonarQube Web Server\\n2023-01-01 10:00:02 INFO  o.s.s.p.ProcessEntryPoint Process[web] is up"
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }
  }

  private static String generateAppLogsPayload() {
    return """
      2023-01-01 10:00:01 INFO  o.s.s.a.WebServer Starting SonarQube Web Server
      2023-01-01 10:00:02 INFO  o.s.s.p.ProcessEntryPoint Process[web] is up""";
  }

}
