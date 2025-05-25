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
package org.sonar.mcp.serverapi.badges;

import javax.annotation.Nullable;
import org.sonar.mcp.serverapi.ServerApiHelper;
import org.sonar.mcp.serverapi.UrlBuilder;

public class ProjectBadgesApi {

  public static final String MEASURE_PATH = "/api/project_badges/measure";
  public static final String AI_CODE_ASSURANCE_PATH = "/api/project_badges/ai_code_assurance";
  public static final String QUALITY_GATE_PATH = "/api/project_badges/quality_gate";

  private final ServerApiHelper helper;

  public ProjectBadgesApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public String getMeasureBadge(String project, String metric, @Nullable String branch, @Nullable String token) {
    try (var response = helper.get(buildMeasurePath(project, metric, branch, token))) {
      return response.bodyAsString();
    }
  }

  public String getAiCodeAssuranceBadge(String project, @Nullable String token) {
    try (var response = helper.get(buildAiCodeAssurancePath(project, token))) {
      return response.bodyAsString();
    }
  }

  public String getQualityGateBadge(String project, @Nullable String branch, @Nullable String token) {
    try (var response = helper.get(buildQualityGatePath(project, branch, token))) {
      return response.bodyAsString();
    }
  }

  private static String buildMeasurePath(String project, String metric, @Nullable String branch, @Nullable String token) {
    return new UrlBuilder(MEASURE_PATH)
      .addParam("project", project)
      .addParam("metric", metric)
      .addParam("branch", branch)
      .addParam("token", token)
      .build();
  }

  private static String buildAiCodeAssurancePath(String project, @Nullable String token) {
    return new UrlBuilder(AI_CODE_ASSURANCE_PATH)
      .addParam("project", project)
      .addParam("token", token)
      .build();
  }

  private static String buildQualityGatePath(String project, @Nullable String branch, @Nullable String token) {
    return new UrlBuilder(QUALITY_GATE_PATH)
      .addParam("project", project)
      .addParam("branch", branch)
      .addParam("token", token)
      .build();
  }

} 
