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
package org.sonarsource.sonarqube.mcp.serverapi.rules;

import com.google.gson.Gson;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;
import org.sonarsource.sonarqube.mcp.serverapi.rules.response.SearchResponse;
import org.sonarsource.sonarqube.mcp.serverapi.rules.response.ShowResponse;

public class RulesApi {

  public static final String SHOW_PATH = "/api/rules/show";
  public static final String SEARCH_PATH = "/api/rules/search";

  private final ServerApiHelper helper;

  public RulesApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public ShowResponse showRule(String ruleKey) {
    try (var response = helper.get(buildPath(ruleKey))) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, ShowResponse.class);
    }
  }

  private String buildPath(String ruleKey) {
    var builder = new UrlBuilder(SHOW_PATH)
      .addParam("key", ruleKey)
      .addParam("organization", helper.getOrganization());
    return builder.build();
  }

  public SearchResponse search(String qualityProfileKey, int page) {
    var url = new UrlBuilder(SEARCH_PATH)
      .addParam("qprofile", qualityProfileKey)
      .addParam("organization", helper.getOrganization())
      .addParam("activation", "true")
      .addParam("f", "templateKey,actives")
      .addParam("p", page)
      .build();
    try (var response = helper.get(url)) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, SearchResponse.class);
    }
  }
}
