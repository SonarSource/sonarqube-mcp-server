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
package org.sonarsource.sonarqube.mcp.its.sonarcloud.tools;

import java.util.List;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.its.sonarcloud.AbstractSonarCloudStagingIT;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.tools.issues.ChangeIssueStatusTool;
import org.sonarsource.sonarqube.mcp.tools.issues.SearchIssuesTool;

import static org.sonarsource.sonarqube.mcp.its.sonarcloud.harness.SonarQubeMcpTestClient.assertStructuredContentContains;

import static org.sonarsource.sonarqube.mcp.its.sonarcloud.harness.SonarQubeMcpTestClient.structuredContent;

@Tag("SonarCloud")
class ChangeIssueStatusSonarCloudIT extends AbstractSonarCloudStagingIT {

  @Test
  void should_call_change_sonar_issue_status_against_staging() {
    var searchResult = mcpClient.callTool(SearchIssuesTool.TOOL_NAME, Map.of(
      SearchIssuesTool.PROJECTS_PROPERTY, new String[] {fixture.projectKey()},
      SearchIssuesTool.PAGE_SIZE_PROPERTY, 10));
    @SuppressWarnings("unchecked")
    var issues = (List<Map<String, Object>>) structuredContent(searchResult).get("issues");
    var issueKey = issues.stream()
      .filter(issue -> "java:S1118".equals(issue.get("rule")))
      .filter(issue -> {
        var status = issue.get("status");
        return "OPEN".equals(status) || "REOPENED".equals(status);
      })
      .map(issue -> (String) issue.get("key"))
      .findFirst()
      .orElseThrow(() -> new AssertionError("No unresolved java:S1118 issue found for " + fixture.projectKey()));

    try {
      var result = mcpClient.callTool(ChangeIssueStatusTool.TOOL_NAME, Map.of(
        ChangeIssueStatusTool.KEY_PROPERTY, issueKey,
        ChangeIssueStatusTool.STATUS_PROPERTY, "falsepositive"));

      assertStructuredContentContains(result, """
        {
          "success" : true,
          "issueKey" : "%s",
          "newStatus" : "falsepositive"
        }""".formatted(issueKey));
    } finally {
      mcpClient.callTool(ChangeIssueStatusTool.TOOL_NAME, Map.of(
        ChangeIssueStatusTool.KEY_PROPERTY, issueKey,
        ChangeIssueStatusTool.STATUS_PROPERTY, "reopen"));
    }
  }
}
