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
import jakarta.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.agenticreadiness.AgenticReadinessApi;

/**
 * Lightweight assessment view used by the list and start tools. Carries only the fields needed to
 * identify an assessment, track its lifecycle, and read its headline score — pillar-level detail is
 * available through {@code get_agentic_readiness_assessment}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssessmentSummary(
  String assessmentId,
  String status,
  @Nullable String branch,
  @Nullable String overallLevel,
  @Nullable String createdAt) {

  public static AssessmentSummary from(AgenticReadinessApi.AssessmentResponse response) {
    var result = response.result();
    return new AssessmentSummary(
      response.id(),
      response.status(),
      response.branch(),
      result != null ? result.overallLevel() : null,
      response.createdAt());
  }
}
