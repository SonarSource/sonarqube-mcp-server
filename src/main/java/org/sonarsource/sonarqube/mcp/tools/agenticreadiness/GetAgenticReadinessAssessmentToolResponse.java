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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import jakarta.annotation.Nullable;

/**
 * Structured response for {@link GetAgenticReadinessAssessmentTool}. Internal identifiers
 * (assessment/pillar UUIDs, project id, timestamps) are intentionally omitted to keep the agent's
 * context focused on actionable signals: the score, recommended actions, and evidence.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetAgenticReadinessAssessmentToolResponse(
  @JsonPropertyDescription("Unique identifier of the assessment") String assessmentId,
  @JsonPropertyDescription("Lifecycle status: PENDING, IN_PROGRESS, COMPLETED, FAILED or INTERRUPTED") String status,
  @JsonPropertyDescription("Branch the assessment ran against, if any") @Nullable String branch,
  @JsonPropertyDescription("Overall readiness level from L1 (pre-agentic) to L5 (agent-native)") @Nullable String overallLevel,
  @JsonPropertyDescription("Human-readable summary of the overall result") @Nullable String message,
  @JsonPropertyDescription("Error message when the assessment failed") @Nullable String error,
  @JsonPropertyDescription("Per-pillar breakdown of the assessment") @Nullable List<Pillar> pillars) {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Pillar(
    @JsonPropertyDescription("Pillar name, e.g. Documentation & Context") String name,
    @JsonPropertyDescription("1-based pillar number") int number,
    @JsonPropertyDescription("Readiness level for this pillar (L1-L5)") @Nullable String level,
    @JsonPropertyDescription("Recommended improvements for this pillar") @Nullable List<String> actions,
    @JsonPropertyDescription("Sub-signals evaluated within this pillar") @Nullable List<SubSignal> subSignals) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record SubSignal(
    @JsonPropertyDescription("Sub-signal name, e.g. readme") String name,
    @JsonPropertyDescription("Readiness level for this sub-signal (L1-L5)") String level,
    @JsonPropertyDescription("Evidence items supporting this sub-signal's level") List<Evidence> evidence) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Evidence(
    @JsonPropertyDescription("Description of the evidence item") String text,
    @JsonPropertyDescription("Evidence type, e.g. blocker or positive") String type) {
  }
}
