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
import java.util.List;

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

  public record SearchParams(
    @Nullable String projectKey,
    @Nullable String branchKey,
    @Nullable String pullRequestKey,
    @Nullable List<String> severities,
    @Nullable List<String> qualities,
    @Nullable List<String> statuses,
    @Nullable Integer pageIndex,
    @Nullable Integer pageSize
  ) {}

  public DependencyRisksResponse getDependencyRisks(SearchParams params) {
    var path = new UrlBuilder(DEPENDENCY_RISKS_PATH)
      .addParam("projectKey", params.projectKey())
      .addParam("branchKey", params.branchKey())
      .addParam("pullRequestKey", params.pullRequestKey())
      .addParam("severities", params.severities())
      .addParam("qualities", params.qualities())
      .addParam("statuses", params.statuses())
      .addParam("pageIndex", params.pageIndex())
      .addParam("pageSize", params.pageSize())
      .build();
    try (var response = helper.isSonarQubeCloud() ? helper.getApiSubdomain(path) : helper.get("/api/v2" + path)) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, DependencyRisksResponse.class);
    }
  }
}
