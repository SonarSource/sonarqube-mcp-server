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
package org.sonar.mcp.serverapi.languages;

import com.google.gson.Gson;
import javax.annotation.Nullable;
import org.sonar.mcp.serverapi.ServerApiHelper;
import org.sonar.mcp.serverapi.UrlBuilder;
import org.sonar.mcp.serverapi.languages.response.ListResponse;

public class LanguagesApi {

  public static final String LIST_PATH = "/api/languages/list";

  private final ServerApiHelper helper;

  public LanguagesApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public ListResponse list(@Nullable String query) {
    try (var response = helper.get(buildListPath(query))) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, ListResponse.class);
    }
  }

  private static String buildListPath(@Nullable String query) {
    return new UrlBuilder(LIST_PATH)
      .addParam("q", query)
      .build();
  }

}
