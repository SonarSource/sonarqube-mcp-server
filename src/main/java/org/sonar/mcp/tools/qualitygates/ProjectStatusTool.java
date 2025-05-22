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
package org.sonar.mcp.tools.qualitygates;

import java.util.Map;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.sonar.mcp.serverapi.ServerApi;
import org.sonar.mcp.serverapi.exception.NotFoundException;
import org.sonar.mcp.serverapi.qualitygates.QualityGatesApi;
import org.sonar.mcp.tools.SchemaToolBuilder;
import org.sonar.mcp.tools.Tool;

public class ProjectStatusTool extends Tool {

  public static final String TOOL_NAME = "get_quality_gate_status_for_project";
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
  public Tool.Result execute(Map<String, Object> arguments) {
    if (!serverApi.isAuthenticationSet()) {
      return Tool.Result.failure("Not connected to SonarQube Cloud, please provide 'SONARQUBE_CLOUD_TOKEN' and 'SONARQUBE_CLOUD_ORG'");
    }

    String analysisId = null;
    if (arguments.containsKey(ANALYSIS_ID_PROPERTY)) {
      analysisId = (String) arguments.get(ANALYSIS_ID_PROPERTY);
    }
    String branch = null;
    if (arguments.containsKey(BRANCH_PROPERTY)) {
      branch = (String) arguments.get(BRANCH_PROPERTY);
    }
    String projectId = null;
    if (arguments.containsKey(PROJECT_ID_PROPERTY)) {
      projectId = (String) arguments.get(PROJECT_ID_PROPERTY);
    }
    String projectKey = null;
    if (arguments.containsKey(PROJECT_KEY_PROPERTY)) {
      projectKey = (String) arguments.get(PROJECT_KEY_PROPERTY);
    }
    String pullRequest = null;
    if (arguments.containsKey(PULL_REQUEST_PROPERTY)) {
      pullRequest = (String) arguments.get(PULL_REQUEST_PROPERTY);
    }

    if (analysisId == null && projectId == null && projectKey == null) {
      return Tool.Result.failure("Either '%s', '%s' or '%s' must be provided".formatted(ANALYSIS_ID_PROPERTY, PROJECT_ID_PROPERTY, PROJECT_KEY_PROPERTY));
    }

    if (projectId != null && (branch != null || pullRequest != null)) {
      return Tool.Result.failure("Project ID doesn't work with branches or pull requests");
    }

    var text = new StringBuilder();
    try {
      var projectStatus = serverApi.qualityGatesApi().getProjectQualityGateStatus(analysisId, branch, projectId, projectKey, pullRequest);
      text.append(buildResponseFromProjectStatus(projectStatus));
    } catch (Exception e) {
      String message;
      if (e instanceof NotFoundException) {
        message = "Make sure your token is valid.";
      } else {
        message = e instanceof ResponseErrorException responseErrorException ? responseErrorException.getResponseError().getMessage() : e.getMessage();
      }
      return Tool.Result.failure("Failed to fetch project status: " + message);
    }

    return Tool.Result.success(text.toString());
  }

  private static String buildResponseFromProjectStatus(QualityGatesApi.ProjectStatusResponse projectStatus) {
    var stringBuilder = new StringBuilder();
    var status = projectStatus.projectStatus();

    stringBuilder.append("The Quality Gate status is ").append(status.status()).append(". Here are the following conditions:\n");

    for (var condition : status.conditions()) {
      stringBuilder.append(condition.metricKey()).append(" is ").append(condition.status())
        .append(", the threshold is ").append(condition.errorThreshold())
        .append(" and the actual value is ").append(condition.actualValue()).append("\n");
    }

    return stringBuilder.toString();
  }
}
