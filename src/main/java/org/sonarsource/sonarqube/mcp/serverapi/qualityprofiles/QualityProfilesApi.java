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
package org.sonarsource.sonarqube.mcp.serverapi.qualityprofiles;

import com.google.gson.Gson;
import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;
import org.sonarsource.sonarqube.mcp.serverapi.qualityprofiles.response.SearchResponse;

public class QualityProfilesApi {

  public static final String SEARCH_PATH = "/api/qualityprofiles/search";

  private final ServerApiHelper helper;

  public QualityProfilesApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public SearchResponse getQualityProfiles(@Nullable String projectKey) {
    var url = new UrlBuilder(SEARCH_PATH);
    url.addParam("organization", helper.getOrganization());
    if (projectKey != null) {
      url.addParam("project", projectKey);
    } else {
      url.addParam("defaults", "true");
    }
    try (var response = helper.get(url.build())) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, SearchResponse.class);
    }
  }

}
