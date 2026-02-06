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

  public ComponentTreeResponse getComponentTree(String component, @Nullable String branch,
    @Nullable List<String> metricKeys, @Nullable String pullRequest, @Nullable String qualifiers,
    @Nullable Integer pageSize, @Nullable Integer pageIndex, @Nullable String strategy) {
    try (var response = helper.get(buildComponentTreePath(component, branch, metricKeys, pullRequest, qualifiers, pageSize, pageIndex, strategy))) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, ComponentTreeResponse.class);
    }
  }

  private static String buildComponentTreePath(String component, @Nullable String branch,
    @Nullable List<String> metricKeys, @Nullable String pullRequest, @Nullable String qualifiers,
    @Nullable Integer pageSize, @Nullable Integer pageIndex, @Nullable String strategy) {
    return new UrlBuilder(COMPONENT_TREE_PATH)
      .addParam("component", component)
      .addParam("branch", branch)
      .addParam("metricKeys", metricKeys)
      .addParam("pullRequest", pullRequest)
      .addParam("qualifiers", qualifiers)
      .addParam("ps", pageSize)
      .addParam("p", pageIndex)
      .addParam("strategy", strategy)
      .addParam("additionalFields", "metrics")
      .build();
  }

} 
