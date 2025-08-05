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
package org.sonarsource.sonarqube.mcp.serverapi.sca;

import com.google.gson.Gson;
import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;
import org.sonarsource.sonarqube.mcp.serverapi.sca.response.IssuesReleasesResponse;

public class ScaApi {

  public static final String ISSUES_RELEASES_PATH = "/api/v2/sca/issues-releases";

  private final ServerApiHelper helper;

  public ScaApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public IssuesReleasesResponse getIssuesReleases(@Nullable String projectKey, @Nullable String branchKey, @Nullable String pullRequestKey) {
    try (var response = helper.get(buildPath(projectKey, branchKey, pullRequestKey))) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, IssuesReleasesResponse.class);
    }
  }

  private String buildPath(@Nullable String projectKey, @Nullable String branchKey, @Nullable String pullRequestKey) {
    var builder = new UrlBuilder(ISSUES_RELEASES_PATH)
      .addParam("projectKey", projectKey)
      .addParam("branchKey", branchKey)
      .addParam("pullRequestKey", pullRequestKey);
    return builder.build();
  }
}