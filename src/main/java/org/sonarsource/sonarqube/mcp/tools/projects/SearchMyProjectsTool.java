/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
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
package org.sonarsource.sonarqube.mcp.tools.projects;

import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.components.response.SearchResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

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
      .setReadOnlyHint()
      .build(),
      ToolCategory.PROJECTS);
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var page = arguments.getIntOrDefault(PAGE_PROPERTY, 1);
    var projects = serverApiProvider.get().componentsApi().searchProjectsInMyOrg(page);
    var toolResponse = buildStructuredContent(projects);
    return Tool.Result.success(toolResponse);
  }

  private static SearchMyProjectsToolResponse buildStructuredContent(SearchResponse response) {
    var projects = response.components().stream()
      .map(p -> new SearchMyProjectsToolResponse.Project(p.key(), p.name()))
      .toList();

    var paging = response.paging();
    var pagingResponse = new SearchMyProjectsToolResponse.Paging(
      paging.pageIndex(),
      paging.pageSize(),
      paging.total()
    );

    return new SearchMyProjectsToolResponse(projects, pagingResponse);
  }

}
