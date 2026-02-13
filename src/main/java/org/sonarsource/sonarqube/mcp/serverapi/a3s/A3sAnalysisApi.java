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
package org.sonarsource.sonarqube.mcp.serverapi.a3s;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.a3s.request.AnalysisCreationRequest;
import org.sonarsource.sonarqube.mcp.serverapi.a3s.response.AnalysisResponse;

public class A3sAnalysisApi {

  public static final String ANALYSES_PATH = "/a3s-analysis/analyses";
  private static final String JSON_CONTENT_TYPE = "application/json";
  private static final Gson GSON = new GsonBuilder()
    .serializeNulls()
    .create();

  private final ServerApiHelper helper;

  public A3sAnalysisApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public AnalysisResponse analyze(AnalysisCreationRequest request) {
    var requestBody = GSON.toJson(request);
    try (var response = helper.postApiSubdomain(ANALYSES_PATH, JSON_CONTENT_TYPE, requestBody)) {
      return GSON.fromJson(response.bodyAsString(), AnalysisResponse.class);
    }
  }
}
