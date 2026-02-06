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
package org.sonarsource.sonarqube.mcp.serverapi.measures;

import com.google.gson.Gson;
import java.util.List;
import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;
import org.sonarsource.sonarqube.mcp.serverapi.measures.response.ComponentMeasuresResponse;
import org.sonarsource.sonarqube.mcp.serverapi.measures.response.ComponentTreeResponse;

public class MeasuresApi {

  public static final String COMPONENT_PATH = "/api/measures/component";
  public static final String COMPONENT_TREE_PATH = "/api/measures/component_tree";

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

  public ComponentTreeResponse getComponentTree(ComponentTreeParams params) {
    var url = new UrlBuilder(COMPONENT_TREE_PATH)
      .addParam("component", params.component())
      .addParam("branch", params.branch())
      .addParam("metricKeys", params.metricKeys())
      .addParam("pullRequest", params.pullRequest())
      .addParam("qualifiers", params.qualifiers())
      .addParam("strategy", params.strategy())
      .addParam("s", params.sort())
      .addParam("metricSort", params.metricSort())
      .addParam("asc", params.asc())
      .addParam("p", params.pageIndex())
      .addParam("ps", params.pageSize())
      .build();

    try (var response = helper.get(url)) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, ComponentTreeResponse.class);
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
