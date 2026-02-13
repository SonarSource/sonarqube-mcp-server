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
package org.sonarsource.sonarqube.mcp.serverapi.hotspots;

import com.google.gson.Gson;
import java.util.List;
import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;
import org.sonarsource.sonarqube.mcp.serverapi.hotspots.response.SearchResponse;
import org.sonarsource.sonarqube.mcp.serverapi.hotspots.response.ShowResponse;

import static org.sonarsource.sonarlint.core.http.HttpClient.FORM_URL_ENCODED_CONTENT_TYPE;
import static org.sonarsource.sonarlint.core.serverapi.UrlUtils.urlEncode;

public class HotspotsApi {

  public static final String SEARCH_PATH = "/api/hotspots/search";
  public static final String SHOW_PATH = "/api/hotspots/show";
  public static final String CHANGE_STATUS_PATH = "/api/hotspots/change_status";

  private final ServerApiHelper helper;

  public HotspotsApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public record SearchParams(
    @Nullable String projectKey,
    @Nullable String branch,
    @Nullable String pullRequest,
    @Nullable List<String> files,
    @Nullable List<String> hotspots,
    @Nullable String status,
    @Nullable String resolution,
    @Nullable Boolean sinceLeakPeriod,
    @Nullable Boolean onlyMine,
    @Nullable Integer page,
    @Nullable Integer pageSize
  ) {}

  public SearchResponse search(SearchParams params) {
    try (var response = helper.get(buildSearchPath(params))) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, SearchResponse.class);
    }
  }

  public ShowResponse show(String hotspotKey) {
    var path = new UrlBuilder(SHOW_PATH)
      .addParam("hotspot", hotspotKey)
      .build();
    try (var response = helper.get(path)) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, ShowResponse.class);
    }
  }

  public void changeStatus(String hotspotKey, String status, @Nullable String resolution, @Nullable String comment) {
    var body = new StringBuilder("hotspot=" + urlEncode(hotspotKey) + "&status=" + urlEncode(status));
    if (resolution != null && !resolution.isEmpty()) {
      body.append("&resolution=").append(urlEncode(resolution));
    }
    if (comment != null && !comment.isEmpty()) {
      body.append("&comment=").append(urlEncode(comment));
    }
    try (var ignored = helper.post(CHANGE_STATUS_PATH, FORM_URL_ENCODED_CONTENT_TYPE, body.toString())) {
      // Response is closed automatically
    }
  }

  private static String buildSearchPath(SearchParams params) {
    var builder = new UrlBuilder(SEARCH_PATH)
      .addParam("projectKey", params.projectKey())
      .addParam("branch", params.branch())
      .addParam("pullRequest", params.pullRequest())
      .addParam("files", params.files())
      .addParam("hotspots", params.hotspots())
      .addParam("status", params.status())
      .addParam("resolution", params.resolution())
      .addParam("sinceLeakPeriod", params.sinceLeakPeriod())
      .addParam("onlyMine", params.onlyMine())
      .addParam("p", params.page())
      .addParam("ps", params.pageSize());
    return builder.build();
  }

}
