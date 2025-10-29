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
package org.sonarsource.sonarqube.mcp.serverapi.webhooks;

import com.google.gson.Gson;
import java.util.ArrayList;
import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;
import org.sonarsource.sonarqube.mcp.serverapi.webhooks.response.CreateResponse;
import org.sonarsource.sonarqube.mcp.serverapi.webhooks.response.ListResponse;

import static org.sonarsource.sonarlint.core.serverapi.UrlUtils.urlEncode;

public class WebhooksApi {

  public static final String CREATE_PATH = "/api/webhooks/create";
  public static final String LIST_PATH = "/api/webhooks/list";

  private final ServerApiHelper helper;

  public WebhooksApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public CreateResponse createWebhook(String name, String url, @Nullable String project, @Nullable String secret) {
    var path = buildPath();
    var body = buildRequestBody(name, url, project, secret);
    try (var response = helper.post(path, "application/x-www-form-urlencoded", body)) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, CreateResponse.class);
    }
  }

  public ListResponse listWebhooks(@Nullable String project) {
    var path = buildListPath(project);
    try (var response = helper.get(path)) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, ListResponse.class);
    }
  }

  private String buildPath() {
    var builder = new UrlBuilder(CREATE_PATH)
      .addParam("organization", helper.getOrganization());
    return builder.build();
  }

  private String buildListPath(@Nullable String project) {
    var builder = new UrlBuilder(LIST_PATH)
      .addParam("organization", helper.getOrganization())
      .addParam("project", project);
    return builder.build();
  }

  private static String buildRequestBody(String name, String url, @Nullable String project, @Nullable String secret) {
    var params = new ArrayList<String>();
    params.add("name=" + urlEncode(name));
    params.add("url=" + urlEncode(url));
    if (project != null) {
      params.add("project=" + urlEncode(project));
    }
    if (secret != null) {
      params.add("secret=" + urlEncode(secret));
    }
    return String.join("&", params);
  }

}
