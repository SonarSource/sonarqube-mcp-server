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

import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.sources.response.SourceLinesResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class GetFileCoverageDetailsTool extends Tool {

  public static final String TOOL_NAME = "get_file_coverage_details";
  public static final String KEY_PROPERTY = "key";
  public static final String BRANCH_PROPERTY = "branch";
  public static final String PULL_REQUEST_PROPERTY = "pullRequest";

  private final ServerApiProvider serverApiProvider;

  public GetFileCoverageDetailsTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(GetFileCoverageDetailsToolResponse.class)
        .setName(TOOL_NAME)
        .setTitle("Get File Coverage Details")
        .setDescription("Get complete line-by-line coverage information for a file, " +
          "including which exact lines are uncovered and which have partially covered branches. " +
          "This tool helps identify precisely where to add test coverage. " +
          "Use after identifying files with low coverage via search_files_by_coverage.")
        .addRequiredStringProperty(KEY_PROPERTY, "File key (e.g. my_project:src/foo/Bar.java)")
        .addStringProperty(BRANCH_PROPERTY, "Branch key (e.g. feature/my_branch)")
        .addStringProperty(PULL_REQUEST_PROPERTY, "Pull request id")
        .setReadOnlyHint()
        .build(),
      ToolCategory.COVERAGE);
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var key = arguments.getStringOrThrow(KEY_PROPERTY);
    var branch = arguments.getOptionalString(BRANCH_PROPERTY);
    var pullRequest = arguments.getOptionalString(PULL_REQUEST_PROPERTY);

    try {
      var sourceLinesResponse = serverApiProvider.get().sourcesApi().getSourceLines(key, branch, pullRequest, null, null);

      // Extract file path from key (after the colon)
      var filePath = key.contains(":") ? key.substring(key.indexOf(':') + 1) : null;

      var toolResponse = buildCoverageResponse(key, filePath, sourceLinesResponse);
      return Tool.Result.success(toolResponse);
    } catch (Exception e) {
      return Tool.Result.failure("Failed to retrieve coverage details: " + e.getMessage());
    }
  }

  private static GetFileCoverageDetailsToolResponse buildCoverageResponse(String key, @Nullable String filePath, SourceLinesResponse response) {
    var sources = response.sources();

    var totalLines = sources.size();

    var coverableLines = sources.stream()
      .filter(SourceLinesResponse.SourceLine::isCoverable)
      .toList();

    var coveredLinesCount = coverableLines.stream()
      .filter(line -> !line.isUncovered())
      .count();

    var uncoveredLinesCount = coverableLines.size() - coveredLinesCount;

    var lineCoveragePercent = coverableLines.isEmpty() ? 100.0 : (coveredLinesCount * 100.0 / coverableLines.size());

    // Calculate branch coverage
    var totalConditions = sources.stream()
      .mapToInt(line -> line.conditions() != null ? line.conditions() : 0)
      .sum();

    var coveredConditions = sources.stream()
      .mapToInt(line -> line.coveredConditions() != null ? line.coveredConditions() : 0)
      .sum();

    var uncoveredConditions = totalConditions - coveredConditions;

    var branchCoveragePercent = totalConditions == 0 ? 100.0 : (coveredConditions * 100.0 / totalConditions);

    var summary = new GetFileCoverageDetailsToolResponse.CoverageSummary(
      totalLines,
      coverableLines.size(),
      (int) coveredLinesCount,
      (int) uncoveredLinesCount,
      lineCoveragePercent,
      totalConditions,
      coveredConditions,
      uncoveredConditions,
      branchCoveragePercent
    );

    // Extract uncovered lines (lines with 0 hits)
    var uncoveredLines = sources.stream()
      .filter(SourceLinesResponse.SourceLine::isUncovered)
      .map(line -> new GetFileCoverageDetailsToolResponse.UncoveredLine(line.line()))
      .toList();

    // Extract partially covered branches
    var partiallyConditionalLines = sources.stream()
      .filter(line -> line.hasPartialBranchCoverage() || line.hasNoBranchCoverage())
      .map(line -> {
        var totalCond = line.conditions() != null ? line.conditions() : 0;
        var coveredCond = line.coveredConditions() != null ? line.coveredConditions() : 0;
        return new GetFileCoverageDetailsToolResponse.PartiallyConditionalLine(
          line.line(),
          totalCond,
          coveredCond,
          totalCond - coveredCond
        );
      })
      .toList();

    return new GetFileCoverageDetailsToolResponse(
      key,
      filePath,
      summary,
      uncoveredLines,
      partiallyConditionalLines
    );
  }

}
