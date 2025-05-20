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
package org.sonar.mcp.serverapi.projects;

import com.google.gson.Gson;
import java.util.List;
import org.sonar.mcp.serverapi.ServerApiHelper;

public class ProjectsApi {

  public static final String SEARCH_MY_PROJECTS_PATH = "/api/projects/search_my_projects";

  private final ServerApiHelper helper;

  public ProjectsApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public SearchResponse searchMyProjects() {
    try (var response = helper.get(SEARCH_MY_PROJECTS_PATH)) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, SearchResponse.class);
    }
  }

  public record SearchResponse(Paging paging, List<ProjectResponse> projects) {
  }

  public record Paging(Integer pageIndex, Integer pageSize, Integer total) {
  }

  public record ProjectResponse(String key, String name, String lastAnalysisDate, String qualityGate, List<LinksResponse> links) {
  }

  public record LinksResponse(String name, String type, String href) {
  }

}
