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
import java.util.Map;
import jakarta.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.agenticreadiness.AgenticReadinessApi;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class GetAgenticReadinessAssessmentTool extends Tool {

  public static final String TOOL_NAME = "get_agentic_readiness_assessment";

  private final ServerApiProvider serverApiProvider;

  public GetAgenticReadinessAssessmentTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(GetAgenticReadinessAssessmentToolResponse.class)
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
    return Result.success(buildStructuredContent(assessment));
  }

  private static GetAgenticReadinessAssessmentToolResponse buildStructuredContent(AgenticReadinessApi.AssessmentResponse response) {
    var result = response.result();
    return new GetAgenticReadinessAssessmentToolResponse(
      response.id(),
      response.status(),
      response.branch(),
      result != null ? result.overallLevel() : null,
      result != null ? result.message() : null,
      response.error(),
      mapPillars(response.pillarExecutions()));
  }

  @Nullable
  private static List<GetAgenticReadinessAssessmentToolResponse.Pillar> mapPillars(
    @Nullable List<AgenticReadinessApi.AssessmentResponse.PillarExecution> pillarExecutions) {
    if (pillarExecutions == null) {
      return null;
    }
    return pillarExecutions.stream().map(GetAgenticReadinessAssessmentTool::mapPillar).toList();
  }

  private static GetAgenticReadinessAssessmentToolResponse.Pillar mapPillar(AgenticReadinessApi.AssessmentResponse.PillarExecution pillar) {
    return new GetAgenticReadinessAssessmentToolResponse.Pillar(
      pillar.pillarName(),
      pillar.pillarNumber(),
      pillar.level(),
      mapActions(pillar.actions()),
      mapSubSignals(pillar.subSignals()));
  }

  @Nullable
  private static List<String> mapActions(@Nullable List<AgenticReadinessApi.AssessmentResponse.Action> actions) {
    if (actions == null) {
      return null;
    }
    return actions.stream().map(AgenticReadinessApi.AssessmentResponse.Action::text).toList();
  }

  @Nullable
  private static List<GetAgenticReadinessAssessmentToolResponse.SubSignal> mapSubSignals(
    @Nullable Map<String, AgenticReadinessApi.AssessmentResponse.SubSignal> subSignals) {
    if (subSignals == null) {
      return null;
    }
    return subSignals.entrySet().stream()
      .map(entry -> {
        var signal = entry.getValue();
        // evidence is required per the API contract, but guard against a contract violation since a
        // null here would fail the whole get call rather than degrade gracefully.
        var evidenceItems = signal.evidence() == null ? List.<AgenticReadinessApi.AssessmentResponse.EvidenceItem>of() : signal.evidence();
        var evidence = evidenceItems.stream()
          .map(e -> new GetAgenticReadinessAssessmentToolResponse.Evidence(e.text(), e.type()))
          .toList();
        return new GetAgenticReadinessAssessmentToolResponse.SubSignal(entry.getKey(), signal.level(), evidence);
      })
      .toList();
  }
}
