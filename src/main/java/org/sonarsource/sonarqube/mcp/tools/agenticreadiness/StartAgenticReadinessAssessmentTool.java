/*
 * SonarQube MCP Server
 * Copyright (C) SonarSource
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
package org.sonarsource.sonarqube.mcp.tools.agenticreadiness;

import jakarta.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.agenticreadiness.AgenticReadinessApi;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class StartAgenticReadinessAssessmentTool extends Tool {

  public static final String TOOL_NAME = "start_agentic_readiness_assessment";

  public static final String PROJECT_KEY_PROPERTY = "projectKey";
  public static final String BRANCH_PROPERTY = "branch";

  private final ServerApiProvider serverApiProvider;
  private final String configuredProjectKey;

  public StartAgenticReadinessAssessmentTool(ServerApiProvider serverApiProvider, @Nullable String configuredProjectKey) {
    super(SchemaToolBuilder.forOutput(StartAgenticReadinessAssessmentToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Start Agentic Readiness Assessment")
      .setDescription(
        "Start an agentic readiness assessment for a project. Pass a branch to assess code changes you have "
          + "pushed; omit it to assess the project's default branch. Returns immediately with status PENDING and "
          + "an assessmentId — use get_agentic_readiness_assessment with that ID to poll for results.")
      .addProjectKeyProperty(PROJECT_KEY_PROPERTY, configuredProjectKey)
      .addStringProperty(BRANCH_PROPERTY, "Branch to assess. Omit to use the project's default branch.")
      .build(),
      ToolCategory.AGENTIC_READINESS);
    this.serverApiProvider = serverApiProvider;
    this.configuredProjectKey = configuredProjectKey;
  }

  @Override
  public Result execute(Arguments arguments) {
    var projectKey = arguments.getProjectKeyWithFallback(PROJECT_KEY_PROPERTY, configuredProjectKey);
    var branch = arguments.getOptionalString(BRANCH_PROPERTY);

    var api = serverApiProvider.get();
    var projectId = api.projectBranchesApi().getProjectId(projectKey);
    if (projectId.isEmpty()) {
      return Result.failure("Could not resolve project '" + projectKey + "'. Verify the project key is correct.");
    }
    var assessment = api.agenticReadinessApi().createAssessment(projectId.get(), branch);
    return Result.success(buildStructuredContent(assessment));
  }

  private static StartAgenticReadinessAssessmentToolResponse buildStructuredContent(AgenticReadinessApi.AssessmentResponse response) {
    var result = response.result();
    return new StartAgenticReadinessAssessmentToolResponse(
      response.id(),
      response.status(),
      response.branch(),
      result != null ? result.overallLevel() : null,
      response.createdAt());
  }
}
