/*
 * Sonar MCP Server
 * Copyright (C) 2025 SonarSource
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
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
    try (var response = helper.get(buildPath())) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, SearchResponse.class);
    }
  }

  private static String buildPath() {
    return SEARCH_MY_PROJECTS_PATH;
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
