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
package org.sonarsource.sonarqube.mcp.tools.projects;

import io.modelcontextprotocol.spec.McpSchema;
import com.github.tomakehurst.wiremock.http.Body;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Nested;
import org.sonarsource.sonarqube.mcp.harness.ReceivedRequest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.components.ComponentsApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;

class SearchMyProjectsToolTests {

  @Nested
  class WithSonarCloudServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(SearchMyProjectsTool.TOOL_NAME);

      assertThat(result.isError()).isTrue();
      var content = result.content().getFirst().toString();
      assertThat(content).contains("An error occurred during the tool execution:");
      assertThat(content).contains("Please verify your token is valid and the requested resource exists.");
    }

    @SonarQubeMcpServerTest
    void it_should_show_error_when_failing(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer()
        .stubFor(get(ComponentsApi.COMPONENTS_SEARCH_PATH + "?p=1&organization=org").willReturn(aResponse().withStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(SearchMyProjectsTool.TOOL_NAME);

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Error 500 on " + harness.getMockSonarQubeServer().baseUrl()
          + "/api/components/search?p=1&organization=org", true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_project_list_when_no_page_is_provided(SonarQubeMcpServerTestHarness harness) {
      var projectKey = "project-key";
      var projectName = "Project Name";
      harness.getMockSonarQubeServer().stubFor(get(ComponentsApi.COMPONENTS_SEARCH_PATH + "?p=1&organization=org")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateResponse(projectKey, projectName, 1, 400).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(SearchMyProjectsTool.TOOL_NAME);

      assertResultEquals(result, """
        {
          "projects" : [ {
            "key" : "project-key",
            "name" : "Project Name"
          } ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 100,
            "total" : 400
          }
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests()).contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_project_list_when_page_is_provided(SonarQubeMcpServerTestHarness harness) {
      var projectKey = "project-key";
      var projectName = "Project Name";
      harness.getMockSonarQubeServer().stubFor(get(ComponentsApi.COMPONENTS_SEARCH_PATH + "?p=2&organization=org")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateResponse(projectKey, projectName, 2, 200).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        SearchMyProjectsTool.TOOL_NAME,
        Map.of("page", "2"));

      assertResultEquals(result, """
        {
          "projects" : [ {
            "key" : "project-key",
            "name" : "Project Name"
          } ],
          "paging" : {
            "pageIndex" : 2,
            "pageSize" : 100,
            "total" : 200
          }
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests()).contains(new ReceivedRequest("Bearer token", ""));
    }
  }

  @Nested
  class WithSonarQubeServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(ComponentsApi.COMPONENTS_SEARCH_PATH + "?p=1&qualifiers=TRK").willReturn(aResponse().withStatus(HttpStatus.SC_FORBIDDEN)));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(SearchMyProjectsTool.TOOL_NAME);

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Forbidden. Please verify your token has the required permissions for this operation.", true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_project_list_when_no_page_is_provided(SonarQubeMcpServerTestHarness harness) {
      var projectKey = "project-key";
      var projectName = "Project Name";
      harness.getMockSonarQubeServer().stubFor(get(ComponentsApi.COMPONENTS_SEARCH_PATH + "?p=1&qualifiers=TRK")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateResponse(projectKey, projectName, 1, 400).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(SearchMyProjectsTool.TOOL_NAME);

      assertResultEquals(result, """
        {
          "projects" : [ {
            "key" : "project-key",
            "name" : "Project Name"
          } ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 100,
            "total" : 400
          }
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests()).contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_project_list_when_page_is_provided(SonarQubeMcpServerTestHarness harness) {
      var projectKey = "project-key";
      var projectName = "Project Name";
      harness.getMockSonarQubeServer().stubFor(get(ComponentsApi.COMPONENTS_SEARCH_PATH + "?p=2&qualifiers=TRK")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateResponse(projectKey, projectName, 2, 200).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        SearchMyProjectsTool.TOOL_NAME,
        Map.of("page", "2"));

      assertResultEquals(result, """
        {
          "projects" : [ {
            "key" : "project-key",
            "name" : "Project Name"
          } ],
          "paging" : {
            "pageIndex" : 2,
            "pageSize" : 100,
            "total" : 200
          }
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests()).contains(new ReceivedRequest("Bearer token", ""));
    }
  }

  private static String generateResponse(String projectKey, String projectName, int pageIndex, int totalItems) {
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
      """.formatted(pageIndex, totalItems, projectKey, projectName);
  }

}
