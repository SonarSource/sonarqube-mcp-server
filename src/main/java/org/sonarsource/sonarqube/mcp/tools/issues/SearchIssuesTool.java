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

import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.issues.response.SearchResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public class SearchIssuesTool extends Tool {

  public static final String TOOL_NAME = "search_sonar_issues_in_projects";
  public static final String PROJECTS_PROPERTY = "projects";
  public static final String PULL_REQUEST_ID_PROPERTY = "pullRequestId";
  public static final String PAGE_PROPERTY = "p";
  public static final String PAGE_SIZE_PROPERTY = "ps";

  private final ServerApi serverApi;

  public SearchIssuesTool(ServerApi serverApi) {
    super(new SchemaToolBuilder()
      .setName(TOOL_NAME)
      .setDescription("Search for Sonar issues in my organization's projects.")
      .addArrayProperty(PROJECTS_PROPERTY, "string", "An optional list of Sonar projects to look in")
      .addStringProperty(PULL_REQUEST_ID_PROPERTY, "The identifier of the Pull Request to look in")
      .addNumberProperty(PAGE_PROPERTY, "An optional page number. Defaults to 1.")
      .addNumberProperty(PAGE_SIZE_PROPERTY, "An optional page size. Must be greater than 0 and less than or equal to 500. Defaults to 100.")
      .build());
    this.serverApi = serverApi;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var projects = arguments.getOptionalStringList(PROJECTS_PROPERTY);
    var pullRequestId = arguments.getOptionalString(PULL_REQUEST_ID_PROPERTY);
    var page = arguments.getOptionalInteger(PAGE_PROPERTY);
    var pageSize = arguments.getOptionalInteger(PAGE_SIZE_PROPERTY);
    var response = serverApi.issuesApi().search(projects, pullRequestId, page, pageSize);
    return Tool.Result.success(buildResponseFromSearchResponse(response));
  }

  private static String buildResponseFromSearchResponse(SearchResponse response) {
    var stringBuilder = new StringBuilder();
    var issues = response.issues();

    if (issues.isEmpty()) {
      stringBuilder.append("No issues were found.");
      return stringBuilder.toString();
    }

    stringBuilder.append("Found ").append(issues.size()).append(" issues.\n");

    var paging = response.paging();
    var totalPages = (int) Math.ceil((double) paging.total() / paging.pageSize());
    stringBuilder.append("This response is paginated and this is the page ").append(paging.pageIndex())
      .append(" out of ").append(totalPages).append(" total pages. There is a maximum of ")
      .append(paging.pageSize()).append(" issues per page.\n");

    for (var issue : issues) {
      stringBuilder.append("Issue key: ").append(issue.key())
        .append(" | Rule: ").append(issue.rule())
        .append(" | Project: ").append(issue.project())
        .append(" | Component: ").append(issue.component())
        .append(" | Severity: ").append(issue.severity())
        .append(" | Status: ").append(issue.status())
        .append(" | Message: ").append(issue.message())
        .append(" | Attribute: ").append(issue.cleanCodeAttribute())
        .append(" | Category: ").append(issue.cleanCodeAttributeCategory())
        .append(" | Author: ").append(issue.author());
      var textRange = issue.textRange();
      stringBuilder
        .append(" | Start Line: ").append(textRange.startLine())
        .append(" | End Line: ").append(textRange.endLine());
      stringBuilder.append(" | Created: ").append(issue.creationDate());
      stringBuilder.append("\n");
    }

    return stringBuilder.toString().trim();
  }

}
