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
package org.sonar.mcp.serverapi.components;

import com.google.gson.Gson;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.sonar.mcp.serverapi.ServerApiHelper;

public class ComponentsApi {

  public static final String COMPONENTS_SEARCH_PATH = "/api/components/search";

  private final ServerApiHelper helper;

  public ComponentsApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public SearchResponse searchProjectsInMyOrg(int page) {
    var path = new StringBuilder(COMPONENTS_SEARCH_PATH);
    var organization = helper.getOrganization();

    path.append("?p=").append(page);
    if (organization != null) {
      path.append("&organization=").append(URLEncoder.encode(organization, StandardCharsets.UTF_8));
    }

    try (var response = helper.get(path.toString())) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, SearchResponse.class);
    }
  }

  public record SearchResponse(Paging paging, List<Component> components) {
  }

  public record Paging(Integer pageIndex, Integer pageSize, Integer total) {
  }

  public record Component(String organization, String key, String qualifier, String name, String project) {
  }

}
