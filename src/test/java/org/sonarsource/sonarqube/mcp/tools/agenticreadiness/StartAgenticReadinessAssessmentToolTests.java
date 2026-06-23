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

import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.agenticreadiness.AgenticReadinessApi;
import org.sonarsource.sonarqube.mcp.serverapi.branches.ProjectBranchesApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertMissingRequiredArgument;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertToolExecutionError;

class StartAgenticReadinessAssessmentToolTests {

  private static final Map<String, String> ORG_ENV = Map.of("SONARQUBE_ORG", "my-org");

  private static final String BRANCHES_RESPONSE = """
    {
      "branches": [
        {"name": "main", "isMain": true, "type": "LONG", "branchId": "project-uuid-123"}
      ]
    }
    """;

  private static final String ASSESSMENT_RESPONSE = """
    {
      "id": "assessment-uuid-1",
      "projectId": "project-uuid-123",
      "createdAt": "2026-06-23T10:00:00Z",
      "status": "PENDING",
      "branch": null,
      "error": null,
      "pillarExecutions": null,
      "result": null
    }
    """;

  @SonarQubeMcpServerTest
  void it_should_validate_input_schema(SonarQubeMcpServerTestHarness harness) {
    harness.stubSaraEnabled();
    var mcpClient = harness.newClient(ORG_ENV);

    var tool = mcpClient.listTools().stream()
      .filter(t -> t.name().equals(StartAgenticReadinessAssessmentTool.TOOL_NAME))
      .findFirst()
      .orElseThrow();

    assertThat(tool.inputSchema()).isEqualTo(Map.of(
      "type", "object",
      "properties", Map.of(
        "projectKey", Map.of("type", "string", "description", "The project key"),
        "branch", Map.of("type", "string", "description", "Branch to assess. Omit to use the project's default branch.")),
      "required", java.util.List.of("projectKey"),
      "additionalProperties", false));
  }

  @SonarQubeMcpServerTest
  void it_should_validate_annotations(SonarQubeMcpServerTestHarness harness) {
    harness.stubSaraEnabled();
    var mcpClient = harness.newClient(ORG_ENV);

    var tool = mcpClient.listTools().stream()
      .filter(t -> t.name().equals(StartAgenticReadinessAssessmentTool.TOOL_NAME))
      .findFirst()
      .orElseThrow();

    assertThat(tool.annotations()).isNotNull();
    assertThat(tool.annotations().readOnlyHint()).isFalse();
    assertThat(tool.annotations().openWorldHint()).isTrue();
    assertThat(tool.annotations().destructiveHint()).isFalse();
  }

  @SonarQubeMcpServerTest
  void it_should_start_an_assessment(SonarQubeMcpServerTestHarness harness) {
    harness.stubSaraEnabled();
    harness.getMockSonarQubeServer().stubFor(get(ProjectBranchesApi.BRANCHES_LIST_PATH + "?project=my-project")
      .willReturn(okJson(BRANCHES_RESPONSE)));
    harness.getMockSonarQubeServer().stubFor(post(urlPathEqualTo(AgenticReadinessApi.ASSESSMENTS_PATH))
      .willReturn(okJson(ASSESSMENT_RESPONSE)));

    var mcpClient = harness.newClient(ORG_ENV);
    var result = mcpClient.callTool(StartAgenticReadinessAssessmentTool.TOOL_NAME, Map.of("projectKey", "my-project"));

    assertResultEquals(result, """
      {
        "id" : "assessment-uuid-1",
        "projectId" : "project-uuid-123",
        "createdAt" : "2026-06-23T10:00:00Z",
        "status" : "PENDING"
      }""");
  }

  @SonarQubeMcpServerTest
  void it_should_start_an_assessment_on_a_branch(SonarQubeMcpServerTestHarness harness) {
    harness.stubSaraEnabled();
    harness.getMockSonarQubeServer().stubFor(get(ProjectBranchesApi.BRANCHES_LIST_PATH + "?project=my-project")
      .willReturn(okJson(BRANCHES_RESPONSE)));
    harness.getMockSonarQubeServer().stubFor(post(urlPathEqualTo(AgenticReadinessApi.ASSESSMENTS_PATH))
      .willReturn(okJson("""
        {
          "id": "assessment-uuid-2",
          "projectId": "project-uuid-123",
          "createdAt": "2026-06-23T10:00:00Z",
          "status": "PENDING",
          "branch": "feature/my-branch"
        }
        """)));

    var mcpClient = harness.newClient(ORG_ENV);
    var result = mcpClient.callTool(StartAgenticReadinessAssessmentTool.TOOL_NAME,
      Map.of("projectKey", "my-project", "branch", "feature/my-branch"));

    assertResultEquals(result, """
      {
        "id" : "assessment-uuid-2",
        "projectId" : "project-uuid-123",
        "createdAt" : "2026-06-23T10:00:00Z",
        "status" : "PENDING",
        "branch" : "feature/my-branch"
      }""");
  }

  @SonarQubeMcpServerTest
  void it_should_return_error_when_project_key_missing(SonarQubeMcpServerTestHarness harness) {
    harness.stubSaraEnabled();
    var mcpClient = harness.newClient(ORG_ENV);

    assertMissingRequiredArgument(mcpClient.callTool(StartAgenticReadinessAssessmentTool.TOOL_NAME, Map.of()), "projectKey");
  }

  @SonarQubeMcpServerTest
  void it_should_return_error_on_api_failure(SonarQubeMcpServerTestHarness harness) {
    harness.stubSaraEnabled();
    harness.getMockSonarQubeServer().stubFor(get(ProjectBranchesApi.BRANCHES_LIST_PATH + "?project=my-project")
      .willReturn(okJson(BRANCHES_RESPONSE)));
    harness.getMockSonarQubeServer().stubFor(post(urlPathEqualTo(AgenticReadinessApi.ASSESSMENTS_PATH))
      .willReturn(aResponse().withStatus(403)));

    var mcpClient = harness.newClient(ORG_ENV);
    var result = mcpClient.callTool(StartAgenticReadinessAssessmentTool.TOOL_NAME, Map.of("projectKey", "my-project"));

    assertToolExecutionError(result, "Forbidden");
  }

  @SonarQubeMcpServerTest
  void it_should_not_register_tools_when_sara_flag_is_disabled(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient(ORG_ENV);

    var toolNames = mcpClient.listTools().stream().map(McpSchema.Tool::name).toList();
    assertThat(toolNames)
      .isNotEmpty()
      .doesNotContain(StartAgenticReadinessAssessmentTool.TOOL_NAME);
  }
}
