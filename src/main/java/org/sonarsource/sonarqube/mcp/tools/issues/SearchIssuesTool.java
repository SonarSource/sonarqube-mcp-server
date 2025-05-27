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
package org.sonarsource.sonarqube.mcp.tools.issues;

import java.util.List;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.issues.response.SearchResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public class SearchIssuesTool extends Tool {

  public static final String TOOL_NAME = "search_sonar_issues_in_projects";
  public static final String PROJECTS_PROPERTY = "projects";
  public static final String PULL_REQUEST_ID_PROPERTY = "pullRequestId";

  private final ServerApi serverApi;

  public SearchIssuesTool(ServerApi serverApi) {
    super(new SchemaToolBuilder()
      .setName(TOOL_NAME)
      .setDescription("Search for Sonar issues in my organization's projects.")
      .addArrayProperty(PROJECTS_PROPERTY, "string", "An optional list of Sonar projects to look in")
      .addStringProperty(PULL_REQUEST_ID_PROPERTY, "The identifier of the Pull Request to look in")
      .build());
    this.serverApi = serverApi;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var projects = arguments.getOptionalStringList(PROJECTS_PROPERTY);
    var pullRequestId = arguments.getOptionalString(PULL_REQUEST_ID_PROPERTY);
    var response = serverApi.issuesApi().search(projects, pullRequestId);
    return Tool.Result.success(buildResponseFromSearchResponse(response.issues()));
  }

  private static String buildResponseFromSearchResponse(List<SearchResponse.Issue> issues) {
    var stringBuilder = new StringBuilder();

    if (issues.isEmpty()) {
      stringBuilder.append("No issues were found.");
      return stringBuilder.toString();
    }

    stringBuilder.append("Found ").append(issues.size()).append(" issues.\n");

    for (var issue : issues) {
      stringBuilder.append("Issue key: ").append(issue.key())
        .append(" | Rule: ").append(issue.rule())
        .append(" | Project: ").append(issue.project())
        .append(" | Severity: ").append(issue.severity())
        .append(" | Status: ").append(issue.status())
        .append(" | Message: ").append(issue.message())
        .append(" | Attribute: ").append(issue.cleanCodeAttribute())
        .append(" | Category: ").append(issue.cleanCodeAttributeCategory());
      var textRange = issue.textRange();
      if (textRange != null) {
        stringBuilder
          .append(" | Start Line: ").append(issue.textRange().startLine())
          .append(" | End Line: ").append(issue.textRange().endLine());
      }
      if (issue.creationDate() != null) {
        stringBuilder.append(" | Created: ").append(issue.creationDate());
      }
      stringBuilder.append("\n");
    }

    return stringBuilder.toString().trim();
  }

}
