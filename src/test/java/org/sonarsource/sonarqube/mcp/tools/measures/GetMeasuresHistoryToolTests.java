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
package org.sonarsource.sonarqube.mcp.tools.measures;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.branches.ProjectBranchesApi;
import org.sonarsource.sonarqube.mcp.serverapi.enterprises.EnterprisesApi;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.history.OrganizationsHistoryApi;
import org.sonarsource.sonarqube.mcp.tools.history.HistoryTargetResolver;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;

class GetMeasuresHistoryToolTests {

  @SonarQubeMcpServerTest
  void it_should_only_be_available_on_sonarcloud(SonarQubeMcpServerTestHarness harness) {
    var serverClient = harness.newClient();

    assertThat(serverClient.listTools())
      .extracting(McpSchema.Tool::name)
      .doesNotContain(GetMeasuresHistoryTool.TOOL_NAME);
  }

  @SonarQubeMcpServerTest
  void it_should_validate_annotations_on_sonarcloud(SonarQubeMcpServerTestHarness harness) {
    var cloudClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

    var tool = cloudClient.listTools().stream()
      .filter(candidate -> candidate.name().equals(GetMeasuresHistoryTool.TOOL_NAME))
      .findFirst()
      .orElseThrow();

    assertThat(tool.annotations()).isNotNull();
    assertThat(tool.annotations().readOnlyHint()).isTrue();
    assertThat(tool.annotations().openWorldHint()).isTrue();
    assertThat(tool.annotations().destructiveHint()).isFalse();
    assertThat(tool.inputSchema().properties().get(GetMeasuresHistoryTool.START_DATE_PROPERTY).toString())
      .contains("YYYY-MM-DDThh:mm:ssZ");
    assertThat(tool.inputSchema().properties().get(GetMeasuresHistoryTool.END_DATE_PROPERTY).toString())
      .contains("YYYY-MM-DDThh:mm:ssZ");
  }

  @SonarQubeMcpServerTest
  void it_should_resolve_project_branch_and_return_measures_history(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get(ProjectBranchesApi.BRANCHES_LIST_PATH + "?project=my_project")
      .willReturn(aResponse().withBody("""
        {
          "branches": [
            {"name":"main","isMain":true,"type":"LONG","branchId":"branch-main"},
            {"name":"release","isMain":false,"type":"LONG","branchId":"branch-release"}
          ]
        }
        """)));
    harness.getMockSonarQubeServer().stubFor(get(OrganizationsHistoryApi.MEASURES_HISTORY_PATH +
      "?entityType=PROJECT_BRANCH&entityId=branch-release&metricKeys=coverage,bugs&startDate=2024-01-01&endDate=2024-01-31")
        .willReturn(aResponse().withBody("""
          {
            "measuresHistory": [
              {
                "date": "2024-01-01T00:00:00Z",
                "measures": [
                  {"metric":"coverage","type":"PERCENT","value":"81.2"},
                  {"metric":"bugs","type":"INT","value":"3"}
                ]
              }
            ]
          }
          """)));
    var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

    var result = mcpClient.callTool(GetMeasuresHistoryTool.TOOL_NAME, Map.of(
      HistoryTargetResolver.TARGET_TYPE_PROPERTY, HistoryTargetResolver.PROJECT_BRANCH,
      HistoryTargetResolver.PROJECT_KEY_PROPERTY, "my_project",
      HistoryTargetResolver.BRANCH_PROPERTY, "release",
      GetMeasuresHistoryTool.METRIC_KEYS_PROPERTY, List.of("coverage", "bugs"),
      GetMeasuresHistoryTool.START_DATE_PROPERTY, "2024-01-01",
      GetMeasuresHistoryTool.END_DATE_PROPERTY, "2024-01-31"));

