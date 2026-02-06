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
package org.sonarsource.sonarqube.mcp.tools.measures;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.annotation.Nullable;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchFilesByCoverageToolResponse(
  @JsonPropertyDescription("Project key") String projectKey,
  @JsonPropertyDescription("Total number of files in the project") int totalFiles,
  @JsonPropertyDescription("Number of files returned in this response") int filesReturned,
  @JsonPropertyDescription("Current page index") int pageIndex,
  @JsonPropertyDescription("Page size") int pageSize,
  @JsonPropertyDescription("Project-level coverage summary") @Nullable ProjectSummary projectSummary,
  @JsonPropertyDescription("List of files with coverage information, sorted by coverage (ascending)") List<FileWithCoverage> files
) {

  public record ProjectSummary(
    @JsonPropertyDescription("Overall project coverage percentage") @Nullable Double coverage,
    @JsonPropertyDescription("Total lines to cover in the project") @Nullable Integer linesToCover,
    @JsonPropertyDescription("Total uncovered lines in the project") @Nullable Integer uncoveredLines
  ) {
  }

  public record FileWithCoverage(
    @JsonPropertyDescription("File component key") String key,
    @JsonPropertyDescription("File path relative to project root") String path,
    @JsonPropertyDescription("Overall coverage percentage for this file") @Nullable Double coverage,
    @JsonPropertyDescription("Line coverage percentage") @Nullable Double lineCoverage,
    @JsonPropertyDescription("Branch coverage percentage") @Nullable Double branchCoverage,
    @JsonPropertyDescription("Number of lines to cover") @Nullable Integer linesToCover,
    @JsonPropertyDescription("Number of uncovered lines") @Nullable Integer uncoveredLines,
    @JsonPropertyDescription("Number of conditions (branches) to cover") @Nullable Integer conditionsToCover,
    @JsonPropertyDescription("Number of uncovered conditions") @Nullable Integer uncoveredConditions
  ) {
  }
}
