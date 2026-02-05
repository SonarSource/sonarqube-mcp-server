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
package org.sonarsource.sonarqube.mcp.serverapi.duplications;

import com.google.gson.Gson;
import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;
import org.sonarsource.sonarqube.mcp.serverapi.duplications.response.DuplicationsResponse;

public class DuplicationsApi {

  public static final String DUPLICATIONS_SHOW_PATH = "/api/duplications/show";

  private final ServerApiHelper helper;

  public DuplicationsApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public DuplicationsResponse getDuplications(String key, @Nullable String branch, @Nullable String pullRequest) {
    var url = new UrlBuilder(DUPLICATIONS_SHOW_PATH)
      .addParam("key", key)
      .addParam("branch", branch)
      .addParam("pullRequest", pullRequest)
      .build();

    try (var response = helper.get(url)) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, DuplicationsResponse.class);
    }
  }

}
