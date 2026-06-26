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
import jakarta.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.agenticreadiness.AgenticReadinessApi;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class ListAgenticReadinessAssessmentsTool extends Tool {

  public static final String TOOL_NAME = "list_agentic_readiness_assessments";

  public static final String PROJECT_KEY_PROPERTY = "projectKey";
  public static final String BRANCH_PROPERTY = "branch";
  public static final String PAGE_INDEX_PROPERTY = "pageIndex";
  public static final String PAGE_SIZE_PROPERTY = "pageSize";

  static final int MAX_PAGE_SIZE = 100;

  private final ServerApiProvider serverApiProvider;
  private final String configuredProjectKey;

  public ListAgenticReadinessAssessmentsTool(ServerApiProvider serverApiProvider, @Nullable String configuredProjectKey) {
    super(SchemaToolBuilder.forOutput(ListAgenticReadinessAssessmentsToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("List Agentic Readiness Assessments")
      .setDescription(
        "List all agentic readiness assessments for a project. " +
          "Returns summaries ordered newest first; use get_agentic_readiness_assessment with an assessmentId " +
          "from this list to retrieve full pillar-level results. " +
          "pageIndex defaults to 1, pageSize defaults to 50 (max 100) when omitted.")
      .addProjectKeyProperty(PROJECT_KEY_PROPERTY, configuredProjectKey)
      .addStringProperty(BRANCH_PROPERTY, "Filter assessments by branch name. Omit to list assessments for all branches.")
      .addNumberProperty(PAGE_INDEX_PROPERTY, "1-based page index (default: 1).")
      .addNumberProperty(PAGE_SIZE_PROPERTY, "Number of items per page, max 100 (default: 50).")
      .setReadOnlyHint()
      .build(),
      ToolCategory.AGENTIC_READINESS);
    this.serverApiProvider = serverApiProvider;
    this.configuredProjectKey = configuredProjectKey;
  }

  @Override
  public Result execute(Arguments arguments) {
    var projectKey = arguments.getProjectKeyWithFallback(PROJECT_KEY_PROPERTY, configuredProjectKey);
    var branch = arguments.getOptionalString(BRANCH_PROPERTY);
    var pageIndex = arguments.getOptionalInteger(PAGE_INDEX_PROPERTY);
    var pageSize = arguments.getOptionalInteger(PAGE_SIZE_PROPERTY);

    if (pageSize != null && (pageSize < 1 || pageSize > MAX_PAGE_SIZE)) {
      return Result.failure("pageSize must be between 1 and " + MAX_PAGE_SIZE);
    }
    if (pageIndex != null && pageIndex < 1) {
      return Result.failure("pageIndex must be greater than or equal to 1");
    }

    var api = serverApiProvider.get();
    var projectId = api.projectBranchesApi().getProjectId(projectKey);
    if (projectId.isEmpty()) {
      return Result.failure("Could not resolve project '" + projectKey + "'. Verify the project key is correct.");
    }
    var assessments = api.agenticReadinessApi().listAssessments(projectId.get(), branch, pageIndex, pageSize);
    return Result.success(buildStructuredContent(assessments));
  }

  private static ListAgenticReadinessAssessmentsToolResponse buildStructuredContent(List<AgenticReadinessApi.AssessmentResponse> assessments) {
    var summaries = assessments.stream()
      .map(assessment -> {
        var result = assessment.result();
        return new ListAgenticReadinessAssessmentsToolResponse.Assessment(
          assessment.id(),
          assessment.status(),
          assessment.branch(),
          result != null ? result.overallLevel() : null,
          assessment.createdAt());
      })
      .toList();
    return new ListAgenticReadinessAssessmentsToolResponse(summaries);
  }
}
