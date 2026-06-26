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
 * Structured response for {@link ListAgenticReadinessAssessmentsTool}. Each entry is a lightweight
 * summary; use {@code get_agentic_readiness_assessment} for the full pillar-level detail.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ListAgenticReadinessAssessmentsToolResponse(
  @JsonPropertyDescription("Assessment summaries, ordered newest first") List<Assessment> assessments) {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Assessment(
    @JsonPropertyDescription("Unique identifier of the assessment") String assessmentId,
    @JsonPropertyDescription("Lifecycle status: PENDING, IN_PROGRESS, COMPLETED, FAILED or INTERRUPTED") String status,
    @JsonPropertyDescription("Branch the assessment ran against, if any") @Nullable String branch,
    @JsonPropertyDescription("Overall readiness level when available (L1-L5)") @Nullable String overallLevel,
    @JsonPropertyDescription("Creation timestamp of the assessment") @Nullable String createdAt) {
  }
}
