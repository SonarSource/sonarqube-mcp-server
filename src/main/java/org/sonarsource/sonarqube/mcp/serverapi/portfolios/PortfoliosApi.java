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
package org.sonarsource.sonarqube.mcp.serverapi.portfolios;

import com.google.gson.Gson;
import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;
import org.sonarsource.sonarqube.mcp.serverapi.portfolios.response.ListResponse;

public class PortfoliosApi {

  // SonarQube Server
  public static final String VIEWS_SEARCH_PATH = "/api/views/search";
  // SonarQube Cloud
  public static final String PORTFOLIOS_PATH = "/enterprises/portfolios";

  private final ServerApiHelper helper;
  private final String organization;

  public PortfoliosApi(ServerApiHelper helper, @Nullable String organization) {
    this.helper = helper;
    this.organization = organization;
  }

  public ListResponse list(@Nullable String enterpriseId, @Nullable String query, @Nullable Boolean favorite, 
    @Nullable Boolean draft, @Nullable Integer pageIndex, @Nullable Integer pageSize) {
    
    if (organization != null) {
      // SonarQube Cloud - uses api.sonarcloud.io subdomain
      try (var response = helper.getApiSubdomain(buildCloudPath(enterpriseId, query, favorite, draft, pageIndex, pageSize))) {
        var responseStr = response.bodyAsString();
        return new Gson().fromJson(responseStr, ListResponse.class);
      }
    } else {
      // SonarQube Server
      try (var response = helper.get(buildServerPath(query, favorite, pageIndex, pageSize))) {
        var responseStr = response.bodyAsString();
        return new Gson().fromJson(responseStr, ListResponse.class);
      }
    }
  }

  private static String buildCloudPath(@Nullable String enterpriseId, @Nullable String query, @Nullable Boolean favorite,
    @Nullable Boolean draft, @Nullable Integer pageIndex, @Nullable Integer pageSize) {
    return new UrlBuilder(PORTFOLIOS_PATH)
      .addParam("enterpriseId", enterpriseId)
      .addParam("q", query)
      .addParam("favorite", favorite)
      .addParam("draft", draft)
      .addParam("pageIndex", pageIndex)
      .addParam("pageSize", pageSize)
      .build();
  }

  private static String buildServerPath(@Nullable String query, @Nullable Boolean favorite, 
    @Nullable Integer pageIndex, @Nullable Integer pageSize) {
    return new UrlBuilder(VIEWS_SEARCH_PATH)
      .addParam("q", query)
      .addParam("onlyFavorites", favorite)
      .addParam("p", pageIndex)
      .addParam("ps", pageSize)
      // VW = portfolios
      .addParam("qualifiers", "VW")
      .build();
  }

}
