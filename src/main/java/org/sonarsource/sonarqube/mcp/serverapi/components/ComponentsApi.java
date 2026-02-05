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
package org.sonarsource.sonarqube.mcp.serverapi.components;

import com.google.gson.Gson;
import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;
import org.sonarsource.sonarqube.mcp.serverapi.components.response.SearchResponse;

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

}
