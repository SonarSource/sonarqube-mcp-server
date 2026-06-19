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
package org.sonarsource.sonarqube.mcp.tools.apps;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.apps.IssueHistoryApp;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.branches.ProjectBranchesApi;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.history.OrganizationsHistoryApi;
import org.sonarsource.sonarqube.mcp.tools.history.HistoryTargetResolver;
import org.sonarsource.sonarqube.mcp.tools.issues.GetIssueCountHistoryTool;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;

class OpenIssueHistoryAppToolTests {

  @SonarQubeMcpServerTest
  void it_should_only_be_available_on_sonarcloud(SonarQubeMcpServerTestHarness harness) {
    var serverClient = harness.newClient();

    assertThat(serverClient.listTools())
      .extracting(McpSchema.Tool::name)
      .doesNotContain(OpenIssueHistoryAppTool.TOOL_NAME);
  }

  @SonarQubeMcpServerTest
  void it_should_render_issue_history_as_a_table(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get(ProjectBranchesApi.BRANCHES_LIST_PATH + "?project=my_project")
      .willReturn(aResponse().withBody("""
        {
          "branches": [
            {"name":"main","isMain":true,"type":"LONG","branchId":"branch-main"}
          ]
        }
        """)));
    harness.getMockSonarQubeServer().stubFor(get(OrganizationsHistoryApi.ISSUE_COUNT_HISTORY_PATH +
      "?entityId=branch-main&entityType=PROJECT_BRANCH&startDate=2024-01-01T00%3A00%3A00Z&sliceBy=SEVERITY")
        .willReturn(aResponse().withBody("""
          {
            "issueCountHistory": [
              {
                "date": "2024-01-01T00:00:00Z",
                "distribution": [
                  {"key":"HIGH","value":4},
                  {"key":"MEDIUM","value":9}
                ]
              },
              {
                "date": "2024-01-02T00:00:00Z",
                "distribution": [
                  {"key":"HIGH","value":3}
                ]
              }
            ]
          }
          """)));
    var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

    assertThat(mcpClient.listTools())
      .extracting(McpSchema.Tool::name)
      .contains("issue_history_app")
      .doesNotContain("open_issue_history_app");

    var result = mcpClient.callTool(OpenIssueHistoryAppTool.TOOL_NAME, Map.of(
      HistoryTargetResolver.TARGET_TYPE_PROPERTY, HistoryTargetResolver.PROJECT_BRANCH,
      HistoryTargetResolver.PROJECT_KEY_PROPERTY, "my_project",
      GetIssueCountHistoryTool.START_DATE_PROPERTY, "2024-01-01",
      GetIssueCountHistoryTool.SLICE_BY_PROPERTY, "SEVERITY"));

    assertThat(result.isError()).isFalse();
    assertThat(result.meta()).containsEntry("openai/outputTemplate", IssueHistoryApp.RESOURCE_URI);
    var embeddedResource = (McpSchema.EmbeddedResource) result.content().getFirst();
    var textResource = (McpSchema.TextResourceContents) embeddedResource.resource();
    assertThat(textResource.uri()).isEqualTo(IssueHistoryApp.RESOURCE_URI);
    assertThat(textResource.mimeType()).isEqualTo(IssueHistoryApp.MIME_TYPE);
    assertThat(textResource.text())
      .contains("<h1>Issue History</h1>")
      .contains("<tbody id=\"rows\">")
      .contains("<script>")
      .contains("var initializeProtocolVersions = [\"2025-11-25\", \"2025-06-18\", \"2024-11-05\"]")
      .contains("function renderIssueHistory(history)")
      .contains("structured.issueHistory.issueCountHistory")
      .contains("hydrateFromMessage(message)")
      .contains("request(\"ui/initialize\", {")
      .contains("protocolVersion: initializeProtocolVersions[index]")
      .contains("appInfo: {")
      .contains("name: \"sonarqube_issue_history\"")
      .contains("appCapabilities: {}")
      .contains("}, 2000)")
      .contains("notify(\"ui/notifications/initialized\")")
      .contains("window.requestAnimationFrame(notifySizeChanged)")
      .contains("<td>2024-01-01T00:00:00Z</td>")
      .contains("<td>HIGH</td>")
      .contains("<td>4</td>")
      .contains("<td>MEDIUM</td>")
      .contains("<td>9</td>")
      .contains("<td>2024-01-02T00:00:00Z</td>")
      .contains("<td>3</td>");

    var readResourceResult = mcpClient.readResource(IssueHistoryApp.RESOURCE_URI);
    var readResource = (McpSchema.TextResourceContents) readResourceResult.contents().getFirst();
    assertThat(readResource.text())
      .contains("<tbody id=\"rows\">")
      .contains("<td>2024-01-01T00:00:00Z</td>")
      .contains("<td>HIGH</td>")
      .contains("<td>4</td>");
  }

}
