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
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class GetAgenticReadinessAssessmentTool extends Tool {

  public static final String TOOL_NAME = "get_agentic_readiness_assessment";

  private final ServerApiProvider serverApiProvider;

  public GetAgenticReadinessAssessmentTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(AssessmentDetails.class)
      .setName(TOOL_NAME)
      .setTitle("Get Agentic Readiness Assessment")
      .setDescription(
        "Retrieve the result of an agentic readiness assessment. Re-call with the same assessmentId "
          + "until status is COMPLETED (or FAILED/INTERRUPTED). When COMPLETED, returns the overall "
          + "readiness level and a per-pillar breakdown, each with recommended actions and supporting evidence.")
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
    return Result.success(AssessmentDetails.from(assessment));
  }
}
