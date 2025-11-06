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
package org.sonarsource.sonarqube.mcp.tools.projects;

import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.components.response.SearchResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.SchemaUtils;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public class SearchMyProjectsTool extends Tool {

  public static final String TOOL_NAME = "search_my_sonarqube_projects";
  public static final String PAGE_PROPERTY = "page";

  private final ServerApiProvider serverApiProvider;

  public SearchMyProjectsTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(SearchMyProjectsToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Search My SonarQube Projects")
      .setDescription("Find SonarQube projects. The response is paginated.")
      .addStringProperty(PAGE_PROPERTY, "An optional page number. Defaults to 1.")
      .build());
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var page = arguments.getIntOrDefault(PAGE_PROPERTY, 1);
    var projects = serverApiProvider.get().componentsApi().searchProjectsInMyOrg(page);
    var textResponse = buildResponseFromAllProjectsResponse(projects);
    var structuredContent = buildStructuredContent(projects);
    return Tool.Result.success(textResponse, structuredContent);
  }

  private static String buildResponseFromAllProjectsResponse(SearchResponse response) {
    var stringBuilder = new StringBuilder();
    var projects = response.components();

    if (projects.isEmpty()) {
      stringBuilder.append("No projects were found.");
      return stringBuilder.toString();
    }

    stringBuilder.append("Found ").append(projects.size()).append(" Sonar projects in your organization.\n");
    
    var paging = response.paging();
    var totalPages = (int) Math.ceil((double) paging.total() / paging.pageSize());
    stringBuilder.append("This response is paginated and this is the page ").append(paging.pageIndex())
      .append(" out of ").append(totalPages).append(" total pages. There is a maximum of ")
      .append(paging.pageSize()).append(" projects per page.\n");

    projects.forEach(p -> {
      stringBuilder.append("Project key: ").append(p.key()).append(" | Project name: ").append(p.name());
      stringBuilder.append("\n");
    });

    return stringBuilder.toString().trim();
  }

  private static java.util.Map<String, Object> buildStructuredContent(SearchResponse response) {
    var projects = response.components().stream()
      .map(p -> new SearchMyProjectsToolResponse.Project(p.key(), p.name()))
      .toList();

    var paging = response.paging();
    var pagingResponse = new SearchMyProjectsToolResponse.Paging(
      paging.pageIndex(),
      paging.pageSize(),
      paging.total()
    );

    var toolResponse = new SearchMyProjectsToolResponse(projects, pagingResponse);
    return SchemaUtils.toStructuredContent(toolResponse);
  }

}
