/*
 * Sonar MCP Server
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
package org.sonar.mcp.tools.issues;

import java.util.List;
import java.util.Map;
import org.sonar.mcp.serverapi.ServerApi;
import org.sonar.mcp.serverapi.issues.IssuesApi;
import org.sonar.mcp.tools.SchemaToolBuilder;
import org.sonar.mcp.tools.Tool;

public class SearchIssuesTool extends Tool {

  public static final String TOOL_NAME = "search_sonar_issues_in_projects";
  public static final String PROJECTS_PROPERTY = "projects";
  public static final String PULL_REQUEST_ID_PROPERTY = "pullRequestId";

  private final ServerApi serverApi;

  public SearchIssuesTool(ServerApi serverApi) {
    super(new SchemaToolBuilder()
      .setName(TOOL_NAME)
      .setDescription("Search for Sonar issues in my organization's projects.")
      .addArrayProperty(PROJECTS_PROPERTY, "String", "An optional list of Sonar projects to look in")
      .addStringProperty(PULL_REQUEST_ID_PROPERTY, "The identifier of the Pull Request to look in")
      .build());
    this.serverApi = serverApi;
  }

  @Override
  public Tool.Result execute(Map<String, Object> arguments) {
    List<String> projects = null;
    if (arguments.containsKey(PROJECTS_PROPERTY)) {
      projects = ((List<String>) arguments.get(PROJECTS_PROPERTY));
    }
    String pullRequestId = null;
    if (arguments.containsKey(PULL_REQUEST_ID_PROPERTY)) {
      pullRequestId = ((String) arguments.get(PULL_REQUEST_ID_PROPERTY));
    }

    var response = serverApi.issuesApi().search(projects, pullRequestId);
    return Tool.Result.success(buildResponseFromSearchResponse(response.issues()));
  }

  private static String buildResponseFromSearchResponse(List<IssuesApi.Issue> issues) {
    var stringBuilder = new StringBuilder();

    if (issues.isEmpty()) {
      stringBuilder.append("No issues were found.");
      return stringBuilder.toString();
    }

    stringBuilder.append("Found ").append(issues.size()).append(" issues.\n");

    issues.forEach(p -> {
      stringBuilder.append("Issue key: ").append(p.key()).append(" | Rule name: ").append(p.rule()).append(" | Project name: ").append(p.project());
      stringBuilder.append("\n");
    });

    return stringBuilder.toString();
  }

}
