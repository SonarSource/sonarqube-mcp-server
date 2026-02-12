/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
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
package org.sonarsource.sonarqube.mcp.tools.sources;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.annotation.Nullable;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetFileCoverageDetailsToolResponse(
  @JsonPropertyDescription("File component key") String fileKey,
  @JsonPropertyDescription("File path") @Nullable String filePath,
  @JsonPropertyDescription("Coverage summary for this file") CoverageSummary summary,
  @JsonPropertyDescription("List of uncovered lines (lines that have never been executed by tests)") List<UncoveredLine> uncoveredLines,
  @JsonPropertyDescription("List of lines with partially covered branches/conditions") List<PartiallyConditionalLine> partiallyConditionalLines
) {

  public record CoverageSummary(
    @JsonPropertyDescription("Total number of lines in the file") int totalLines,
    @JsonPropertyDescription("Number of coverable lines (executable code)") int coverableLines,
    @JsonPropertyDescription("Number of lines covered by tests") int coveredLines,
    @JsonPropertyDescription("Number of lines not covered by tests") int uncoveredLines,
    @JsonPropertyDescription("Line coverage percentage") double lineCoveragePercent,
    @JsonPropertyDescription("Total number of conditions (branches) to cover") int totalConditions,
    @JsonPropertyDescription("Number of conditions covered by tests") int coveredConditions,
    @JsonPropertyDescription("Number of conditions not covered by tests") int uncoveredConditions,
    @JsonPropertyDescription("Branch coverage percentage") double branchCoveragePercent
  ) {
  }

  public record UncoveredLine(
    @JsonPropertyDescription("Line number (1-based)") int lineNumber
  ) {
  }

  public record PartiallyConditionalLine(
    @JsonPropertyDescription("Line number (1-based)") int lineNumber,
    @JsonPropertyDescription("Total number of conditions (branches) on this line") int totalConditions,
    @JsonPropertyDescription("Number of conditions covered by tests") int coveredConditions,
    @JsonPropertyDescription("Number of conditions not covered by tests") int uncoveredConditions
  ) {
  }
}
