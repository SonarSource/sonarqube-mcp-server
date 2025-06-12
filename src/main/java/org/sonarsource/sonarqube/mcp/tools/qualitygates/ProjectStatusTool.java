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
package org.sonarsource.sonarqube.mcp.tools.qualitygates;

import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.qualitygates.response.ProjectStatusResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public class ProjectStatusTool extends Tool {

  public static final String TOOL_NAME = "get_project_quality_gate_status";
  public static final String ANALYSIS_ID_PROPERTY = "analysisId";
  public static final String BRANCH_PROPERTY = "branch";
  public static final String PROJECT_ID_PROPERTY = "projectId";
  public static final String PROJECT_KEY_PROPERTY = "projectKey";
  public static final String PULL_REQUEST_PROPERTY = "pullRequest";

  private final ServerApi serverApi;

  public ProjectStatusTool(ServerApi serverApi) {
    super(new SchemaToolBuilder()
      .setName(TOOL_NAME)
      .setDescription("""
        Get the Quality Gate Status for the project. Either '%s', '%s' or '%s' must be provided.
        """.formatted(ANALYSIS_ID_PROPERTY, PROJECT_ID_PROPERTY, PROJECT_KEY_PROPERTY))
      .addStringProperty(ANALYSIS_ID_PROPERTY, "The optional analysis ID to get the status for, for example 'AU-TpxcA-iU5OvuD2FL1'")
      .addStringProperty(BRANCH_PROPERTY, "The optional branch key to get the status for, for example 'feature/my_branch'")
      .addStringProperty(PROJECT_ID_PROPERTY, """
        The optional project ID to get the status for, for example 'AU-Tpxb--iU5OvuD2FLy'. Doesn't work with branches or pull requests.
        """)
      .addStringProperty(PROJECT_KEY_PROPERTY, "The optional project key to get the status for, for example 'my_project'")
      .addStringProperty(PULL_REQUEST_PROPERTY, "The optional pull request ID to get the status for, for example '5461'")
      .build());
    this.serverApi = serverApi;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    if (!serverApi.isAuthenticationSet()) {
      return Tool.Result.failure("Not connected to SonarQube, please provide valid credentials");
    }

    var analysisId = arguments.getOptionalString(ANALYSIS_ID_PROPERTY);
    var branch = arguments.getOptionalString(BRANCH_PROPERTY);
    var projectId = arguments.getOptionalString(PROJECT_ID_PROPERTY);
    var projectKey = arguments.getOptionalString(PROJECT_KEY_PROPERTY);
    var pullRequest = arguments.getOptionalString(PULL_REQUEST_PROPERTY);

    if (analysisId == null && projectId == null && projectKey == null) {
      return Tool.Result.failure("Either '%s', '%s' or '%s' must be provided".formatted(ANALYSIS_ID_PROPERTY, PROJECT_ID_PROPERTY, PROJECT_KEY_PROPERTY));
    }

    if (projectId != null && (branch != null || pullRequest != null)) {
      return Tool.Result.failure("Project ID doesn't work with branches or pull requests");
    }

    var projectStatus = serverApi.qualityGatesApi().getProjectQualityGateStatus(analysisId, branch, projectId, projectKey, pullRequest);
    return Tool.Result.success(buildResponseFromProjectStatus(projectStatus));
  }

  private static String buildResponseFromProjectStatus(ProjectStatusResponse projectStatus) {
    var stringBuilder = new StringBuilder();
    var status = projectStatus.projectStatus();

    stringBuilder.append("The Quality Gate status is ").append(status.status()).append(". Here are the following conditions:\n");

    for (var condition : status.conditions()) {
      stringBuilder.append(condition.metricKey()).append(" is ").append(condition.status())
        .append(", the threshold is ").append(condition.errorThreshold())
        .append(" and the actual value is ").append(condition.actualValue()).append("\n");
    }

    return stringBuilder.toString().trim();
  }

}
