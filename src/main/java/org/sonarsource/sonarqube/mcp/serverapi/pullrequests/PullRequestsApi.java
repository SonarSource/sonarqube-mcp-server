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
package org.sonarsource.sonarqube.mcp.serverapi.pullrequests;

import com.google.gson.Gson;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;
import org.sonarsource.sonarqube.mcp.serverapi.pullrequests.response.PullRequestsListResponse;

public class PullRequestsApi {

  public static final String PULL_REQUESTS_LIST_PATH = "/api/project_pull_requests/list";

  private final ServerApiHelper helper;

  public PullRequestsApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public PullRequestsListResponse listPullRequests(String projectKey) {
    var url = new UrlBuilder(PULL_REQUESTS_LIST_PATH)
      .addParam("project", projectKey)
      .build();

    try (var response = helper.get(url)) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, PullRequestsListResponse.class);
    }
  }

}
