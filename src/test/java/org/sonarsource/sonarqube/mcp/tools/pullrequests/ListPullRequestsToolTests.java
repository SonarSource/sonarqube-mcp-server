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
package org.sonarsource.sonarqube.mcp.tools.pullrequests;

import io.modelcontextprotocol.spec.McpSchema;
import com.github.tomakehurst.wiremock.http.Body;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.harness.ReceivedRequest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.pullrequests.PullRequestsApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertSchemaEquals;

class ListPullRequestsToolTests {

  @SonarQubeMcpServerTest
  void it_should_validate_output_schema_and_annotations(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient();

    var tool = mcpClient.listTools().stream().filter(t -> t.name().equals(ListPullRequestsTool.TOOL_NAME)).findFirst().orElseThrow();

    assertThat(tool.annotations()).isNotNull();
    assertThat(tool.annotations().readOnlyHint()).isTrue();
    assertThat(tool.annotations().openWorldHint()).isTrue();
    assertThat(tool.annotations().idempotentHint()).isFalse();
    assertThat(tool.annotations().destructiveHint()).isFalse();

    assertSchemaEquals(tool.outputSchema(), """
      {
         "type":"object",
         "properties":{
            "projectKey":{
               "description":"Project key",
               "type":"string"
            },
            "totalPullRequests":{
               "description":"Total number of pull requests",
               "type":"integer"
            },
            "pullRequests":{
               "description":"List of pull requests for this project",
               "type":"array",
               "items":{
                  "type":"object",
                  "properties":{
                     "key":{
                        "type":"string",
                        "description":"Pull request key/ID"
                     },
                     "title":{
                        "type":"string",
                        "description":"Pull request title"
                     },
                     "branch":{
                        "type":"string",
                        "description":"Source branch name"
                     }
                  },
                  "required":[
                     "branch",
                     "key",
                     "title"
                  ]
               }
            }
         },
         "required":[
            "projectKey",
            "pullRequests",
            "totalPullRequests"
         ]
      }
      """);
  }

  @SonarQubeMcpServerTest
  void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get(PullRequestsApi.PULL_REQUESTS_LIST_PATH + "?project=my_project")
      .willReturn(aResponse().withStatus(403)));
    var mcpClient = harness.newClient();

    var result = mcpClient.callTool(ListPullRequestsTool.TOOL_NAME, Map.of(ListPullRequestsTool.PROJECT_KEY_PROPERTY, "my_project"));

    assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true).addTextContent("An error occurred during the tool execution: SonarQube answered with Forbidden. Please verify your token has the required permissions for this operation.").build());
  }

  @SonarQubeMcpServerTest
  void it_should_return_empty_message_when_no_pull_requests(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get(PullRequestsApi.PULL_REQUESTS_LIST_PATH + "?project=my_project")
      .willReturn(aResponse().withResponseBody(
        Body.fromJsonBytes(generateEmptyPullRequestsResponse().getBytes(StandardCharsets.UTF_8))
      )));
    var mcpClient = harness.newClient();

    var result = mcpClient.callTool(ListPullRequestsTool.TOOL_NAME, Map.of(ListPullRequestsTool.PROJECT_KEY_PROPERTY, "my_project"));

    assertResultEquals(result, """
      {
        "projectKey" : "my_project",
        "totalPullRequests" : 0,
        "pullRequests" : [ ]
      }""");
    assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
      .contains(new ReceivedRequest("Bearer token", ""));
  }

  @SonarQubeMcpServerTest
  void it_should_list_pull_requests(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get(PullRequestsApi.PULL_REQUESTS_LIST_PATH + "?project=my_project")
      .willReturn(aResponse().withResponseBody(
        Body.fromJsonBytes(generatePullRequestsResponse().getBytes(StandardCharsets.UTF_8))
      )));
    var mcpClient = harness.newClient();

    var result = mcpClient.callTool(ListPullRequestsTool.TOOL_NAME, Map.of(ListPullRequestsTool.PROJECT_KEY_PROPERTY, "my_project"));

    assertResultEquals(result, """
      {
        "projectKey" : "my_project",
        "totalPullRequests" : 2,
        "pullRequests" : [ {
          "key" : "123",
          "title" : "Add feature X",
          "branch" : "feature/bar"
        }, {
          "key" : "234",
          "title" : "Add feature Y",
          "branch" : "feature/baz"
        } ]
      }""");
    assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
      .contains(new ReceivedRequest("Bearer token", ""));
  }

  @SonarQubeMcpServerTest
  void it_should_handle_server_error_response(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get(PullRequestsApi.PULL_REQUESTS_LIST_PATH + "?project=my_project")
      .willReturn(aResponse().withStatus(500)));
    var mcpClient = harness.newClient();

    var result = mcpClient.callTool(ListPullRequestsTool.TOOL_NAME, Map.of(ListPullRequestsTool.PROJECT_KEY_PROPERTY, "my_project"));

    assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true).addTextContent("An error occurred during the tool execution: SonarQube answered with Error 500 on " + harness.getMockSonarQubeServer().baseUrl() + "/api/project_pull_requests/list?project=my_project").build());
  }

  private static String generateEmptyPullRequestsResponse() {
    return """
      {
        "pullRequests": []
      }
      """;
  }

  private static String generatePullRequestsResponse() {
    return """
      {
        "pullRequests": [
          {
            "key": "123",
            "title": "Add feature X",
            "branch": "feature/bar",
            "base": "feature/foo",
            "status": {
              "qualityGateStatus": "OK",
              "bugs": 0,
              "vulnerabilities": 0,
              "codeSmells": 0
            },
            "analysisDate": "2017-04-01T02:15:42+0200",
            "url": "https://github.com/SonarSource/sonar-core-plugins/pull/32",
            "target": "feature/foo",
            "commit": {
              "sha": "P1A5AxmsWdy1WPk0YRk48lVPDuYcy4EgUjtm2oGXt6LKdM6YS9"
            },
            "contributors" : [
              {
                "name": "Foo Bar",
                "login": "foobar@github",
                "avatar": ""
              }
            ],
            "pullRequestUuidV1": "0195EC1F3DD35965AD",
            "pullRequestId": "d0ab25a7-1f8e-4f71-b36d-dcdc520c941b"
          },
          {
            "key": "234",
            "title": "Add feature Y",
            "branch": "feature/baz",
            "base": "feature/foo",
            "status": {
              "qualityGateStatus": "OK",
              "bugs": 0,
              "vulnerabilities": 0,
              "codeSmells": 0
            },
            "analysisDate": "2017-02-01T02:15:42+0200",
            "url": "https://github.com/SonarSource/sonar-core-plugins/pull/42",
            "target": "feature/foo",
            "commit": {
              "sha": "P1A5AxmsWdy1WPk0YRk48lVPDuYcy4EgUjtm2oGXt6LKdM6YS8"
            },
            "contributors" : [
              {
                "name": "Bar Baz",
                "login": "barbaz@github",
                "avatar": ""
              }
            ],
            "pullRequestUuidV1": "0195EC1F3E1A5965AE",
            "pullRequestId": "50f64362-48e5-41f5-afa6-a26ec5379f5b"
          }
        ]
      }
      """;
  }

}
