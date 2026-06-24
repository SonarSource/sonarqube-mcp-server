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
import org.sonarsource.sonarqube.mcp.serverapi.agenticreadiness.AgenticReadinessApi;

/**
 * Full assessment view returned by {@code get_agentic_readiness_assessment}. Internal identifiers
 * (assessment/pillar UUIDs, project id, timestamps) are intentionally omitted to keep the agent's
 * context focused on actionable signals: the score, recommended actions, and evidence.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssessmentDetails(
  String assessmentId,
  String status,
  @Nullable String branch,
  @Nullable String overallLevel,
  @Nullable String message,
  @Nullable String error,
  @Nullable List<Pillar> pillars) {

  public record Pillar(
    String name,
    int number,
    @Nullable String level,
    @JsonPropertyDescription("Recommended improvements for this pillar") @Nullable List<String> actions,
    @Nullable List<SubSignal> subSignals) {
  }

  public record SubSignal(
    String name,
    @Nullable String level,
    @Nullable List<Evidence> evidence) {
  }

  public record Evidence(
    String text,
    @JsonPropertyDescription("Evidence type, e.g. blocker or positive") String type) {
  }

  public static AssessmentDetails from(AgenticReadinessApi.AssessmentResponse response) {
    var result = response.result();
    return new AssessmentDetails(
      response.id(),
      response.status(),
      response.branch(),
      result != null ? result.overallLevel() : null,
      result != null ? result.message() : null,
      response.error(),
      mapPillars(response.pillarExecutions()));
  }

  @Nullable
  private static List<Pillar> mapPillars(@Nullable List<AgenticReadinessApi.AssessmentResponse.PillarExecution> pillarExecutions) {
    if (pillarExecutions == null) {
      return null;
    }
    return pillarExecutions.stream().map(AssessmentDetails::mapPillar).toList();
  }

  private static Pillar mapPillar(AgenticReadinessApi.AssessmentResponse.PillarExecution pillar) {
    return new Pillar(
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
  private static List<SubSignal> mapSubSignals(@Nullable java.util.Map<String, AgenticReadinessApi.AssessmentResponse.SubSignal> subSignals) {
    if (subSignals == null) {
      return null;
    }
    return subSignals.entrySet().stream()
      .map(entry -> {
        var signal = entry.getValue();
        var evidence = signal.evidence() == null ? null
          : signal.evidence().stream().map(e -> new Evidence(e.text(), e.type())).toList();
        return new SubSignal(entry.getKey(), signal.level(), evidence);
      })
      .toList();
  }
}
