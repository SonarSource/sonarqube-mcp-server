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
import org.sonarsource.sonarqube.mcp.tools.BranchPullRequestContext;
import org.sonarsource.sonarqube.mcp.tools.ToolDefinitionBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolAccessMode;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;
import org.sonarsource.sonarqube.mcp.tools.ToolCapabilities;
import org.sonarsource.sonarqube.mcp.tools.ToolPolicy;
import org.sonarsource.sonarqube.mcp.tools.ToolParameters;

import static org.sonarsource.sonarqube.mcp.tools.ToolAccessMode.READ_WRITE;

public class StartAgenticReadinessAssessmentTool extends Tool {

  public static final String TOOL_NAME = "start_agentic_readiness_assessment";
  public static final ToolPolicy REGISTRATION_POLICY = ToolPolicy.of(ToolCategory.AGENTIC_READINESS).requires(ToolCapabilities::agenticReadiness);
  public static final String INSTRUCTIONS = """

    ## Agentic Readiness Assessment

    Tools to assess how well a SonarQube Cloud project's codebase supports AI agents, scored per pillar \
    (Documentation, Workflow, Dev Environment, Build, Testing, Code Quality, Security, Observability) from \
    L1 (pre-agentic, implicit conventions) to L5 (agent-native, self-describing).

    Workflow:
    1. Call list_agentic_readiness_assessments (filtered by branch) and reuse a recent COMPLETED result if \
    one exists — assessments take ~10 minutes.
    2. Otherwise call start_agentic_readiness_assessment; it returns PENDING with an assessmentId. Do not poll immediately.
    3. After a few minutes, poll get_agentic_readiness_assessment until status is COMPLETED, FAILED, or INTERRUPTED.
    4. Present overallLevel and the per-pillar breakdown, including each pillar's recommended actions and the evidence behind them.

    To measure the impact of changes, compare a baseline assessment of the default branch against a new \
    assessment of your feature branch. Always filter the list by branch — unfiltered results mix branches.""";

  public static final String PROJECT_KEY_PROPERTY = ToolParameters.PROJECT_KEY;
  public static final String BRANCH_PROPERTY = BranchPullRequestContext.BRANCH_PROPERTY;

  private final ServerApiProvider serverApiProvider;
  private final String configuredProjectKey;

  public StartAgenticReadinessAssessmentTool(ServerApiProvider serverApiProvider, @Nullable String configuredProjectKey) {
    super(ToolDefinitionBuilder.builder()
      .setName(TOOL_NAME)
      .setTitle("Start Agentic Readiness Assessment")
      .setDescription(
        "Start an agentic readiness assessment for a project. Pass a branch to assess code changes you have "
          + "pushed; omit it to assess the project's default branch. Returns immediately with status PENDING and "
          + "an assessmentId — use get_agentic_readiness_assessment with that ID to poll for results.")
      .addProjectKeyProperty(PROJECT_KEY_PROPERTY, configuredProjectKey)
      .addStringProperty(BRANCH_PROPERTY, "Branch to assess. Omit to use the project's default branch.")
      .build(),
      REGISTRATION_POLICY);
    this.serverApiProvider = serverApiProvider;
    this.configuredProjectKey = configuredProjectKey;
  }

  @Override
  public ToolAccessMode requiredAccessMode() {
    // Resolve the project ID with a GET before creating the assessment.
    return READ_WRITE;
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
