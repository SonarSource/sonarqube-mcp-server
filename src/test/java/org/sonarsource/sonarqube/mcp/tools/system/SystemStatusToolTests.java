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

class SystemStatusToolTests {

  @SonarQubeMcpServerTest
  void it_should_validate_output_schema_and_annotations(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient();

    var tool = mcpClient.listTools().stream().filter(t -> t.name().equals(SystemStatusTool.TOOL_NAME)).findFirst().orElseThrow();

    assertThat(tool.annotations()).isNotNull();
    assertThat(tool.annotations().readOnlyHint()).isTrue();
    assertThat(tool.annotations().openWorldHint()).isTrue();
    assertThat(tool.annotations().idempotentHint()).isFalse();
    assertThat(tool.annotations().destructiveHint()).isFalse();

    assertSchemaEquals(tool.outputSchema(), """
      {
         "type":"object",
         "properties":{
            "description":{
               "type":"string",
               "description":"Human-readable description of the status"
            },
            "id":{
               "type":"string",
               "description":"Unique system identifier"
            },
            "status":{
               "type":"string",
               "description":"System status (UP, DOWN, etc.)"
            },
            "version":{
               "type":"string",
               "description":"SonarQube version"
            }
         },
         "required":[
            "description",
            "id",
            "status",
            "version"
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

      var exception = assertThrows(io.modelcontextprotocol.spec.McpError.class, () -> {
        mcpClient.callTool(SystemStatusTool.TOOL_NAME);
      });

      assertThat(exception.getMessage()).isEqualTo("Unknown tool: invalid_tool_name");
    }
  }

  @Nested
  class WithSonarQubeServer {

    @SonarQubeMcpServerTest
    void it_should_return_status_without_authentication(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();
      harness.getMockSonarQubeServer().stubFor(get(SystemApi.STATUS_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateUpStatusPayload().getBytes(StandardCharsets.UTF_8)))));

      var result = mcpClient.callTool(SystemStatusTool.TOOL_NAME);

      assertResultEquals(result, """
        {
          "status" : "UP",
          "description" : "SonarQube Server instance is up and running",
          "id" : "20150504120436",
          "version" : "5.1"
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest(null, ""));
    }

    @SonarQubeMcpServerTest
    void it_should_show_error_when_request_fails(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();
      harness.getMockSonarQubeServer().stubFor(get(SystemApi.STATUS_PATH).willReturn(aResponse().withStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR)));

      var result = mcpClient.callTool(SystemStatusTool.TOOL_NAME);

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true).addTextContent("An error occurred during the tool execution: SonarQube answered with Error 500 on " + harness.getMockSonarQubeServer().baseUrl() + "/api/system/status").build());
    }

    @SonarQubeMcpServerTest
    void it_should_return_up_status(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();
      harness.getMockSonarQubeServer().stubFor(get(SystemApi.STATUS_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateUpStatusPayload().getBytes(StandardCharsets.UTF_8)))));

      var result = mcpClient.callTool(SystemStatusTool.TOOL_NAME);

      assertResultEquals(result, """
        {
          "status" : "UP",
          "description" : "SonarQube Server instance is up and running",
          "id" : "20150504120436",
          "version" : "5.1"
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_starting_status(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();
      harness.getMockSonarQubeServer().stubFor(get(SystemApi.STATUS_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateStartingStatusPayload().getBytes(StandardCharsets.UTF_8)))));

      var result = mcpClient.callTool(SystemStatusTool.TOOL_NAME);

      assertResultEquals(result, """
        {
          "status" : "STARTING",
          "description" : "SonarQube Server Web Server is up and serving some Web Services but initialization is still ongoing",
          "id" : "20150504120436",
          "version" : "5.1"
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_db_migration_needed_status(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();
      harness.getMockSonarQubeServer().stubFor(get(SystemApi.STATUS_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateDbMigrationNeededStatusPayload().getBytes(StandardCharsets.UTF_8)))));

      var result = mcpClient.callTool(SystemStatusTool.TOOL_NAME);

      assertResultEquals(result, """
        {
          "status" : "DB_MIGRATION_NEEDED",
          "description" : "Database migration is required",
          "id" : "20150504120436",
          "version" : "5.1"
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
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
