/*
 * SonarQube MCP Server
 * Copyright (C) SonarSource
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
package org.sonarsource.sonarqube.mcp.tools.branches;

import io.modelcontextprotocol.spec.McpSchema;
import com.github.tomakehurst.wiremock.http.Body;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.harness.ReceivedRequest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.branches.ProjectBranchesApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertSchemaEquals;

class ListBranchesToolTests {

  @SonarQubeMcpServerTest
  void it_should_validate_output_schema_and_annotations(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient();

    var tool = mcpClient.listTools().stream().filter(t -> t.name().equals(ListBranchesTool.TOOL_NAME)).findFirst().orElseThrow();

    assertThat(tool.annotations()).isNotNull();
    assertThat(tool.annotations().readOnlyHint()).isTrue();
    assertThat(tool.annotations().openWorldHint()).isTrue();

    assertSchemaEquals(tool.outputSchema(), """
      {
         "type":"object",
         "properties":{
            "projectKey":{
               "description":"Project key",
               "type":"string"
            },
            "totalBranches":{
               "description":"Total number of branches",
               "type":"integer"
            },
            "branches":{
               "description":"List of branches for this project",
               "type":"array",
               "items":{
                  "type":"object",
                  "properties":{
                     "name":{
                        "type":"string",
                        "description":"Branch name that can be used with other tools as the branch parameter"
                     },
                     "isMain":{
                        "type":"boolean",
                        "description":"Whether this is the main branch"
                     },
                     "type":{
                        "type":"string",
                        "description":"Branch type in SonarQube"
                     },
                     "qualityGateStatus":{
                        "type":"string",
                        "description":"Quality gate status for this branch"
                     },
                     "analysisDate":{
                        "type":"string",
                        "description":"Date of the last analysis"
                     },
                     "branchId":{
                        "type":"string",
                        "description":"Internal branch identifier"
                     }
                  },
                  "required":[
                     "isMain",
                     "name"
                  ]
               }
            }
         },
         "required":[
            "branches",
            "projectKey",
            "totalBranches"
         ]
      }
      """);
  }

  @SonarQubeMcpServerTest
  void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get(ProjectBranchesApi.BRANCHES_LIST_PATH + "?project=my_project")
      .willReturn(aResponse().withStatus(403)));
    var mcpClient = harness.newClient();

    var result = mcpClient.callTool(ListBranchesTool.TOOL_NAME, Map.of(ListBranchesTool.PROJECT_KEY_PROPERTY, "my_project"));

    assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true).addTextContent("An error occurred during the tool execution: SonarQube answered with Forbidden. Please verify your token has the required permissions for this operation.").build());
  }

  @SonarQubeMcpServerTest
  void it_should_return_empty_message_when_no_branches(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get(ProjectBranchesApi.BRANCHES_LIST_PATH + "?project=my_project")
      .willReturn(aResponse().withResponseBody(
        Body.fromJsonBytes(generateEmptyBranchesResponse().getBytes(StandardCharsets.UTF_8))
      )));
    var mcpClient = harness.newClient();

    var result = mcpClient.callTool(ListBranchesTool.TOOL_NAME, Map.of(ListBranchesTool.PROJECT_KEY_PROPERTY, "my_project"));

    assertResultEquals(result, """
      {
        "projectKey" : "my_project",
        "totalBranches" : 0,
        "branches" : [ ]
      }""");
    assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
      .contains(new ReceivedRequest("Bearer token", ""));
  }

  @SonarQubeMcpServerTest
  void it_should_list_branches(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get(ProjectBranchesApi.BRANCHES_LIST_PATH + "?project=my_project")
      .willReturn(aResponse().withResponseBody(
        Body.fromJsonBytes(generateBranchesResponse().getBytes(StandardCharsets.UTF_8))
      )));
    var mcpClient = harness.newClient();

    var result = mcpClient.callTool(ListBranchesTool.TOOL_NAME, Map.of(ListBranchesTool.PROJECT_KEY_PROPERTY, "my_project"));

    assertResultEquals(result, """
      {
        "projectKey" : "my_project",
        "totalBranches" : 2,
        "branches" : [ {
          "name" : "develop",
          "isMain" : false,
          "type" : "BRANCH",
          "qualityGateStatus" : "OK",
          "analysisDate" : "2017-04-03T13:37:00+0100",
          "branchId" : "ac312cc6-26a2-4e2c-9eff-1072358f2017"
        }, {
          "name" : "main",
          "isMain" : true,
          "type" : "BRANCH",
          "qualityGateStatus" : "ERROR",
          "analysisDate" : "2017-04-01T01:15:42+0100",
          "branchId" : "57f02458-65db-4e7f-a144-20122af12a4c"
        } ]
      }""");
    assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
      .contains(new ReceivedRequest("Bearer token", ""));
  }

  @SonarQubeMcpServerTest
  void it_should_handle_server_error_response(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get(ProjectBranchesApi.BRANCHES_LIST_PATH + "?project=my_project")
      .willReturn(aResponse().withStatus(500)));
    var mcpClient = harness.newClient();

    var result = mcpClient.callTool(ListBranchesTool.TOOL_NAME, Map.of(ListBranchesTool.PROJECT_KEY_PROPERTY, "my_project"));

    assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true).addTextContent("An error occurred during the tool execution: SonarQube answered with Error 500 on " + harness.getMockSonarQubeServer().baseUrl() + "/api/project_branches/list?project=my_project").build());
  }

  private static String generateEmptyBranchesResponse() {
    return """
      {
        "branches": []
      }
      """;
  }

  private static String generateBranchesResponse() {
    return """
      {
        "branches": [
          {
            "name": "develop",
            "isMain": false,
            "type": "BRANCH",
            "status": {
              "qualityGateStatus": "OK"
            },
            "analysisDate": "2017-04-03T13:37:00+0100",
            "branchId": "ac312cc6-26a2-4e2c-9eff-1072358f2017"
          },
          {
            "name": "main",
            "isMain": true,
            "type": "BRANCH",
            "status": {
              "qualityGateStatus": "ERROR"
            },
            "analysisDate": "2017-04-01T01:15:42+0100",
            "branchId": "57f02458-65db-4e7f-a144-20122af12a4c"
          }
        ]
      }
      """;
  }

}
