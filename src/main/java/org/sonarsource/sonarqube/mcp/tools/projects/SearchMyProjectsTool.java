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

import io.modelcontextprotocol.spec.McpSchema;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.components.response.SearchResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class SearchMyProjectsTool extends Tool {

  public static final String TOOL_NAME = "search_my_sonarqube_projects";
  public static final String PAGE_PROPERTY = "page";
  public static final String PAGE_SIZE_PROPERTY = "pageSize";
  public static final String SEARCH_QUERY_PROPERTY = "q";
  public static final int MAX_PAGE_SIZE = 500;

  private final ServerApiProvider serverApiProvider;

  public SearchMyProjectsTool(ServerApiProvider serverApiProvider, boolean isSonarCloud) {
    super(createToolDefinition(isSonarCloud),
      ToolCategory.PROJECTS);
    this.serverApiProvider = serverApiProvider;
  }

  private static McpSchema.Tool createToolDefinition(boolean isSonarCloud) {
    var scope = isSonarCloud ? "organization" : "instance";
    var description = "Find SonarQube projects in your " + scope + ". Supports searching by project name or key. " +
      "Use this first when projectKey is unknown - most other tools require the project key from this response.";

    return SchemaToolBuilder.forOutput(SearchMyProjectsToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Search My SonarQube Projects")
      .setDescription(description)
      .addStringProperty(PAGE_PROPERTY, "An optional page number. Defaults to 1.")
      .addNumberProperty(PAGE_SIZE_PROPERTY, "An optional page size. Must be greater than 0 and less than or equal to 500. Defaults to 500.")
      .addStringProperty(SEARCH_QUERY_PROPERTY, "An optional search query to filter projects by name (partial match) or key (exact match).")
      .setReadOnlyHint()
      .build();
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var page = arguments.getIntOrDefault(PAGE_PROPERTY, 1);
    var pageSize = arguments.getIntOrDefault(PAGE_SIZE_PROPERTY, MAX_PAGE_SIZE);
    var searchQuery = arguments.getOptionalString(SEARCH_QUERY_PROPERTY);
    
    if (pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
      return Tool.Result.failure("Page size must be greater than 0 and less than or equal to " + MAX_PAGE_SIZE);
    }
    
    var projects = serverApiProvider.get().componentsApi().searchProjects(page, pageSize, searchQuery);
    var toolResponse = buildStructuredContent(projects);
    return Tool.Result.success(toolResponse);
  }

  private static SearchMyProjectsToolResponse buildStructuredContent(SearchResponse response) {
    var projects = response.components().stream()
      .map(p -> new SearchMyProjectsToolResponse.Project(p.key(), p.name()))
      .toList();

    var paging = response.paging();
    var hasNextPage = (paging.pageIndex() * paging.pageSize()) < paging.total();
    var pagingResponse = new SearchMyProjectsToolResponse.Paging(
      paging.pageIndex(),
      paging.pageSize(),
      paging.total(),
      hasNextPage
    );

    return new SearchMyProjectsToolResponse(projects, pagingResponse);
  }

}
