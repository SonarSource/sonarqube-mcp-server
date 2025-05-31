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
package org.sonarsource.sonarqube.mcp.serverapi.measures;

import com.google.gson.Gson;
import java.util.List;
import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;
import org.sonarsource.sonarqube.mcp.serverapi.measures.response.ComponentMeasuresResponse;

public class MeasuresApi {

  public static final String COMPONENT_PATH = "/api/measures/component";

  private final ServerApiHelper helper;

  public MeasuresApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public ComponentMeasuresResponse getComponentMeasures(@Nullable String component, @Nullable String branch, 
                                                       @Nullable List<String> metricKeys, @Nullable String pullRequest) {
    try (var response = helper.get(buildPath(component, branch, metricKeys, pullRequest))) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, ComponentMeasuresResponse.class);
    }
  }

  private static String buildPath(@Nullable String component, @Nullable String branch, 
                                 @Nullable List<String> metricKeys, @Nullable String pullRequest) {
    return new UrlBuilder(COMPONENT_PATH)
      .addParam("component", component)
      .addParam("branch", branch)
      .addParam("metricKeys", metricKeys)
      .addParam("pullRequest", pullRequest)
      .addParam("additionalFields", "metrics")
      .build();
  }

} 
