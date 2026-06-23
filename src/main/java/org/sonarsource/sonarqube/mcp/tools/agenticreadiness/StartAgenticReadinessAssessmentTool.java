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

import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.agenticreadiness.AgenticReadinessApi;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class StartAgenticReadinessAssessmentTool extends Tool {

  public static final String TOOL_NAME = "start_agentic_readiness_assessment";

  private final ServerApiProvider serverApiProvider;

  public StartAgenticReadinessAssessmentTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(AgenticReadinessApi.AssessmentResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Start Agentic Readiness Assessment")
      .setDescription(
        "Start an agentic readiness assessment for a project. The assessment evaluates the project across multiple readiness pillars " +
          "(Documentation & Context, Workflow & Contribution, Dev Environment, etc.) and scores each from L1 (pre-agentic) to L5 (agent-native). " +
          "To measure the impact of code changes, push them to a branch first, then pass that branch via the branch parameter. " +
          "Without branch, the assessment runs against the project's default branch and will not reflect uncommitted work. " +
          "Returns immediately with status PENDING and an assessmentId. Use get_agentic_readiness_assessment with that ID to poll for results.")
      .addRequiredStringProperty("projectKey", "The project key")
      .addStringProperty("branch", "Branch to assess. Omit to use the project's default branch.")
      .build(),
      ToolCategory.AGENTIC_READINESS);
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Result execute(Arguments arguments) {
    var projectKey = arguments.getStringOrThrow("projectKey");
    var branch = arguments.getOptionalString("branch");

    var api = serverApiProvider.get();
    var projectId = api.projectBranchesApi().getProjectUuid(projectKey);
    var assessment = api.agenticReadinessApi().createAssessment(projectId, branch);
    return Result.success(assessment);
  }
}
