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
package org.sonarsource.sonarqube.mcp.tools.sources;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.Nullable;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetFileCoverageDetailsToolResponse(
  String fileKey,
  @Nullable String filePath,
  CoverageSummary summary,
  List<UncoveredLine> uncoveredLines,
  List<PartiallyConditionalLine> partiallyConditionalLines
) {

  public record CoverageSummary(
    int totalLines,
    int coverableLines,
    int coveredLines,
    int uncoveredLines,
    double lineCoveragePercent,
    int totalConditions,
    int coveredConditions,
    int uncoveredConditions,
    double branchCoveragePercent
  ) {
  }

  public record UncoveredLine(
    int lineNumber
  ) {
  }

  public record PartiallyConditionalLine(
    int lineNumber,
    int totalConditions,
    int coveredConditions,
    int uncoveredConditions
  ) {
  }
}
