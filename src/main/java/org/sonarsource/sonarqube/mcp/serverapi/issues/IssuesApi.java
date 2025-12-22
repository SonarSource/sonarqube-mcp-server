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
package org.sonarsource.sonarqube.mcp.serverapi.issues;

import com.google.gson.Gson;
import java.util.List;
import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;
import org.sonarsource.sonarqube.mcp.serverapi.issues.response.SearchResponse;

import static org.sonarsource.sonarlint.core.http.HttpClient.FORM_URL_ENCODED_CONTENT_TYPE;
import static org.sonarsource.sonarlint.core.serverapi.UrlUtils.urlEncode;

public class IssuesApi {

  public static final String SEARCH_PATH = "/api/issues/search";

  private final ServerApiHelper helper;

  public IssuesApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public SearchResponse search(@Nullable List<String> projects, @Nullable String branch, @Nullable List<String> files, @Nullable String pullRequestId,
    @Nullable List<String> severities, @Nullable List<String> issueStatuses, @Nullable Integer page, @Nullable Integer pageSize) {
    try (var response = helper.get(buildPath(projects, branch, files, pullRequestId, severities, issueStatuses, page, pageSize))) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, SearchResponse.class);
    }
  }

  public void doTransition(String issueKey, Transition transition) {
    var body = "issue=" + urlEncode(issueKey) + "&transition=" + urlEncode(transition.getStatus());
    var response = helper.post("/api/issues/do_transition", FORM_URL_ENCODED_CONTENT_TYPE, body);
    response.close();
  }

  private String buildPath(@Nullable List<String> projects, @Nullable String branch, @Nullable List<String> files, @Nullable String pullRequestId,
    @Nullable List<String> severities, @Nullable List<String> issueStatuses, @Nullable Integer page, @Nullable Integer pageSize) {
    var builder = new UrlBuilder(SEARCH_PATH)
      .addParam("projects", projects)
      .addParam("branch", branch)
      .addParam("files", files)
      .addParam("pullRequest", pullRequestId)
      .addParam("impactSeverities", severities)
      .addParam("issueStatuses", issueStatuses)
      .addParam("p", page)
      .addParam("ps", pageSize)
      .addParam("organization", helper.getOrganization());
    return builder.build();
  }

}
