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
package org.sonarsource.sonarqube.mcp.tools.measures;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.Nullable;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchFilesByCoverageToolResponse(
  String projectKey,
  int totalFiles,
  int filesReturned,
  int pageIndex,
  int pageSize,
  @Nullable ProjectSummary projectSummary,
  List<FileWithCoverage> files
) {

  public record ProjectSummary(
    @Nullable Double coverage,
    @Nullable Integer linesToCover,
    @Nullable Integer uncoveredLines
  ) {
  }

  public record FileWithCoverage(
    String key,
    String path,
    @Nullable Double coverage,
    @Nullable Double lineCoverage,
    @Nullable Double branchCoverage,
    @Nullable Integer linesToCover,
    @Nullable Integer uncoveredLines,
    @Nullable Integer conditionsToCover,
    @Nullable Integer uncoveredConditions
  ) {
  }
}
