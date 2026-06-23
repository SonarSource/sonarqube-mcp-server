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

public class GetAgenticReadinessAssessmentTool extends Tool {

  public static final String TOOL_NAME = "get_agentic_readiness_assessment";

  private final ServerApiProvider serverApiProvider;

  public GetAgenticReadinessAssessmentTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(AgenticReadinessApi.AssessmentResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Get Agentic Readiness Assessment")
      .setDescription(
        "Retrieve the result of an agentic readiness assessment started with start_agentic_readiness_assessment. " +
          "Re-call with the same assessmentId until status is COMPLETED (or FAILED/INTERRUPTED on error). " +
          "Assessments typically take around 10 minutes to complete and may be delayed by a queue. " +
          "When COMPLETED, the response contains: overallLevel (L1-L5) — the project's current readiness score; " +
          "pillars — per-pillar breakdown with levels, suggestion actions, sub-signals, and evidence items. " +
          "Each sub-signal contains evidence items with a type (e.g. blocker, info) and descriptive text.")
      .addRequiredStringProperty("assessmentId", "The assessment ID returned by start_agentic_readiness_assessment.")
      .setReadOnlyHint()
      .build(),
      ToolCategory.AGENTIC_READINESS);
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Result execute(Arguments arguments) {
    var assessmentId = arguments.getStringOrThrow("assessmentId");
    var assessment = serverApiProvider.get().agenticReadinessApi().getAssessment(assessmentId);
    return Result.success(assessment);
  }
}
