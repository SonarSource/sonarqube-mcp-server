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
package org.sonarsource.sonarqube.mcp.serverapi.metrics;

import com.google.gson.Gson;
import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;
import org.sonarsource.sonarqube.mcp.serverapi.metrics.response.SearchMetricsResponse;

public class MetricsApi {

  public static final String SEARCH_PATH = "/api/metrics/search";

  private final ServerApiHelper helper;

  public MetricsApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public SearchMetricsResponse searchMetrics(@Nullable Integer page, @Nullable Integer pageSize) {
    try (var response = helper.get(buildSearchPath(page, pageSize))) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, SearchMetricsResponse.class);
    }
  }

  private static String buildSearchPath(@Nullable Integer page, @Nullable Integer pageSize) {
    return new UrlBuilder(SEARCH_PATH)
      .addParam("p", page)
      .addParam("ps", pageSize)
      .build();
  }

}
