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
package org.sonarsource.sonarqube.mcp.tools.enterprises;

import io.modelcontextprotocol.spec.McpSchema;
import com.github.tomakehurst.wiremock.http.Body;
import io.modelcontextprotocol.spec.McpError;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Nested;
import org.sonarsource.sonarqube.mcp.harness.ReceivedRequest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.enterprises.EnterprisesApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;

class ListEnterprisesToolTests {

  @Nested
  class WithSonarQubeServer {

    @SonarQubeMcpServerTest
    void it_should_not_be_available_for_sonarqube_server(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();

      var exception = assertThrows(McpError.class, () -> mcpClient.callTool(ListEnterprisesTool.TOOL_NAME));

      assertThat(exception.getMessage()).isEqualTo("Unknown tool: invalid_tool_name");
    }
  }

  @Nested
  class WithSonarCloudServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(EnterprisesApi.ENTERPRISES_PATH).willReturn(aResponse().withStatus(HttpStatus.SC_FORBIDDEN)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org",
        "SONARQUBE_CLOUD_URL", harness.getMockSonarQubeServer().baseUrl()));

      var result = mcpClient.callTool(ListEnterprisesTool.TOOL_NAME);

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Forbidden", true));
    }

    @SonarQubeMcpServerTest
    void it_should_show_error_when_request_fails(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(EnterprisesApi.ENTERPRISES_PATH).willReturn(aResponse().withStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org",
        "SONARQUBE_CLOUD_URL", harness.getMockSonarQubeServer().baseUrl()));

      var result = mcpClient.callTool(ListEnterprisesTool.TOOL_NAME);

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Error 500 on " + harness.getMockSonarQubeServer().baseUrl() + "/enterprises/enterprises", true));
      assertThat(result).isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Error 500 on " + harness.getMockSonarQubeServer().baseUrl() + "/enterprises/enterprises", true));
      assertThat(result).isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Error 500 on " + harness.getMockSonarQubeServer().baseUrl() + "/enterprises/enterprises", true));
      assertThat(result).isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Error 500 on " + harness.getMockSonarQubeServer().baseUrl() + "/enterprises/enterprises", true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_empty_message_when_no_enterprises(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(EnterprisesApi.ENTERPRISES_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("[]".getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org",
        "SONARQUBE_CLOUD_URL", harness.getMockSonarQubeServer().baseUrl()));

      var result = mcpClient.callTool(ListEnterprisesTool.TOOL_NAME);

      assertResultEquals(result, """
        {
          "enterprises" : [ ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_enterprises_list(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(EnterprisesApi.ENTERPRISES_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateEnterprisesResponse().getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org",
        "SONARQUBE_CLOUD_URL", harness.getMockSonarQubeServer().baseUrl()));

      var result = mcpClient.callTool(ListEnterprisesTool.TOOL_NAME);

      assertResultEquals(result, """
        {
          "enterprises" : [ {
            "id" : "bbc185c3-3830-44df-b5f3-f2cf2f55868f",
            "key" : "some-enterprise-key",
            "name" : "Some Enterprise Name",
            "avatar" : "https://gravatar.com/avatar/aca8d9fc74ff8c807018adfffdc90996",
            "defaultPortfolioPermissionTemplateId" : "bbc185c3-3830-44df-b5f3-f2cf2f55868f"
          }, {
            "id" : "def456gh-7890-1234-ijkl-567890123456",
            "key" : "another-key",
            "name" : "Another Enterprise"
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_filter_enterprises_by_key(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(EnterprisesApi.ENTERPRISES_PATH + "?enterpriseKey=my-enterprise")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateFilteredEnterprisesResponse().getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org",
        "SONARQUBE_CLOUD_URL", harness.getMockSonarQubeServer().baseUrl()));

      var result = mcpClient.callTool(
        ListEnterprisesTool.TOOL_NAME,
        Map.of(ListEnterprisesTool.ENTERPRISE_KEY_PROPERTY, "my-enterprise"));

      assertResultEquals(result, """
        {
          "enterprises" : [ {
            "id" : "abc123de-4567-8901-fghi-234567890123",
            "key" : "my-enterprise",
            "name" : "My Filtered Enterprise"
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }
  }

  private static String generateEnterprisesResponse() {
    return """
      [
        {
          "id": "bbc185c3-3830-44df-b5f3-f2cf2f55868f",
          "key": "some-enterprise-key",
          "name": "Some Enterprise Name",
          "avatar": "https://gravatar.com/avatar/aca8d9fc74ff8c807018adfffdc90996",
          "defaultPortfolioPermissionTemplateId": "bbc185c3-3830-44df-b5f3-f2cf2f55868f"
        },
        {
          "id": "def456gh-7890-1234-ijkl-567890123456",
          "key": "another-key",
          "name": "Another Enterprise",
          "avatar": null,
          "defaultPortfolioPermissionTemplateId": null
        }
      ]""";
  }

  private static String generateFilteredEnterprisesResponse() {
    return """
      [
        {
          "id": "abc123de-4567-8901-fghi-234567890123",
          "key": "my-enterprise",
          "name": "My Filtered Enterprise",
          "avatar": null,
          "defaultPortfolioPermissionTemplateId": null
        }
      ]""";
  }
}
