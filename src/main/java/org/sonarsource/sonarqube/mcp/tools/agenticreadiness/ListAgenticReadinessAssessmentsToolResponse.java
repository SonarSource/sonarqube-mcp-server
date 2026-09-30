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
 * Structured response for {@link ListAgenticReadinessAssessmentsTool}. Each entry is a lightweight
 * summary; use {@code get_agentic_readiness_assessment} for the full pillar-level detail.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ListAgenticReadinessAssessmentsToolResponse(
  List<Assessment> assessments) {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Assessment(
    String assessmentId,
    String status,
    @Nullable String branch,
    @Nullable String overallLevel,
    @Nullable String createdAt) {
  }
}
