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
package org.sonar.mcp.tools.badges;

import org.sonar.mcp.serverapi.ServerApi;
import org.sonar.mcp.tools.SchemaToolBuilder;
import org.sonar.mcp.tools.Tool;

public class GetProjectMeasureBadgeTool extends Tool {

  public static final String TOOL_NAME = "get_project_measure_badge";
  public static final String PROJECT_PROPERTY = "project";
  public static final String METRIC_PROPERTY = "metric";
  public static final String BRANCH_PROPERTY = "branch";
  public static final String TOKEN_PROPERTY = "token";

  private final ServerApi serverApi;

  public GetProjectMeasureBadgeTool(ServerApi serverApi) {
    super(new SchemaToolBuilder()
      .setName(TOOL_NAME)
      .setDescription("Get an SVG badge for a project's measure")
      .addRequiredStringProperty(PROJECT_PROPERTY, "Project or application key")
      .addRequiredStringProperty(METRIC_PROPERTY, "Metric key (e.g. 'alert_status', 'bugs', etc)")
      .addStringProperty(BRANCH_PROPERTY, "Optional branch key")
      .addStringProperty(TOKEN_PROPERTY, "Optional security token required for private projects")
      .build());
    this.serverApi = serverApi;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    if (!serverApi.isAuthenticationSet()) {
      return Tool.Result.failure("Not connected to SonarQube Cloud, please provide 'SONARQUBE_CLOUD_TOKEN' and 'SONARQUBE_CLOUD_ORG'");
    }

    var project = arguments.getStringOrThrow(PROJECT_PROPERTY);
    var metric = arguments.getStringOrThrow(METRIC_PROPERTY);
    var branch = arguments.getOptionalString(BRANCH_PROPERTY);
    var token = arguments.getOptionalString(TOKEN_PROPERTY);

    var badge = serverApi.projectBadgesApi().getMeasureBadge(project, metric, branch, token);
    return Tool.Result.success(badge);
  }

} 
