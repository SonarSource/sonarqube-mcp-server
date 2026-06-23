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
package org.sonarsource.sonarqube.mcp.tools.agenticreadiness;

import java.util.Map;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.agenticreadiness.AgenticReadinessApi;
import org.sonarsource.sonarqube.mcp.serverapi.branches.ProjectBranchesApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertMissingRequiredArgument;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertToolExecutionError;

class ListAgenticReadinessAssessmentsToolTests {

  private static final Map<String, String> ORG_ENV = Map.of("SONARQUBE_ORG", "my-org");

  private static final String BRANCHES_RESPONSE = """
    {
      "branches": [
        {"name": "main", "isMain": true, "type": "LONG", "branchId": "project-uuid-123"}
      ]
    }
    """;

  @SonarQubeMcpServerTest
  void it_should_list_assessments(SonarQubeMcpServerTestHarness harness) {
    harness.stubSaraEnabled();
    harness.getMockSonarQubeServer().stubFor(get(ProjectBranchesApi.BRANCHES_LIST_PATH + "?project=my-project")
      .willReturn(okJson(BRANCHES_RESPONSE)));
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(AgenticReadinessApi.ASSESSMENTS_PATH))
      .willReturn(okJson("""
        {
          "assessments": [
            {
              "id": "assessment-uuid-1",
              "projectId": "project-uuid-123",
              "createdAt": "2026-06-23T10:00:00Z",
              "status": "COMPLETED",
              "result": {"overallLevel": "L2"}
            },
            {
              "id": "assessment-uuid-2",
              "projectId": "project-uuid-123",
              "createdAt": "2026-06-22T09:00:00Z",
              "status": "COMPLETED",
              "result": {"overallLevel": "L1"}
            }
          ]
        }
        """)));

    var mcpClient = harness.newClient(ORG_ENV);
    var result = mcpClient.callTool(ListAgenticReadinessAssessmentsTool.TOOL_NAME, Map.of("projectKey", "my-project"));

    assertResultEquals(result, """
      {
        "assessments" : [ {
          "id" : "assessment-uuid-1",
          "projectId" : "project-uuid-123",
          "createdAt" : "2026-06-23T10:00:00Z",
          "status" : "COMPLETED",
          "result" : {
            "overallLevel" : "L2"
          }
        }, {
          "id" : "assessment-uuid-2",
          "projectId" : "project-uuid-123",
          "createdAt" : "2026-06-22T09:00:00Z",
          "status" : "COMPLETED",
          "result" : {
            "overallLevel" : "L1"
          }
        } ]
      }""");
  }

  @SonarQubeMcpServerTest
  void it_should_list_assessments_for_a_branch(SonarQubeMcpServerTestHarness harness) {
    harness.stubSaraEnabled();
    harness.getMockSonarQubeServer().stubFor(get(ProjectBranchesApi.BRANCHES_LIST_PATH + "?project=my-project")
      .willReturn(okJson(BRANCHES_RESPONSE)));
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(AgenticReadinessApi.ASSESSMENTS_PATH))
      .willReturn(okJson("""
        {
          "assessments": [
            {
              "id": "assessment-uuid-3",
              "projectId": "project-uuid-123",
              "createdAt": "2026-06-23T11:00:00Z",
              "status": "PENDING",
              "branch": "feature/my-branch"
            }
          ]
        }
        """)));

    var mcpClient = harness.newClient(ORG_ENV);
    var result = mcpClient.callTool(ListAgenticReadinessAssessmentsTool.TOOL_NAME,
      Map.of("projectKey", "my-project", "branch", "feature/my-branch"));

    assertResultEquals(result, """
      {
        "assessments" : [ {
          "id" : "assessment-uuid-3",
          "projectId" : "project-uuid-123",
          "createdAt" : "2026-06-23T11:00:00Z",
          "status" : "PENDING",
          "branch" : "feature/my-branch"
        } ]
      }""");
  }

  @SonarQubeMcpServerTest
  void it_should_return_error_when_project_key_missing(SonarQubeMcpServerTestHarness harness) {
    harness.stubSaraEnabled();
    var mcpClient = harness.newClient(ORG_ENV);

    assertMissingRequiredArgument(mcpClient.callTool(ListAgenticReadinessAssessmentsTool.TOOL_NAME, Map.of()), "projectKey");
  }

  @SonarQubeMcpServerTest
  void it_should_return_error_on_api_failure(SonarQubeMcpServerTestHarness harness) {
    harness.stubSaraEnabled();
    harness.getMockSonarQubeServer().stubFor(get(ProjectBranchesApi.BRANCHES_LIST_PATH + "?project=my-project")
      .willReturn(okJson(BRANCHES_RESPONSE)));
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(AgenticReadinessApi.ASSESSMENTS_PATH))
      .willReturn(aResponse().withStatus(500)));

    var mcpClient = harness.newClient(ORG_ENV);
    var result = mcpClient.callTool(ListAgenticReadinessAssessmentsTool.TOOL_NAME, Map.of("projectKey", "my-project"));

    assertToolExecutionError(result, "500");
  }
}
