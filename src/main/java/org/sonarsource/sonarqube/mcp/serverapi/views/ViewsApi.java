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
package org.sonarsource.sonarqube.mcp.serverapi.views;

import com.google.gson.Gson;
import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;
import org.sonarsource.sonarqube.mcp.serverapi.views.response.SearchResponse;

public class ViewsApi {

  public static final String VIEWS_SEARCH_PATH = "/api/views/search";

  private final ServerApiHelper helper;

  public ViewsApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public SearchResponse search(@Nullable String query, @Nullable Boolean onlyFavorites, 
    @Nullable Integer pageIndex, @Nullable Integer pageSize) {
    
    try (var response = helper.get(buildSearchPath(query, onlyFavorites, pageIndex, pageSize))) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, SearchResponse.class);
    }
  }

  private static String buildSearchPath(@Nullable String query, @Nullable Boolean onlyFavorites, 
    @Nullable Integer pageIndex, @Nullable Integer pageSize) {
    return new UrlBuilder(VIEWS_SEARCH_PATH)
      .addParam("q", query)
      .addParam("onlyFavorites", onlyFavorites)
      .addParam("p", pageIndex)
      .addParam("ps", pageSize)
      // VW = portfolios
      .addParam("qualifiers", "VW")
      .build();
  }

}
