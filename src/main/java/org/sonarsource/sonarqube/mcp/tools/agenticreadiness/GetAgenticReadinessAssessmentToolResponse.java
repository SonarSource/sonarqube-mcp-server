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
import java.util.List;
import jakarta.annotation.Nullable;

/**
 * Structured response for {@link GetAgenticReadinessAssessmentTool}. Internal identifiers
 * (assessment/pillar UUIDs, project id, timestamps) are intentionally omitted to keep the agent's
 * context focused on actionable signals: the score, recommended actions, and evidence.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetAgenticReadinessAssessmentToolResponse(
  String assessmentId,
  String status,
  @Nullable String branch,
  @Nullable String overallLevel,
  @Nullable String message,
  @Nullable String error,
  @Nullable List<Pillar> pillars) {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Pillar(
    String name,
    int number,
    @Nullable String level,
    @Nullable List<String> actions,
    @Nullable List<SubSignal> subSignals) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record SubSignal(
    String name,
    String level,
    List<Evidence> evidence) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Evidence(
    String text,
    String type) {
  }
}
