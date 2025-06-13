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
package org.sonarsource.sonarqube.mcp.serverapi.system;

import com.google.gson.Gson;
import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;
import org.sonarsource.sonarqube.mcp.serverapi.system.response.HealthResponse;
import org.sonarsource.sonarqube.mcp.serverapi.system.response.InfoResponse;
import org.sonarsource.sonarqube.mcp.serverapi.system.response.StatusResponse;

public class SystemApi {

  public static final String HEALTH_PATH = "/api/system/health";
  public static final String INFO_PATH = "/api/system/info";
  public static final String LOGS_PATH = "/api/system/logs";
  public static final String PING_PATH = "/api/system/ping";
  public static final String STATUS_PATH = "/api/system/status";

  private final ServerApiHelper helper;

  public SystemApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public HealthResponse getHealth() {
    try (var response = helper.get(HEALTH_PATH)) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, HealthResponse.class);
    }
  }

  public InfoResponse getInfo() {
    try (var response = helper.get(INFO_PATH)) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, InfoResponse.class);
    }
  }

  public String getLogs(@Nullable String name) {
    try (var response = helper.get(buildLogsPath(name))) {
      return response.bodyAsString();
    }
  }

  public String getPing() {
    try (var response = helper.getAnonymous(PING_PATH)) {
      return response.bodyAsString();
    }
  }

  public StatusResponse getStatus() {
    try (var response = helper.getAnonymous(STATUS_PATH)) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, StatusResponse.class);
    }
  }

  private static String buildLogsPath(@Nullable String name) {
    var builder = new UrlBuilder(LOGS_PATH);
    if (name != null) {
      builder.addParam("name", name);
    }
    return builder.build();
  }

}