    assertResultEquals(result, """
      {
        "measuresHistory" : [ {
          "date" : "2024-01-01T00:00:00Z",
          "measures" : [ {
            "metric" : "coverage",
            "type" : "PERCENT",
            "value" : "81.2"
          }, {
            "metric" : "bugs",
            "type" : "INT",
            "value" : "3"
          } ]
        } ]
      }""");
  }

  @SonarQubeMcpServerTest
  void it_should_call_history_endpoint_directly_when_portfolio_id_is_provided(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get(OrganizationsHistoryApi.MEASURES_HISTORY_PATH +
      "?entityType=PORTFOLIO&entityId=portfolio-1&metricKeys=ncloc&startDate=2024-01-01")
      .willReturn(aResponse().withBody("""
        {"measuresHistory":[]}
        """)));
    var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

    var result = mcpClient.callTool(GetMeasuresHistoryTool.TOOL_NAME, Map.of(
      HistoryTargetResolver.TARGET_TYPE_PROPERTY, HistoryTargetResolver.PORTFOLIO,
      HistoryTargetResolver.PORTFOLIO_ID_PROPERTY, "portfolio-1",
      GetMeasuresHistoryTool.METRIC_KEYS_PROPERTY, List.of("ncloc"),
      GetMeasuresHistoryTool.START_DATE_PROPERTY, "2024-01-01"));

    assertResultEquals(result, """
      {
        "measuresHistory" : [ ]
      }""");
  }

  @SonarQubeMcpServerTest
  void it_should_allow_measure_history_without_type(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get(OrganizationsHistoryApi.MEASURES_HISTORY_PATH +
      "?entityType=PORTFOLIO&entityId=portfolio-1&metricKeys=ncloc&startDate=2026-06-17T00%3A00%3A00Z&endDate=2026-06-17T23%3A59%3A59Z")
      .willReturn(aResponse().withBody("""
        {
          "measuresHistory": [
            {
              "date": "2026-06-17T00:00:00Z",
              "measures": [
                {"metric":"ncloc","value":"94340"}
              ]
            }
          ]
        }
        """)));
    var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

    var result = mcpClient.callTool(GetMeasuresHistoryTool.TOOL_NAME, Map.of(
      HistoryTargetResolver.TARGET_TYPE_PROPERTY, HistoryTargetResolver.PORTFOLIO,
      HistoryTargetResolver.PORTFOLIO_ID_PROPERTY, "portfolio-1",
      GetMeasuresHistoryTool.METRIC_KEYS_PROPERTY, List.of("ncloc"),
      GetMeasuresHistoryTool.START_DATE_PROPERTY, "2026-06-17T00:00:00Z",
      GetMeasuresHistoryTool.END_DATE_PROPERTY, "2026-06-17T23:59:59Z"));

    assertResultEquals(result, """
      {
        "measuresHistory" : [ {
          "date" : "2026-06-17T00:00:00Z",
          "measures" : [ {
            "metric" : "ncloc",
            "value" : "94340"
          } ]
        } ]
      }""");
  }

  @SonarQubeMcpServerTest
  void it_should_return_candidates_when_portfolio_name_is_ambiguous(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get(EnterprisesApi.PORTFOLIOS_PATH + "?q=Shared&favorite=true")
      .willReturn(aResponse().withBody("""
        {
          "portfolios": [
            {"id":"portfolio-1","name":"Shared"},
            {"id":"portfolio-2","name":"Shared"}
          ]
        }
        """)));
    var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

    var result = mcpClient.callTool(GetMeasuresHistoryTool.TOOL_NAME, Map.of(
      HistoryTargetResolver.TARGET_TYPE_PROPERTY, HistoryTargetResolver.PORTFOLIO,
      HistoryTargetResolver.PORTFOLIO_NAME_PROPERTY, "Shared",
      GetMeasuresHistoryTool.METRIC_KEYS_PROPERTY, List.of("coverage"),
      GetMeasuresHistoryTool.START_DATE_PROPERTY, "2024-01-01"));

    assertThat(result).isEqualTo(McpSchema.CallToolResult.builder()
      .isError(true)
      .addTextContent("Multiple portfolios named 'Shared' were found. Retry with portfolioId. Candidates: Shared (portfolio-1), Shared (portfolio-2)")
      .build());
  }
}
