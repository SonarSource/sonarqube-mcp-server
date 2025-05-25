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
import javax.annotation.Nullable;
import org.sonar.mcp.serverapi.ServerApiHelper;
import org.sonar.mcp.serverapi.UrlBuilder;
import org.sonar.mcp.serverapi.qualitygates.response.ListResponse;
import org.sonar.mcp.serverapi.qualitygates.response.ProjectStatusResponse;

public class QualityGatesApi {

  public static final String PROJECT_STATUS_PATH = "/api/qualitygates/project_status";
  public static final String LIST_PATH = "/api/qualitygates/list";

  private final ServerApiHelper helper;

  public QualityGatesApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public ProjectStatusResponse getProjectQualityGateStatus(@Nullable String analysisId, @Nullable String branchKey,
    @Nullable String projectId, @Nullable String projectKey, @Nullable String pullRequest) {
    try (var response = helper.get(buildStatusPath(analysisId, branchKey, projectId, projectKey, pullRequest))) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, ProjectStatusResponse.class);
    }
  }

  private static String buildStatusPath(@Nullable String analysisId, @Nullable String branchKey,
    @Nullable String projectId, @Nullable String projectKey, @Nullable String pullRequest) {
    return new UrlBuilder(PROJECT_STATUS_PATH)
      .addParam("analysisId", analysisId)
      .addParam("branchKey", branchKey)
      .addParam("projectId", projectId)
      .addParam("projectKey", projectKey)
      .addParam("pullRequest", pullRequest)
      .build();
  }

  public ListResponse list() {
    try (var response = helper.get(buildListPath(helper.getOrganization()))) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, ListResponse.class);
    }
  }

  private static String buildListPath(@Nullable String organization) {
    return new UrlBuilder(LIST_PATH)
      .addParam("organization", organization)
      .build();
  }

}
