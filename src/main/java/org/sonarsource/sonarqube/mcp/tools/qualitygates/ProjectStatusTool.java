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
package org.sonarsource.sonarqube.mcp.tools.qualitygates;

import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
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

  private final ServerApiProvider serverApiProvider;

  public ProjectStatusTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(ProjectStatusToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Get SonarQube Project Quality Gate Status")
      .setDescription("""
        Get the Quality Gate Status for the SonarQube project. Either '%s', '%s' or '%s' must be provided.
        """.formatted(ANALYSIS_ID_PROPERTY, PROJECT_ID_PROPERTY, PROJECT_KEY_PROPERTY))
      .addStringProperty(ANALYSIS_ID_PROPERTY, "The optional analysis ID to get the status for, for example 'AU-TpxcA-iU5OvuD2FL1'")
      .addStringProperty(BRANCH_PROPERTY, "The optional branch key to get the status for, for example 'feature/my_branch'")
      .addStringProperty(PROJECT_ID_PROPERTY, """
        The optional project ID to get the status for, for example 'AU-Tpxb--iU5OvuD2FLy'. Doesn't work with branches or pull requests.
        """)
      .addStringProperty(PROJECT_KEY_PROPERTY, "The optional project key to get the status for, for example 'my_project'")
      .addStringProperty(PULL_REQUEST_PROPERTY, "The optional pull request ID to get the status for, for example '5461'")
      .setReadOnlyHint()
      .build());
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
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

    var projectStatus = serverApiProvider.get().qualityGatesApi().getProjectQualityGateStatus(analysisId, branch, projectId, projectKey, pullRequest);
    var toolResponse = buildStructuredContent(projectStatus);
    return Tool.Result.success(toolResponse);
  }

  private static ProjectStatusToolResponse buildStructuredContent(ProjectStatusResponse projectStatus) {
    var status = projectStatus.projectStatus();
    var conditions = status.conditions().stream()
      .map(c -> new ProjectStatusToolResponse.Condition(c.metricKey(), c.status(), c.errorThreshold(), c.actualValue()))
      .toList();

    return new ProjectStatusToolResponse(status.status(), conditions, null);
  }

}
