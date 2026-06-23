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

import java.util.List;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.agenticreadiness.AgenticReadinessApi;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class ListAgenticReadinessAssessmentsTool extends Tool {

  public static final String TOOL_NAME = "list_agentic_readiness_assessments";

  private final ServerApiProvider serverApiProvider;

  public ListAgenticReadinessAssessmentsTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(AssessmentsListResult.class)
      .setName(TOOL_NAME)
      .setTitle("List Agentic Readiness Assessments")
      .setDescription(
        "List all agentic readiness assessments for a project. " +
          "Returns summaries ordered newest first; use get_agentic_readiness_assessment with an assessmentId " +
          "from this list to retrieve full pillar-level results. " +
          "pageIndex defaults to 1, pageSize defaults to 50 (max 100) when omitted.")
      .addRequiredStringProperty("projectKey", "The project key to list assessments for.")
      .addStringProperty("branch", "Filter assessments by branch name. Omit to list assessments for all branches.")
      .addNumberProperty("pageIndex", "1-based page index (default: 1).")
      .addNumberProperty("pageSize", "Number of items per page, max 100 (default: 50).")
      .setReadOnlyHint()
      .build(),
      ToolCategory.AGENTIC_READINESS);
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Result execute(Arguments arguments) {
    var projectKey = arguments.getStringOrThrow("projectKey");
    var branch = arguments.getOptionalString("branch");
    var pageIndex = arguments.getOptionalInteger("pageIndex");
    var pageSize = arguments.getOptionalInteger("pageSize");

    var api = serverApiProvider.get();
    var projectId = api.projectBranchesApi().getProjectUuid(projectKey);
    var assessments = api.agenticReadinessApi().listAssessments(projectId, branch, pageIndex, pageSize);
    return Result.success(new AssessmentsListResult(assessments));
  }

  public record AssessmentsListResult(List<AgenticReadinessApi.AssessmentResponse> assessments) {
  }
}
