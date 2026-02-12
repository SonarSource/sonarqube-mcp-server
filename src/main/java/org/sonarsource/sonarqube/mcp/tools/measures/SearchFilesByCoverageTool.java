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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.measures.ComponentTreeParams;
import org.sonarsource.sonarqube.mcp.serverapi.measures.response.ComponentMeasuresResponse;
import org.sonarsource.sonarqube.mcp.serverapi.measures.response.ComponentTreeResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class SearchFilesByCoverageTool extends Tool {

  public static final String TOOL_NAME = "search_files_by_coverage";
  public static final String PROJECT_KEY_PROPERTY = "projectKey";
  public static final String BRANCH_PROPERTY = "branch";
  public static final String PULL_REQUEST_PROPERTY = "pullRequest";
  public static final String MAX_COVERAGE_PROPERTY = "maxCoverage";
  public static final String PAGE_INDEX_PROPERTY = "pageIndex";
  public static final String PAGE_SIZE_PROPERTY = "pageSize";

  // Metric keys
  private static final String METRIC_COVERAGE = "coverage";
  private static final String METRIC_LINES_TO_COVER = "lines_to_cover";
  private static final String METRIC_UNCOVERED_LINES = "uncovered_lines";

  private final ServerApiProvider serverApiProvider;

  public SearchFilesByCoverageTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(SearchFilesByCoverageToolResponse.class)
        .setName(TOOL_NAME)
        .setTitle("Search Files by Coverage")
        .setDescription("Search for files in a project sorted by coverage (ascending - worst coverage first). " +
          "This tool helps identify files that need test coverage improvements.")
        .addRequiredStringProperty(PROJECT_KEY_PROPERTY, "The project key to search in")
        .addStringProperty(BRANCH_PROPERTY, "Branch key to analyze (e.g. feature/my_branch)")
        .addStringProperty(PULL_REQUEST_PROPERTY, "Pull request id to analyze")
        .addNumberProperty(MAX_COVERAGE_PROPERTY, "Only return files with coverage below this threshold (0-100)")
        .addNumberProperty(PAGE_INDEX_PROPERTY, "Page index (1-based, default: 1)")
        .addNumberProperty(PAGE_SIZE_PROPERTY, "Page size (default: 100, max: 500)")
        .setReadOnlyHint()
        .build(),
      ToolCategory.COVERAGE);
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var projectKey = arguments.getStringOrThrow(PROJECT_KEY_PROPERTY);
    var branch = arguments.getOptionalString(BRANCH_PROPERTY);
    var pullRequest = arguments.getOptionalString(PULL_REQUEST_PROPERTY);
    var maxCoverage = arguments.getOptionalInteger(MAX_COVERAGE_PROPERTY);
    var pageIndex = arguments.getOptionalInteger(PAGE_INDEX_PROPERTY);
    var pageSize = arguments.getOptionalInteger(PAGE_SIZE_PROPERTY);

    if (maxCoverage != null && (maxCoverage < 0 || maxCoverage > 100)) {
      return Tool.Result.failure("maxCoverage must be between 0 and 100");
    }

    var actualPageIndex = (pageIndex != null && pageIndex > 0) ? pageIndex : 1;
    var actualPageSize = (pageSize != null && pageSize > 0) ? Math.min(pageSize, 500) : 100;

    // First, get project-level metrics for summary
    var projectMetrics = serverApiProvider.get().measuresApi().getComponentMeasures(projectKey, branch,
      List.of(METRIC_COVERAGE, METRIC_LINES_TO_COVER, METRIC_UNCOVERED_LINES), pullRequest
    );

    // Then get the file tree with coverage metrics
    var metricKeys = List.of(METRIC_COVERAGE, "line_coverage", "branch_coverage",
      METRIC_LINES_TO_COVER, METRIC_UNCOVERED_LINES,
      "conditions_to_cover", "uncovered_conditions");

    var params = new ComponentTreeParams(
      projectKey,
      branch,
      metricKeys,
      pullRequest,
      // Only files
      "FIL",
      // All files in tree
      "all",
      // Sort by metric
      "metric",
      // Sort by coverage metric specifically
      METRIC_COVERAGE,
      // Ascending order (worst coverage first)
      true,
      actualPageIndex,
      actualPageSize,
      null
    );
    var treeResponse = serverApiProvider.get().measuresApi().getComponentTree(params);

    var toolResponse = buildStructuredContent(projectKey, treeResponse, projectMetrics, maxCoverage, actualPageIndex, actualPageSize);
    return Tool.Result.success(toolResponse);
  }

  private static SearchFilesByCoverageToolResponse buildStructuredContent(String projectKey, ComponentTreeResponse treeResponse, ComponentMeasuresResponse projectMetrics,
    Integer maxCoverage, int pageIndex, int pageSize) {
    // Build project summary from project metrics
    SearchFilesByCoverageToolResponse.ProjectSummary projectSummary = null;
    if (projectMetrics != null && projectMetrics.component() != null && projectMetrics.component().measures() != null) {
      var measuresMap = projectMetrics.component().measures().stream()
        .collect(Collectors.toMap(ComponentMeasuresResponse.Measure::metric, ComponentMeasuresResponse.Measure::value));

      projectSummary = new SearchFilesByCoverageToolResponse.ProjectSummary(
        parseDouble(measuresMap.get(METRIC_COVERAGE)),
        parseInteger(measuresMap.get(METRIC_LINES_TO_COVER)),
        parseInteger(measuresMap.get(METRIC_UNCOVERED_LINES))
      );
    }

    // Build file list with coverage info, applying filters
    var files = treeResponse.components().stream()
      .map(comp -> {
        var measures = comp.measures() != null ?
          comp.measures().stream().collect(Collectors.toMap(
            ComponentTreeResponse.Measure::metric,
            ComponentTreeResponse.Measure::value
          )) : Map.<String, String>of();

        var coverage = parseDouble(measures.get(METRIC_COVERAGE));
        var lineCoverage = parseDouble(measures.get("line_coverage"));
        var branchCoverage = parseDouble(measures.get("branch_coverage"));
        var linesToCover = parseInteger(measures.get(METRIC_LINES_TO_COVER));
        var uncoveredLines = parseInteger(measures.get(METRIC_UNCOVERED_LINES));
        var conditionsToCover = parseInteger(measures.get("conditions_to_cover"));
        var uncoveredConditions = parseInteger(measures.get("uncovered_conditions"));

        return new SearchFilesByCoverageToolResponse.FileWithCoverage(
          comp.key(),
          comp.path() != null ? comp.path() : comp.name(),
          coverage,
          lineCoverage,
          branchCoverage,
          linesToCover,
          uncoveredLines,
          conditionsToCover,
          uncoveredConditions
        );
      })
      // Apply coverage filter
      .filter(file -> {
        if (file.coverage() == null) {
          return false;
        }
        return maxCoverage == null || file.coverage() <= maxCoverage;
      })
      .toList();

    return new SearchFilesByCoverageToolResponse(
      projectKey,
      treeResponse.paging().total(),
      files.size(),
      pageIndex,
      pageSize,
      projectSummary,
      files
    );
  }

  private static Double parseDouble(String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Integer parseInteger(String value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

}
