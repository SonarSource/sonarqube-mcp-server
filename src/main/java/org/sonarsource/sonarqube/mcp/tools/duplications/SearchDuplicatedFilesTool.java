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
package org.sonarsource.sonarqube.mcp.tools.duplications;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.measures.response.ComponentMeasuresResponse;
import org.sonarsource.sonarqube.mcp.serverapi.measures.response.ComponentTreeResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class SearchDuplicatedFilesTool extends Tool {

  public static final String TOOL_NAME = "search_duplicated_files";
  public static final String PROJECT_KEY_PROPERTY = "projectKey";
  public static final String BRANCH_PROPERTY = "branch";
  public static final String PULL_REQUEST_PROPERTY = "pullRequest";
  public static final String PAGE_SIZE_PROPERTY = "pageSize";
  public static final String PAGE_INDEX_PROPERTY = "pageIndex";

  private static final String DUPLICATED_LINES_METRIC = "duplicated_lines";
  private static final String DUPLICATED_BLOCKS_METRIC = "duplicated_blocks";
  private static final String DUPLICATED_LINES_DENSITY_METRIC = "duplicated_lines_density";

  private static final List<String> DUPLICATION_METRIC_KEYS = List.of(DUPLICATED_LINES_METRIC, DUPLICATED_BLOCKS_METRIC,
    DUPLICATED_LINES_DENSITY_METRIC);
  private static final String FILE_QUALIFIER = "FIL";
  private static final String STRATEGY = "leaves";
  private static final int DEFAULT_PAGE_SIZE = 500;
  private static final int DEFAULT_PAGE_INDEX = 1;
  private static final int MAX_PAGE_SIZE = 500;
  private static final int MAX_PAGES_TO_FETCH = 20;

  private final ServerApiProvider serverApiProvider;

  public SearchDuplicatedFilesTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(SearchDuplicatedFilesToolResponse.class)
        .setName(TOOL_NAME)
        .setTitle("Search Files With Duplications")
        .setDescription("Search for files with code duplications in a SonarQube project. " +
          "By default, automatically fetches all duplicated files across all pages (up to 10,000 files max).")
        .addRequiredStringProperty(PROJECT_KEY_PROPERTY, "Project key (e.g. my_project)")
        .addStringProperty(BRANCH_PROPERTY, "Branch key (e.g. feature/my_branch)")
        .addStringProperty(PULL_REQUEST_PROPERTY, "Pull request id")
        .addNumberProperty(PAGE_SIZE_PROPERTY, "Optional: Number of results per page for manual pagination (max: 500). " +
          "If not specified, auto-fetches all duplicated files.")
        .addNumberProperty(PAGE_INDEX_PROPERTY, "Optional: Page number for manual pagination (starts at 1). " +
          "If not specified, auto-fetches all duplicated files.")
        .setReadOnlyHint()
        .build(),
      ToolCategory.DUPLICATIONS);
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var projectKey = arguments.getStringOrThrow(PROJECT_KEY_PROPERTY);
    var branch = arguments.getOptionalString(BRANCH_PROPERTY);
    var pullRequest = arguments.getOptionalString(PULL_REQUEST_PROPERTY);
    var requestedPageSize = arguments.getOptionalInteger(PAGE_SIZE_PROPERTY);
    var requestedPageIndex = arguments.getOptionalInteger(PAGE_INDEX_PROPERTY);

    // If user explicitly provided pagination parameters, use single-page mode
    var manualPagination = requestedPageSize != null || requestedPageIndex != null;

    if (manualPagination) {
      return executeSinglePage(projectKey, branch, pullRequest, requestedPageSize != null ? requestedPageSize : DEFAULT_PAGE_SIZE,
        requestedPageIndex != null ? requestedPageIndex : DEFAULT_PAGE_INDEX);
    }

    // Auto-fetch mode: fetch all pages to get all duplicated files
    return executeAutoFetch(projectKey, branch, pullRequest);
  }

  private Tool.Result executeSinglePage(String projectKey, @Nullable String branch, @Nullable String pullRequest, int pageSize, int pageIndex) {
    if (pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
      return Tool.Result.failure("Page size must be between 1 and " + MAX_PAGE_SIZE);
    }
    if (pageIndex <= 0) {
      return Tool.Result.failure("Page index must be greater than 0");
    }

    var projectMetrics = serverApiProvider.get().measuresApi().getComponentMeasures(projectKey, branch, DUPLICATION_METRIC_KEYS, pullRequest);
    var componentTree = serverApiProvider.get().measuresApi().getComponentTree(projectKey, branch, DUPLICATION_METRIC_KEYS, pullRequest, FILE_QUALIFIER, pageSize,
      pageIndex, STRATEGY);

    var response = buildStructuredContent(componentTree, projectMetrics);
    return Tool.Result.success(response);
  }

  private Tool.Result executeAutoFetch(String projectKey, @Nullable String branch, @Nullable String pullRequest) {
    var projectMetrics = serverApiProvider.get().measuresApi().getComponentMeasures(projectKey, branch, DUPLICATION_METRIC_KEYS, pullRequest);

    var allDuplicatedFiles = new ArrayList<ComponentTreeResponse.Component>();
    var currentPage = 1;
    var shouldContinue = true;

    while (currentPage <= MAX_PAGES_TO_FETCH && shouldContinue) {
      var componentTree = serverApiProvider.get().measuresApi().getComponentTree(projectKey, branch, DUPLICATION_METRIC_KEYS,
        pullRequest, FILE_QUALIFIER, MAX_PAGE_SIZE, currentPage, STRATEGY);

      if (componentTree.components().isEmpty()) {
        shouldContinue = false;
      } else {
        // Collect files with duplications from this page
        var duplicatedInPage = componentTree.components().stream().filter(SearchDuplicatedFilesTool::hasDuplications).toList();

        allDuplicatedFiles.addAll(duplicatedInPage);

        var totalFiles = componentTree.paging().total();
        var filesProcessed = currentPage * MAX_PAGE_SIZE;

        // Stop if we've processed all files
        if (filesProcessed >= totalFiles) {
          shouldContinue = false;
        }

        currentPage++;
      }
    }

    var response = buildStructuredContentFromFiles(allDuplicatedFiles, projectMetrics);
    return Tool.Result.success(response);
  }

  private static SearchDuplicatedFilesToolResponse buildStructuredContent(ComponentTreeResponse componentTree, ComponentMeasuresResponse projectMetrics) {

    var duplicatedFiles = componentTree.components().stream().filter(SearchDuplicatedFilesTool::hasDuplications).map(component -> {
      var duplicatedLines = getMeasureValue(component, DUPLICATED_LINES_METRIC);
      var duplicatedBlocks = getMeasureValue(component, DUPLICATED_BLOCKS_METRIC);
      var duplicatedLinesDensity = getMeasureStringValue(component, DUPLICATED_LINES_DENSITY_METRIC);

      return new SearchDuplicatedFilesToolResponse.DuplicatedFile(component.key(), component.name(), component.path(), duplicatedLines, duplicatedBlocks, duplicatedLinesDensity);
    }).toList();

    var paging = new SearchDuplicatedFilesToolResponse.Paging(componentTree.paging().pageIndex(), componentTree.paging().pageSize(), componentTree.paging().total());
    var summary = buildSummary(projectMetrics);

    return new SearchDuplicatedFilesToolResponse(duplicatedFiles, paging, summary);
  }

  private static SearchDuplicatedFilesToolResponse buildStructuredContentFromFiles(List<ComponentTreeResponse.Component> duplicatedComponents,
    ComponentMeasuresResponse projectMetrics) {
    var duplicatedFiles = duplicatedComponents.stream().map(component -> {
      var duplicatedLines = getMeasureValue(component, DUPLICATED_LINES_METRIC);
      var duplicatedBlocks = getMeasureValue(component, DUPLICATED_BLOCKS_METRIC);
      var duplicatedLinesDensity = getMeasureStringValue(component, DUPLICATED_LINES_DENSITY_METRIC);

      return new SearchDuplicatedFilesToolResponse.DuplicatedFile(component.key(), component.name(), component.path(), duplicatedLines, duplicatedBlocks, duplicatedLinesDensity);
    }).toList();

    // Build paging information - show total duplicated files, not all files
    var paging = new SearchDuplicatedFilesToolResponse.Paging(1, duplicatedFiles.size(), duplicatedFiles.size());

    // Build summary from project-level metrics
    var summary = buildSummary(projectMetrics);

    return new SearchDuplicatedFilesToolResponse(duplicatedFiles, paging, summary);
  }

  private static SearchDuplicatedFilesToolResponse.Summary buildSummary(ComponentMeasuresResponse projectMetrics) {
    if (projectMetrics.component() == null || projectMetrics.component().measures() == null) {
      return null;
    }

    var totalDuplicatedLines = getProjectMeasureValue(projectMetrics, DUPLICATED_LINES_METRIC);
    var totalDuplicatedBlocks = getProjectMeasureValue(projectMetrics, DUPLICATED_BLOCKS_METRIC);
    var overallDensity = getProjectMeasureStringValue(projectMetrics, DUPLICATED_LINES_DENSITY_METRIC);

    if (totalDuplicatedLines != null || totalDuplicatedBlocks != null || overallDensity != null) {
      return new SearchDuplicatedFilesToolResponse.Summary(totalDuplicatedLines, totalDuplicatedBlocks, overallDensity);
    }

    return null;
  }

  private static boolean hasDuplications(ComponentTreeResponse.Component component) {
    if (component.measures() == null) {
      return false;
    }
    return component.measures().stream().anyMatch(measure -> DUPLICATED_LINES_METRIC.equals(measure.metric())
      && measure.value() != null && !"0".equals(measure.value()) && !"0.0".equals(measure.value()));
  }

  @Nullable
  private static Integer getMeasureValue(ComponentTreeResponse.Component component, String metricKey) {
    if (component.measures() == null) {
      return null;
    }
    return component.measures().stream()
      .filter(m -> metricKey.equals(m.metric()))
      .map(m -> parseIntegerValue(m.value()))
      .findFirst()
      .orElse(null);
  }

  @Nullable
  private static Integer parseIntegerValue(@Nullable String value) {
    if (value == null) {
      return null;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @Nullable
  private static String getMeasureStringValue(ComponentTreeResponse.Component component, String metricKey) {
    if (component.measures() == null) {
      return null;
    }
    return component.measures().stream()
      .filter(m -> metricKey.equals(m.metric()))
      .map(ComponentTreeResponse.Measure::value)
      .findFirst()
      .orElse(null);
  }

  @Nullable
  private static Integer getProjectMeasureValue(ComponentMeasuresResponse projectMetrics, String metricKey) {
    if (projectMetrics.component() == null || projectMetrics.component().measures() == null) {
      return null;
    }
    return projectMetrics.component().measures().stream()
      .filter(m -> metricKey.equals(m.metric()))
      .map(m -> parseIntegerValue(m.value()))
      .findFirst()
      .orElse(null);
  }

  @Nullable
  private static String getProjectMeasureStringValue(ComponentMeasuresResponse projectMetrics, String metricKey) {
    if (projectMetrics.component() == null || projectMetrics.component().measures() == null) {
      return null;
    }
    return projectMetrics.component().measures().stream()
      .filter(m -> metricKey.equals(m.metric()))
      .map(ComponentMeasuresResponse.Measure::value)
      .findFirst()
      .orElse(null);
  }

}
