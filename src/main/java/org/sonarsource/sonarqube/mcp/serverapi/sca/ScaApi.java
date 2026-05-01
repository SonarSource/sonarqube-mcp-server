/*
 * SonarQube MCP Server
 * Copyright (C) SonarSource
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
package org.sonarsource.sonarqube.mcp.serverapi.sca;

import com.google.gson.Gson;
import jakarta.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;
import org.sonarsource.sonarqube.mcp.serverapi.sca.response.DependencyRisksResponse;
import org.sonarsource.sonarqube.mcp.serverapi.sca.response.FeatureEnabledResponse;

public class ScaApi {

  public static final String DEPENDENCY_RISKS_PATH = "/sca/issues-releases";
  public static final String FEATURE_ENABLED_PATH = "/sca/feature-enabled";

  private final ServerApiHelper helper;

  public ScaApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public boolean isScaEnabled() {
    var path = new UrlBuilder(FEATURE_ENABLED_PATH)
      .addParam("organization", helper.getOrganization())
      .build();
    try (var response = helper.getApiSubdomain(path)) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, FeatureEnabledResponse.class).enabled();
    } catch (Exception e) {
      return false;
    }
  }

  public DependencyRisksResponse getDependencyRisks(String projectKey, @Nullable String branchKey, @Nullable String pullRequestKey,
    @Nullable Integer pageIndex, @Nullable Integer pageSize) {
    var path = buildPath(projectKey, branchKey, pullRequestKey, pageIndex, pageSize);
    try (var response = helper.isSonarQubeCloud() ? helper.getApiSubdomain(path) : helper.get("/api/v2" + path)) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, DependencyRisksResponse.class);
    }
  }

  private static String buildPath(String projectKey, @Nullable String branchKey, @Nullable String pullRequestKey,
    @Nullable Integer pageIndex, @Nullable Integer pageSize) {
    var builder = new UrlBuilder(DEPENDENCY_RISKS_PATH);
    builder.addParam("projectKey", projectKey);
    builder.addParam("branchKey", branchKey);
    builder.addParam("pullRequestKey", pullRequestKey);
    builder.addParam("pageIndex", pageIndex);
    builder.addParam("pageSize", pageSize);
    return builder.build();
  }

}
