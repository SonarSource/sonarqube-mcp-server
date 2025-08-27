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
package org.sonarsource.sonarqube.mcp.serverapi.enterprises;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;
import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;
import org.sonarsource.sonarqube.mcp.serverapi.enterprises.response.ListResponse;
import org.sonarsource.sonarqube.mcp.serverapi.enterprises.response.PortfoliosResponse;

public class EnterprisesApi {

  public static final String ENTERPRISES_PATH = "/enterprises/enterprises";
  public static final String PORTFOLIOS_PATH = "/enterprises/portfolios";

  private final ServerApiHelper helper;

  public EnterprisesApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public ListResponse listEnterprises(@Nullable String enterpriseKey) {
    try (var response = helper.getApiSubdomain(buildEnterprisesPath(enterpriseKey))) {
      // The API returns a direct array, not wrapped in an object
      var responseStr = response.bodyAsString();
      var enterpriseListType = new TypeToken<List<ListResponse.Enterprise>>(){}.getType();
      List<ListResponse.Enterprise> enterprises = new Gson().fromJson(responseStr, enterpriseListType);
      
      return new ListResponse(enterprises);
    }
  }

  public PortfoliosResponse listPortfolios(@Nullable String enterpriseId, @Nullable String query, @Nullable Boolean favorite,
    @Nullable Boolean draft, @Nullable Integer pageIndex, @Nullable Integer pageSize) {
    try (var response = helper.getApiSubdomain(buildPortfoliosPath(enterpriseId, query, favorite, draft, pageIndex, pageSize))) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, PortfoliosResponse.class);
    }
  }

  private static String buildEnterprisesPath(@Nullable String enterpriseKey) {
    return new UrlBuilder(ENTERPRISES_PATH)
      .addParam("enterpriseKey", enterpriseKey)
      .build();
  }

  private static String buildPortfoliosPath(@Nullable String enterpriseId, @Nullable String query, @Nullable Boolean favorite,
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

}
