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

import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.issues.response.SearchResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public class SearchIssuesTool extends Tool {

  public static final String TOOL_NAME = "search_sonar_issues_in_projects";
  public static final String PROJECTS_PROPERTY = "projects";
  public static final String PULL_REQUEST_ID_PROPERTY = "pullRequestId";
  public static final String SEVERITIES_PROPERTY = "severities";
  public static final String PAGE_PROPERTY = "p";
  public static final String PAGE_SIZE_PROPERTY = "ps";

  private final ServerApiProvider serverApiProvider;

  public SearchIssuesTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(SearchIssuesToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Search SonarQube Issues in Projects")
      .setDescription("Search for SonarQube issues in my organization's projects.")
      .addArrayProperty(PROJECTS_PROPERTY, "string", "An optional list of Sonar projects to look in")
      .addStringProperty(PULL_REQUEST_ID_PROPERTY, "The identifier of the Pull Request to look in")
      .addStringProperty(SEVERITIES_PROPERTY, "An optional list of severities to filter by, separated by a comma. Possible values: INFO, LOW, MEDIUM, HIGH, BLOCKER")
      .addNumberProperty(PAGE_PROPERTY, "An optional page number. Defaults to 1.")
      .addNumberProperty(PAGE_SIZE_PROPERTY, "An optional page size. Must be greater than 0 and less than or equal to 500. Defaults to 100.")
      .build());
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var projects = arguments.getOptionalStringList(PROJECTS_PROPERTY);
    var pullRequestId = arguments.getOptionalString(PULL_REQUEST_ID_PROPERTY);
    var severities = arguments.getOptionalStringList(SEVERITIES_PROPERTY);
    var page = arguments.getOptionalInteger(PAGE_PROPERTY);
    var pageSize = arguments.getOptionalInteger(PAGE_SIZE_PROPERTY);
    var response = serverApiProvider.get().issuesApi().search(projects, pullRequestId, severities, page, pageSize);
    var toolResponse = buildStructuredContent(response);
    return Tool.Result.success(toolResponse);
  }

  private static SearchIssuesToolResponse buildStructuredContent(SearchResponse response) {
    var issues = response.issues().stream()
      .map(issue -> {
        SearchIssuesToolResponse.TextRange textRange = null;
        if (issue.textRange() != null) {
          textRange = new SearchIssuesToolResponse.TextRange(
            issue.textRange().startLine(),
            issue.textRange().endLine()
          );
        }
        
        return new SearchIssuesToolResponse.Issue(
          issue.key(),
          issue.rule(),
          issue.project(),
          issue.component(),
          issue.severity(),
          issue.status(),
          issue.message(),
          issue.cleanCodeAttribute(),
          issue.cleanCodeAttributeCategory(),
          issue.author(),
          issue.creationDate(),
          textRange
        );
      })
      .toList();

    var paging = response.paging();
    var pagingResponse = new SearchIssuesToolResponse.Paging(
      paging.pageIndex(),
      paging.pageSize(),
      paging.total()
    );

    return new SearchIssuesToolResponse(issues, pagingResponse);
  }

}
