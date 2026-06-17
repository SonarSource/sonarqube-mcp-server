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
package org.sonarsource.sonarqube.mcp.tools.issues;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.branches.ProjectBranchesApi;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.history.OrganizationsHistoryApi;
import org.sonarsource.sonarqube.mcp.tools.history.HistoryTargetResolver;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;

class GetIssueCountHistoryToolTests {

  @SonarQubeMcpServerTest
  void it_should_only_be_available_on_sonarcloud(SonarQubeMcpServerTestHarness harness) {
    var serverClient = harness.newClient();

    assertThat(serverClient.listTools())
      .extracting(McpSchema.Tool::name)
      .doesNotContain(GetIssueCountHistoryTool.TOOL_NAME);
  }

  @SonarQubeMcpServerTest
  void it_should_validate_annotations_on_sonarcloud(SonarQubeMcpServerTestHarness harness) {
    var cloudClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

    var tool = cloudClient.listTools().stream()
      .filter(candidate -> candidate.name().equals(GetIssueCountHistoryTool.TOOL_NAME))
      .findFirst()
      .orElseThrow();

    assertThat(tool.annotations()).isNotNull();
    assertThat(tool.annotations().readOnlyHint()).isTrue();
    assertThat(tool.annotations().openWorldHint()).isTrue();
    assertThat(tool.annotations().destructiveHint()).isFalse();
    assertThat(tool.inputSchema().properties().get(GetIssueCountHistoryTool.START_DATE_PROPERTY).toString())
      .contains("YYYY-MM-DDThh:mm:ssZ");
    assertThat(tool.inputSchema().properties().get(GetIssueCountHistoryTool.END_DATE_PROPERTY).toString())
      .contains("YYYY-MM-DDThh:mm:ssZ");
  }

  @SonarQubeMcpServerTest
  void it_should_resolve_main_branch_and_forward_filters(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get(ProjectBranchesApi.BRANCHES_LIST_PATH + "?project=my_project")
      .willReturn(aResponse().withBody("""
        {
          "branches": [
            {"name":"main","isMain":true,"type":"LONG","branchId":"branch-main"}
          ]
        }
        """)));
    harness.getMockSonarQubeServer().stubFor(get(OrganizationsHistoryApi.ISSUE_COUNT_HISTORY_PATH +
      "?entityId=branch-main&entityType=PROJECT_BRANCH&startDate=2024-01-01&endDate=2024-01-31" +
      "&impacts=MAINTAINABILITY%3AHIGH&issueTypes=BUG,VULNERABILITY&ruleKeys=java%3AS106&severities=HIGH&sliceBy=SEVERITY&statuses=OPEN,SAFE")
        .willReturn(aResponse().withBody("""
          {
            "issueCountHistory": [
              {
                "date": "2024-01-01T00:00:00Z",
                "distribution": [
                  {"key":"HIGH","value":4}
                ]
              }
            ]
          }
          """)));
    var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

    var result = mcpClient.callTool(GetIssueCountHistoryTool.TOOL_NAME, Map.of(
      HistoryTargetResolver.TARGET_TYPE_PROPERTY, HistoryTargetResolver.PROJECT_BRANCH,
      HistoryTargetResolver.PROJECT_KEY_PROPERTY, "my_project",
      GetIssueCountHistoryTool.START_DATE_PROPERTY, "2024-01-01",
      GetIssueCountHistoryTool.END_DATE_PROPERTY, "2024-01-31",
      GetIssueCountHistoryTool.IMPACTS_PROPERTY, List.of("MAINTAINABILITY:HIGH"),
      GetIssueCountHistoryTool.ISSUE_TYPES_PROPERTY, List.of("BUG", "VULNERABILITY"),
      GetIssueCountHistoryTool.RULE_KEYS_PROPERTY, List.of("java:S106"),
      GetIssueCountHistoryTool.SEVERITIES_PROPERTY, List.of("HIGH"),
      GetIssueCountHistoryTool.SLICE_BY_PROPERTY, "SEVERITY",
      GetIssueCountHistoryTool.STATUSES_PROPERTY, List.of("OPEN", "SAFE")));

    assertResultEquals(result, """
      {
        "issueCountHistory" : [ {
          "date" : "2024-01-01T00:00:00Z",
          "distribution" : [ {
            "key" : "HIGH",
            "value" : 4
          } ]
        } ]
      }""");
  }

  @SonarQubeMcpServerTest
  void it_should_validate_issue_count_history_enums(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

    var result = mcpClient.callTool(GetIssueCountHistoryTool.TOOL_NAME, Map.of(
      HistoryTargetResolver.TARGET_TYPE_PROPERTY, HistoryTargetResolver.PORTFOLIO,
      HistoryTargetResolver.PORTFOLIO_ID_PROPERTY, "portfolio-1",
      GetIssueCountHistoryTool.START_DATE_PROPERTY, "2024-01-01",
      GetIssueCountHistoryTool.STATUSES_PROPERTY, List.of("IN_SANDBOX")));

    assertThat(result).isEqualTo(McpSchema.CallToolResult.builder()
      .isError(true)
      .addTextContent("An error occurred during the tool execution: Invalid statuses: IN_SANDBOX. Possible values: ACCEPTED, CONFIRMED, FALSE_POSITIVE, FIXED, OPEN, REVIEWED, SAFE, TO_REVIEW")
      .build());
  }
}
