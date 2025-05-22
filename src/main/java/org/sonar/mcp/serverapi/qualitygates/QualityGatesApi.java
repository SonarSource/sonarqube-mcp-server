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
package org.sonar.mcp.serverapi.qualitygates;

import com.google.gson.Gson;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.annotation.Nullable;
import org.sonar.mcp.serverapi.ServerApiHelper;

public class QualityGatesApi {

  public static final String PROJECT_STATUS_PATH = "/api/qualitygates/project_status";

  private final ServerApiHelper helper;

  public QualityGatesApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public ProjectStatusResponse getProjectQualityGateStatus(@Nullable String analysisId, @Nullable String branchKey,
    @Nullable String projectId, @Nullable String projectKey, @Nullable String pullRequest) {
    var path = new StringBuilder(PROJECT_STATUS_PATH);
    boolean hasQueryParams = false;

    if (analysisId != null) {
      path.append("?analysisId=").append(URLEncoder.encode(analysisId, StandardCharsets.UTF_8));
      hasQueryParams = true;
    }
    if (branchKey != null) {
      path.append(hasQueryParams ? "&" : "?").append("branchKey=").append(URLEncoder.encode(branchKey, StandardCharsets.UTF_8));
      hasQueryParams = true;
    }
    if (projectId != null) {
      path.append(hasQueryParams ? "&" : "?").append("projectId=").append(URLEncoder.encode(projectId, StandardCharsets.UTF_8));
      hasQueryParams = true;
    }
    if (projectKey != null) {
      path.append(hasQueryParams ? "&" : "?").append("projectKey=").append(URLEncoder.encode(projectKey, StandardCharsets.UTF_8));
      hasQueryParams = true;
    }
    if (pullRequest != null) {
      path.append(hasQueryParams ? "&" : "?").append("pullRequest=").append(URLEncoder.encode(pullRequest, StandardCharsets.UTF_8));
    }

    try (var response = helper.get(path.toString())) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, ProjectStatusResponse.class);
    }
  }

  public record ProjectStatusResponse(ProjectStatus projectStatus) {
  }

  public record ProjectStatus(String status, boolean ignoredConditions, List<Condition> conditions) {
  }

  public record Condition(String status, String metricKey, String comparator, int periodIndex, String errorThreshold, String actualValue) {
  }

}
