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
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertSchemaEquals;

class SearchMyProjectsToolTests {

  @SonarQubeMcpServerTest
  void it_should_validate_output_schema_and_annotations(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient();

    var tool = mcpClient.listTools().stream().filter(t -> t.name().equals(SearchMyProjectsTool.TOOL_NAME)).findFirst().orElseThrow();

    assertThat(tool.annotations()).isNotNull();
    assertThat(tool.annotations().readOnlyHint()).isTrue();
    assertThat(tool.annotations().openWorldHint()).isTrue();
    assertThat(tool.annotations().idempotentHint()).isFalse();
    assertThat(tool.annotations().destructiveHint()).isFalse();

    assertSchemaEquals(tool.outputSchema(), """
      {
         "type":"object",
         "properties":{
            "paging":{
               "type":"object",
               "properties":{
                  "hasNextPage":{
                     "type":"boolean",
                     "description":"Whether there are more pages available"
                  },
                  "pageIndex":{
                     "type":"integer",
                     "description":"Current page index (1-based)"
                  },
                  "pageSize":{
                     "type":"integer",
                     "description":"Number of items per page"
                  },
                  "total":{
                     "type":"integer",
                     "description":"Total number of items across all pages"
                  }
               },
               "required":[
                  "hasNextPage",
                  "pageIndex",
                  "pageSize",
                  "total"
               ],
               "description":"Pagination information for the results"
            },
            "projects":{
               "description":"List of projects found in the organization",
               "type":"array",
               "items":{
                  "type":"object",
                  "properties":{
                     "key":{
                        "type":"string",
                        "description":"Unique project key"
                     },
                     "name":{
                        "type":"string",
                        "description":"Project display name"
                     }
                  },
                  "required":[
                     "key",
                     "name"
                  ]
               }
            }
         },
         "required":[
            "paging",
            "projects"
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

      var result = mcpClient.callTool(SearchMyProjectsTool.TOOL_NAME);

      assertThat(result.isError()).isTrue();
      var content = result.content().getFirst().toString();
      assertThat(content).contains("An error occurred during the tool execution:");
      assertThat(content).contains("Please verify your token is valid and the requested resource exists.");
    }

    @SonarQubeMcpServerTest
    void it_should_show_error_when_failing(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer()
        .stubFor(get(ComponentsApi.COMPONENTS_SEARCH_PATH + "?p=1&ps=500&organization=org").willReturn(aResponse().withStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(SearchMyProjectsTool.TOOL_NAME);

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder()
          .isError(true)
          .addTextContent("An error occurred during the tool execution: SonarQube answered with Error 500 on " + harness.getMockSonarQubeServer().baseUrl()
          + "/api/components/search?p=1&ps=500&organization=org")
          .build());
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_project_list_when_no_page_is_provided(SonarQubeMcpServerTestHarness harness) {
      var projectKey = "project-key";
      var projectName = "Project Name";
      harness.getMockSonarQubeServer().stubFor(get(ComponentsApi.COMPONENTS_SEARCH_PATH + "?p=1&ps=500&organization=org")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateResponse(projectKey, projectName, 1, 500, 1000).getBytes(StandardCharsets.UTF_8)))));
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
            "pageSize" : 500,
            "total" : 1000,
            "hasNextPage" : true
          }
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests()).contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_project_list_when_page_is_provided(SonarQubeMcpServerTestHarness harness) {
      var projectKey = "project-key";
      var projectName = "Project Name";
      harness.getMockSonarQubeServer().stubFor(get(ComponentsApi.COMPONENTS_SEARCH_PATH + "?p=2&ps=500&organization=org")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateResponse(projectKey, projectName, 2, 500, 1000).getBytes(StandardCharsets.UTF_8)))));
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
            "pageSize" : 500,
            "total" : 1000,
            "hasNextPage" : false
          }
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests()).contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_filter_projects_by_search_query(SonarQubeMcpServerTestHarness harness) {
      var projectKey = "sonar-project";
      var projectName = "Sonar Project";
      harness.getMockSonarQubeServer().stubFor(get(ComponentsApi.COMPONENTS_SEARCH_PATH + "?p=1&ps=500&q=sonar&organization=org")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateResponse(projectKey, projectName, 1, 500, 50).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        SearchMyProjectsTool.TOOL_NAME,
        Map.of("q", "sonar"));

      assertResultEquals(result, """
        {
          "projects" : [ {
            "key" : "sonar-project",
            "name" : "Sonar Project"
          } ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 500,
            "total" : 50,
            "hasNextPage" : false
          }
        }""");
    }

    @SonarQubeMcpServerTest
    void it_should_use_custom_page_size(SonarQubeMcpServerTestHarness harness) {
      var projectKey = "project-key";
      var projectName = "Project Name";
      harness.getMockSonarQubeServer().stubFor(get(ComponentsApi.COMPONENTS_SEARCH_PATH + "?p=1&ps=50&organization=org")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateResponse(projectKey, projectName, 1, 50, 200).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        SearchMyProjectsTool.TOOL_NAME,
        Map.of("pageSize", "50"));

      assertResultEquals(result, """
        {
          "projects" : [ {
            "key" : "project-key",
            "name" : "Project Name"
          } ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 50,
            "total" : 200,
            "hasNextPage" : true
          }
        }""");
    }

    @SonarQubeMcpServerTest
    void it_should_return_error_when_page_size_is_invalid(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        SearchMyProjectsTool.TOOL_NAME,
        Map.of("pageSize", "600"));

      assertThat(result.isError()).isTrue();
      assertThat(result.content().getFirst().toString()).contains("Page size must be greater than 0 and less than or equal to 500");
    }

    @SonarQubeMcpServerTest
    void it_should_combine_search_query_and_pagination(SonarQubeMcpServerTestHarness harness) {
      var projectKey = "sonar-test";
      var projectName = "Sonar Test";
      harness.getMockSonarQubeServer().stubFor(get(ComponentsApi.COMPONENTS_SEARCH_PATH + "?p=2&ps=100&q=test&organization=org")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateResponse(projectKey, projectName, 2, 100, 250).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        SearchMyProjectsTool.TOOL_NAME,
        Map.of("page", "2", "pageSize", "100", "q", "test"));

      assertResultEquals(result, """
        {
          "projects" : [ {
            "key" : "sonar-test",
            "name" : "Sonar Test"
          } ],
          "paging" : {
            "pageIndex" : 2,
            "pageSize" : 100,
            "total" : 250,
            "hasNextPage" : true
          }
        }""");
    }
  }

  @Nested
  class WithSonarQubeServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(ComponentsApi.COMPONENTS_SEARCH_PATH + "?p=1&ps=500&qualifiers=TRK").willReturn(aResponse().withStatus(HttpStatus.SC_FORBIDDEN)));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(SearchMyProjectsTool.TOOL_NAME);

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true).addTextContent("An error occurred during the tool execution: SonarQube answered with Forbidden. Please verify your token has the required permissions for this operation.").build());
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_project_list_when_no_page_is_provided(SonarQubeMcpServerTestHarness harness) {
      var projectKey = "project-key";
      var projectName = "Project Name";
      harness.getMockSonarQubeServer().stubFor(get(ComponentsApi.COMPONENTS_SEARCH_PATH + "?p=1&ps=500&qualifiers=TRK")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateResponse(projectKey, projectName, 1, 500, 1000).getBytes(StandardCharsets.UTF_8)))));
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
            "pageSize" : 500,
            "total" : 1000,
            "hasNextPage" : true
          }
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests()).contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_project_list_when_page_is_provided(SonarQubeMcpServerTestHarness harness) {
      var projectKey = "project-key";
      var projectName = "Project Name";
      harness.getMockSonarQubeServer().stubFor(get(ComponentsApi.COMPONENTS_SEARCH_PATH + "?p=2&ps=500&qualifiers=TRK")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateResponse(projectKey, projectName, 2, 500, 1000).getBytes(StandardCharsets.UTF_8)))));
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
            "pageSize" : 500,
            "total" : 1000,
            "hasNextPage" : false
          }
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests()).contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_filter_projects_by_search_query(SonarQubeMcpServerTestHarness harness) {
      var projectKey = "sonar-project";
      var projectName = "Sonar Project";
      harness.getMockSonarQubeServer().stubFor(get(ComponentsApi.COMPONENTS_SEARCH_PATH + "?p=1&ps=500&q=sonar&qualifiers=TRK")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateResponse(projectKey, projectName, 1, 500, 50).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        SearchMyProjectsTool.TOOL_NAME,
        Map.of("q", "sonar"));

      assertResultEquals(result, """
        {
          "projects" : [ {
            "key" : "sonar-project",
            "name" : "Sonar Project"
          } ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 500,
            "total" : 50,
            "hasNextPage" : false
          }
        }""");
    }

    @SonarQubeMcpServerTest
    void it_should_use_custom_page_size(SonarQubeMcpServerTestHarness harness) {
      var projectKey = "project-key";
      var projectName = "Project Name";
      harness.getMockSonarQubeServer().stubFor(get(ComponentsApi.COMPONENTS_SEARCH_PATH + "?p=1&ps=50&qualifiers=TRK")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateResponse(projectKey, projectName, 1, 50, 200).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        SearchMyProjectsTool.TOOL_NAME,
        Map.of("pageSize", "50"));

      assertResultEquals(result, """
        {
          "projects" : [ {
            "key" : "project-key",
            "name" : "Project Name"
          } ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 50,
            "total" : 200,
            "hasNextPage" : true
          }
        }""");
    }

    @SonarQubeMcpServerTest
    void it_should_combine_search_query_and_pagination(SonarQubeMcpServerTestHarness harness) {
      var projectKey = "sonar-test";
      var projectName = "Sonar Test";
      harness.getMockSonarQubeServer().stubFor(get(ComponentsApi.COMPONENTS_SEARCH_PATH + "?p=2&ps=100&q=test&qualifiers=TRK")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateResponse(projectKey, projectName, 2, 100, 250).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        SearchMyProjectsTool.TOOL_NAME,
        Map.of("page", "2", "pageSize", "100", "q", "test"));

      assertResultEquals(result, """
        {
          "projects" : [ {
            "key" : "sonar-test",
            "name" : "Sonar Test"
          } ],
          "paging" : {
            "pageIndex" : 2,
            "pageSize" : 100,
            "total" : 250,
            "hasNextPage" : true
          }
        }""");
    }
  }

  private static String generateResponse(String projectKey, String projectName, int pageIndex, int pageSize, int totalItems) {
    return """
      {
         "paging": {
           "pageIndex": %s,
           "pageSize": %s,
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
      """.formatted(pageIndex, pageSize, totalItems, projectKey, projectName);
  }

}
