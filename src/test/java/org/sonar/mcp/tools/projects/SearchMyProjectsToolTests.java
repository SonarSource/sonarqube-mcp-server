/*
 * Sonar MCP Server
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
package org.sonar.mcp.tools.projects;

import com.github.tomakehurst.wiremock.http.Body;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.sonar.mcp.harness.MockWebServer;
import org.sonar.mcp.harness.ReceivedRequest;
import org.sonar.mcp.harness.SonarMcpServerTest;
import org.sonar.mcp.harness.SonarMcpServerTestHarness;
import org.sonar.mcp.serverapi.components.ComponentsApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;

class SearchMyProjectsToolTests {

  @Nested
  class MissingPrerequisite {

    @SonarMcpServerTest
    void it_should_return_an_error_if_sonarqube_cloud_token_is_missing(SonarMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of("SONARQUBE_CLOUD_ORG", "org"));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        SearchMyProjectsTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Not connected to SonarQube Cloud, please provide 'SONARQUBE_CLOUD_TOKEN' and 'SONARQUBE_CLOUD_ORG'", true));
    }

    @SonarMcpServerTest
    void it_should_return_an_error_if_sonarqube_cloud_org_is_missing(SonarMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of("SONARQUBE_CLOUD_TOKEN", "token"));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        SearchMyProjectsTool.TOOL_NAME,
        Map.of("prefix", "proj")));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Not connected to SonarQube Cloud, please provide 'SONARQUBE_CLOUD_TOKEN' and 'SONARQUBE_CLOUD_ORG'", true));
    }
  }

  @Nested
  class WithServer {

    private final MockWebServer mockServer = new MockWebServer();

    @BeforeEach
    void setup() {
      mockServer.start();
    }

    @AfterEach
    void teardown() {
      mockServer.stop();
    }

    @SonarMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_CLOUD_URL", mockServer.baseUrl(),
        "SONARQUBE_CLOUD_TOKEN", "token",
        "SONARQUBE_CLOUD_ORG", "org"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        SearchMyProjectsTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Failed to fetch all projects: Make sure your token is valid.", true));
    }

    @SonarMcpServerTest
    void it_should_show_error_when_failing(SonarMcpServerTestHarness harness) {
      mockServer.stubFor(get(ComponentsApi.COMPONENTS_SEARCH_PATH + "?p=1&organization=org").willReturn(aResponse().withStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_CLOUD_URL", mockServer.baseUrl(),
        "SONARQUBE_CLOUD_TOKEN", "token",
        "SONARQUBE_CLOUD_ORG", "org"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        SearchMyProjectsTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Failed to fetch all projects: Error 500 on " + mockServer.baseUrl() + "/api/components/search?p=1&organization=org", true));
    }

    @SonarMcpServerTest
    void it_should_return_the_project_list_when_no_page_is_provided(SonarMcpServerTestHarness harness) {
      var projectKey = "project-key";
      var projectName = "Project Name";
      mockServer.stubFor(get(ComponentsApi.COMPONENTS_SEARCH_PATH + "?p=1&organization=org")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateResponse(projectKey, projectName, 1, 4).getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_CLOUD_URL", mockServer.baseUrl(),
        "SONARQUBE_CLOUD_TOKEN", "token",
        "SONARQUBE_CLOUD_ORG", "org"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        SearchMyProjectsTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          Found 1 Sonar projects in your organization.
          This response is paginated and this is the page 1 out of 4 total pages. There is a maximum of 100 projects per page.
          Project key: %s | Project name: %s
          """.formatted(projectKey, projectName), false));
      assertThat(mockServer.getReceivedRequests()).containsExactly(new ReceivedRequest("Bearer token", ""));
    }

    @SonarMcpServerTest
    void it_should_return_the_project_list_when_page_is_provided(SonarMcpServerTestHarness harness) {
      var projectKey = "project-key";
      var projectName = "Project Name";
      mockServer.stubFor(get(ComponentsApi.COMPONENTS_SEARCH_PATH + "?p=2&organization=org")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateResponse(projectKey, projectName, 2, 2).getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_CLOUD_URL", mockServer.baseUrl(),
        "SONARQUBE_CLOUD_TOKEN", "token",
        "SONARQUBE_CLOUD_ORG", "org"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        SearchMyProjectsTool.TOOL_NAME,
        Map.of("page", "2")));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          Found 1 Sonar projects in your organization.
          This response is paginated and this is the page 2 out of 2 total pages. There is a maximum of 100 projects per page.
          Project key: %s | Project name: %s
          """.formatted(projectKey, projectName), false));
      assertThat(mockServer.getReceivedRequests()).containsExactly(new ReceivedRequest("Bearer token", ""));
    }
  }

  private static String generateResponse(String projectKey, String projectName, int pageIndex, int totalPages) {
    return """
            {
               "paging": {
                 "pageIndex": %s,
                 "pageSize": 100,
                 "total": %s
               },
               "components": [
                 {
                   "organization": "my-org-1",
                   "key": "%s",
                   "qualifier": "TRK",
                   "name": "%s",
                   "project": "project-key"
                 }
               ]
             }
            """.formatted(pageIndex, totalPages, projectKey, projectName);
  }

}
