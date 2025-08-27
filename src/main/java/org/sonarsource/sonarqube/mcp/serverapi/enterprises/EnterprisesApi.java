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

public class EnterprisesApi {

  public static final String ENTERPRISES_PATH = "/enterprises/enterprises";

  private final ServerApiHelper helper;

  public EnterprisesApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public ListResponse list(@Nullable String enterpriseKey) {
    try (var response = helper.getApiSubdomain(buildPath(enterpriseKey))) {
      // The API returns a direct array, not wrapped in an object
      var responseStr = response.bodyAsString();
      Type enterpriseListType = new TypeToken<List<ListResponse.Enterprise>>(){}.getType();
      List<ListResponse.Enterprise> enterprises = new Gson().fromJson(responseStr, enterpriseListType);
      
      return new ListResponse(enterprises);
    }
  }

  private static String buildPath(@Nullable String enterpriseKey) {
    return new UrlBuilder(ENTERPRISES_PATH)
      .addParam("enterpriseKey", enterpriseKey)
      .build();
  }

}
