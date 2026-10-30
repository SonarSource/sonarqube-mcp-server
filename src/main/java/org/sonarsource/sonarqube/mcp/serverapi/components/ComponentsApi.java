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
package org.sonarsource.sonarqube.mcp.serverapi.components;

import com.google.gson.Gson;
import jakarta.annotation.Nullable;
import java.util.List;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;
import org.sonarsource.sonarqube.mcp.serverapi.components.response.SearchResponse;
import org.sonarsource.sonarqube.mcp.serverapi.exception.NotFoundException;

public class ComponentsApi {

  public static final String COMPONENTS_SEARCH_PATH = "/api/components/search";

  private final ServerApiHelper helper;

  public ComponentsApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public SearchResponse searchProjects(int page, @Nullable Integer pageSize, @Nullable String searchQuery) {
    var builder = new UrlBuilder(COMPONENTS_SEARCH_PATH)
      .addParam("p", page)
      .addParam("ps", pageSize)
      .addParam("q", searchQuery);

    var organization = helper.getOrganization();
    if (organization != null) {
      builder.addParam("organization", organization);
    } else {
      builder.addParam("qualifiers", "TRK");
    }

    try (var response = helper.get(builder.build())) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, SearchResponse.class);
    }
  }

  public String getProjectId(String projectKey) {
    var path = new UrlBuilder("/api/v2/components/projects")
      .addParam("keys", projectKey).addParam("pageSize", 1).build();
    try (var response = helper.get(path)) {
      var result = new Gson().fromJson(response.bodyAsString(), ProjectsResponse.class);
      if (result == null || result.projects() == null || result.projects().size() != 1
        || result.page() == null || !Integer.valueOf(1).equals(result.page().total())) {
        throw new IllegalStateException("Could not resolve the project. Check its key and Browse permission.");
      }
      var project = result.projects().getFirst();
      if (project == null || !projectKey.equals(project.key()) || !(project.id() instanceof String id) || id.isBlank()) {
        throw new IllegalStateException("The public project lookup returned an invalid project.");
      }
      return id;
    } catch (NotFoundException e) {
      throw new IllegalStateException("This Server does not expose the public project lookup required for intended architecture and directives. "
        + "Use a Server build with GET /api/v2/components/projects and the public Architecture APIs.", e);
    }
  }

  private record ProjectsResponse(List<Project> projects, Page page) {
  }

  private record Project(Object id, String key) {
  }

  private record Page(Integer total) {
  }

}
