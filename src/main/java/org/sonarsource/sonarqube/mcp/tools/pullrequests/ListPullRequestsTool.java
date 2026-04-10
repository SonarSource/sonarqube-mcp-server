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
package org.sonarsource.sonarqube.mcp.tools.pullrequests;

import jakarta.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class ListPullRequestsTool extends Tool {

  public static final String TOOL_NAME = "list_pull_requests";
  public static final String PROJECT_KEY_PROPERTY = "projectKey";

  private final ServerApiProvider serverApiProvider;
  @Nullable
  private final String configuredProjectKey;

  public ListPullRequestsTool(ServerApiProvider serverApiProvider, @Nullable String configuredProjectKey) {
    super(SchemaToolBuilder.forOutput(ListPullRequestsToolResponse.class)
        .setName(TOOL_NAME)
        .setTitle("List SonarQube Pull Requests")
        .setDescription("List all pull requests for a project. " +
          "Use this tool to discover available pull requests and their corresponding branch names before analyzing their coverage, issues, or quality. " +
          "Returns the pull request key/ID and source branch for each PR, which can be used with other tools that accept a pullRequest parameter.")
        .addProjectKeyProperty(PROJECT_KEY_PROPERTY, "Project key (e.g. my_project)", configuredProjectKey)
        .setReadOnlyHint()
        .build(),
      ToolCategory.PROJECTS);
    this.serverApiProvider = serverApiProvider;
    this.configuredProjectKey = configuredProjectKey;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var projectKey = arguments.getProjectKeyWithFallback(PROJECT_KEY_PROPERTY, configuredProjectKey);

    var response = serverApiProvider.get().pullRequestsApi().listPullRequests(projectKey);

    var pullRequests = response.pullRequests().stream()
      .map(pr -> new ListPullRequestsToolResponse.PullRequest(
        pr.key(),
        pr.title(),
        pr.branch()
      ))
      .toList();

    var toolResponse = new ListPullRequestsToolResponse(
      projectKey,
      pullRequests.size(),
      pullRequests
    );

    return Tool.Result.success(toolResponse);
  }

}
