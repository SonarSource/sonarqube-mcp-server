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
package org.sonarsource.sonarqube.mcp.tools.qualitygates;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.hotspots.HotspotsApi;
import org.sonarsource.sonarqube.mcp.serverapi.issues.IssuesApi;
import org.sonarsource.sonarqube.mcp.serverapi.issues.response.SearchResponse;
import org.sonarsource.sonarqube.mcp.serverapi.measures.ComponentTreeParams;
import org.sonarsource.sonarqube.mcp.serverapi.measures.response.ComponentTreeResponse;
import org.sonarsource.sonarqube.mcp.serverapi.qualitygates.response.ProjectStatusResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class ProjectStatusTool extends Tool {

  public static final String TOOL_NAME = "check_quality_gate_status";
  public static final String PROJECT_KEY_PROPERTY = "projectKey";
  public static final String PULL_REQUEST_PROPERTY = "pullRequest";
  public static final String BASIC_REPORT_PROPERTY = "basicReport";

  private static final String METRIC_COVERAGE = "coverage";
  private static final String METRIC_LINES_TO_COVER = "lines_to_cover";
  private static final String METRIC_UNCOVERED_LINES = "uncovered_lines";
  private static final String METRIC_DUPLICATED_LINES = "duplicated_lines";
  private static final String METRIC_DUPLICATED_BLOCKS = "duplicated_blocks";
  private static final String METRIC_DUPLICATED_LINES_DENSITY = "duplicated_lines_density";

  private static final int MAX_FILES_TO_SHOW = 10;
  private static final int MAX_ISSUES_TO_SHOW = 20;
  private static final int MAX_HOTSPOTS_TO_SHOW = 10;
  private static final int MAX_DEPENDENCY_RISKS_TO_SHOW = 10;

  private final ServerApiProvider serverApiProvider;

  public ProjectStatusTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(ProjectStatusToolResponse.class)
        .setName(TOOL_NAME)
        .setTitle("Check Quality Gate Status")
        .setDescription("""
          Check if a project passes its quality gate. Returns the overall status (PASSED/FAILED) and details about \
          which quality conditions passed or failed. By default, provides comprehensive detailed information about all \
          failures including specific files, issues, hotspots, duplications, and dependency risks. Use this to determine \
          what needs to be fixed when the quality gate fails.
          """)
        .addRequiredStringProperty(PROJECT_KEY_PROPERTY, "The project key (e.g., 'my_project')")
        .addStringProperty(PULL_REQUEST_PROPERTY, "The pull request ID to check (e.g., '5461'). If not provided, checks the main branch")
        .addBooleanProperty(BASIC_REPORT_PROPERTY, "If true, returns only status and conditions without detailed failure information. " +
          "Default: false (detailed report)")
        .setReadOnlyHint()
        .build(),
      ToolCategory.QUALITY_GATES);
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var projectKey = arguments.getStringOrThrow(PROJECT_KEY_PROPERTY);
    var pullRequest = arguments.getOptionalString(PULL_REQUEST_PROPERTY);
    var basicReport = arguments.getOptionalBoolean(BASIC_REPORT_PROPERTY);

    var projectStatus = serverApiProvider.get().qualityGatesApi().getProjectQualityGateStatus(null, null, null, projectKey, pullRequest);
    var toolResponse = buildStructuredContent(projectStatus, projectKey, pullRequest, basicReport);
    return Tool.Result.success(toolResponse);
  }

  private ProjectStatusToolResponse buildStructuredContent(ProjectStatusResponse projectStatus, String projectKey,
    @Nullable String pullRequest, @Nullable Boolean basicReport) {
    var status = projectStatus.projectStatus();
    var conditions = status.conditions().stream()
      .map(c -> new ProjectStatusToolResponse.Condition(c.metricKey(), c.status(), c.errorThreshold(), c.actualValue()))
      .toList();

    // Default to detailed (basicReport = false or null)
    var isDetailed = basicReport == null || !basicReport;

    var failureDetails = isDetailed && "ERROR".equals(status.status())
      ? buildFailureDetails(conditions, projectKey, pullRequest)
      : null;

    return new ProjectStatusToolResponse(status.status(), conditions, failureDetails);
  }

  private ProjectStatusToolResponse.FailureDetails buildFailureDetails(List<ProjectStatusToolResponse.Condition> conditions,
    String projectKey, @Nullable String pullRequest) {

    var failedConditions = conditions.stream()
      .filter(c -> "ERROR".equals(c.status()))
      .toList();

    if (failedConditions.isEmpty()) {
      return null;
    }

    var coverageFailures = new ArrayList<ProjectStatusToolResponse.CoverageFailure>();
    var issuesFailures = new ArrayList<ProjectStatusToolResponse.IssuesFailure>();
    var hotspotsFailures = new ArrayList<ProjectStatusToolResponse.HotspotsFailure>();
    var duplicationFailures = new ArrayList<ProjectStatusToolResponse.DuplicationFailure>();
    var dependencyRisksFailures = new ArrayList<ProjectStatusToolResponse.DependencyRisksFailure>();

    for (var condition : failedConditions) {
      collectFailureByType(condition, projectKey, pullRequest, coverageFailures, issuesFailures,
        hotspotsFailures, duplicationFailures, dependencyRisksFailures);
    }

    if (hasNoFailures(coverageFailures, issuesFailures, hotspotsFailures, duplicationFailures, dependencyRisksFailures)) {
      return null;
    }

    return new ProjectStatusToolResponse.FailureDetails(
      emptyToNull(coverageFailures),
      emptyToNull(issuesFailures),
      emptyToNull(hotspotsFailures),
      emptyToNull(duplicationFailures),
      emptyToNull(dependencyRisksFailures)
    );
  }

  private void collectFailureByType(ProjectStatusToolResponse.Condition condition, String projectKey,
    @Nullable String pullRequest, List<ProjectStatusToolResponse.CoverageFailure> coverageFailures,
    List<ProjectStatusToolResponse.IssuesFailure> issuesFailures, List<ProjectStatusToolResponse.HotspotsFailure> hotspotsFailures,
    List<ProjectStatusToolResponse.DuplicationFailure> duplicationFailures, List<ProjectStatusToolResponse.DependencyRisksFailure> dependencyRisksFailures) {
    var metricKey = condition.metricKey();

    if (isCoverageMetric(metricKey)) {
      addIfNotNull(coverageFailures, buildCoverageFailure(condition, projectKey, pullRequest));
    } else if (isIssueMetric(metricKey)) {
      addIfNotNull(issuesFailures, buildIssuesFailure(condition, projectKey, pullRequest));
    } else if (isHotspotMetric(metricKey)) {
      addIfNotNull(hotspotsFailures, buildHotspotsFailure(condition, projectKey, pullRequest));
    } else if (isDuplicationMetric(metricKey)) {
      addIfNotNull(duplicationFailures, buildDuplicationFailure(condition, projectKey, pullRequest));
    } else if (isDependencyRiskMetric(metricKey)) {
      addIfNotNull(dependencyRisksFailures, buildDependencyRisksFailure(condition, projectKey, pullRequest));
    }
  }

  private static <T> void addIfNotNull(List<T> list, @Nullable T item) {
    if (item != null) {
      list.add(item);
    }
  }

  private static boolean hasNoFailures(List<?>... failureLists) {
    return java.util.Arrays.stream(failureLists).allMatch(List::isEmpty);
  }

  private static <T> List<T> emptyToNull(List<T> list) {
    return list.isEmpty() ? null : list;
  }

  @Nullable
  private ProjectStatusToolResponse.CoverageFailure buildCoverageFailure(
    ProjectStatusToolResponse.Condition condition, String projectKey, @Nullable String pullRequest) {
    try {
      var metricKeys = List.of(METRIC_COVERAGE, METRIC_LINES_TO_COVER, METRIC_UNCOVERED_LINES);
      var params = new ComponentTreeParams(
        projectKey,
        null,
        metricKeys,
        pullRequest,
        "FIL",
        "all",
        "metric",
        METRIC_COVERAGE,
        true,
        1,
        MAX_FILES_TO_SHOW,
        null
      );

      var componentTree = serverApiProvider.get().measuresApi().getComponentTree(params);
      var worstFiles = componentTree.components().stream()
        .map(ProjectStatusTool::mapToFileCoverage)
        .toList();

      return new ProjectStatusToolResponse.CoverageFailure(
        condition.metricKey(),
        condition.errorThreshold(),
        condition.actualValue(),
        worstFiles
      );
    } catch (Exception e) {
      return null;
    }
  }

  private static ProjectStatusToolResponse.FileCoverage mapToFileCoverage(ComponentTreeResponse.Component component) {
    var path = component.path() != null ? component.path() : component.name();
    var measures = component.measures();

    if (measures == null) {
      return new ProjectStatusToolResponse.FileCoverage(
        path,
        null,
        null,
        null
      );
    }

    var measureMap = measures.stream().collect(Collectors.toMap(ComponentTreeResponse.Measure::metric, m -> m));

    var coverage = getMeasureValue(measureMap, METRIC_COVERAGE);
    var linesToCover = getMeasureIntValue(measureMap, METRIC_LINES_TO_COVER);
    var uncoveredLines = getMeasureIntValue(measureMap, METRIC_UNCOVERED_LINES);

    return new ProjectStatusToolResponse.FileCoverage(
      path,
      coverage,
      linesToCover,
      uncoveredLines
    );
  }

  @Nullable
  private ProjectStatusToolResponse.IssuesFailure buildIssuesFailure(
    ProjectStatusToolResponse.Condition condition, String projectKey, @Nullable String pullRequest) {

    try {
      var severities = getSeveritiesForMetric(condition.metricKey());
      var issueStatuses = List.of("OPEN", "CONFIRMED");

      // For "new_*" metrics, filter for issues in the new code period only
      var inNewCodePeriod = condition.metricKey().startsWith("new_") ? true : null;

      var searchParams = new IssuesApi.SearchParams(
        List.of(projectKey),
        null,
        null,
        pullRequest,
        severities,
        null,
        issueStatuses,
        null,
        1,
        MAX_ISSUES_TO_SHOW,
        inNewCodePeriod
      );

      var issuesResponse = serverApiProvider.get().issuesApi().search(searchParams);

      // Create a map of component keys to paths for quick lookup
      var componentPaths = issuesResponse.components().stream()
        .collect(Collectors.toMap(
          SearchResponse.Component::key,
          c -> c.path() != null ? c.path() : c.name()
        ));

      var issues = issuesResponse.issues().stream()
        .map(issue -> new ProjectStatusToolResponse.IssuePreview(
          issue.key(),
          issue.rule(),
          issue.severity(),
          issue.type(),
          componentPaths.get(issue.component()),
          issue.line(),
          issue.message()
        ))
        .toList();

      return new ProjectStatusToolResponse.IssuesFailure(
        condition.metricKey(),
        condition.errorThreshold(),
        condition.actualValue(),
        issues
      );
    } catch (Exception e) {
      return null;
    }
  }

  @Nullable
  private ProjectStatusToolResponse.HotspotsFailure buildHotspotsFailure(
    ProjectStatusToolResponse.Condition condition, String projectKey, @Nullable String pullRequest) {

    try {
      var searchParams = new HotspotsApi.SearchParams(
        projectKey,
        null,
        pullRequest,
        null,
        null,
        "TO_REVIEW",
        null,
        null,
        null,
        1,
        MAX_HOTSPOTS_TO_SHOW
      );

      var hotspotsResponse = serverApiProvider.get().hotspotsApi().search(searchParams);

      var componentPaths = hotspotsResponse.components().stream()
        .collect(Collectors.toMap(
          org.sonarsource.sonarqube.mcp.serverapi.hotspots.response.SearchResponse.Component::key,
          c -> c.path() != null ? c.path() : c.name()
        ));

      var hotspots = hotspotsResponse.hotspots().stream()
        .map(hotspot -> new ProjectStatusToolResponse.HotspotPreview(
          hotspot.key(),
          hotspot.vulnerabilityProbability(),
          componentPaths.get(hotspot.component()),
          hotspot.line(),
          hotspot.message()
        ))
        .toList();

      return new ProjectStatusToolResponse.HotspotsFailure(
        condition.metricKey(),
        condition.errorThreshold(),
        condition.actualValue(),
        hotspots
      );
    } catch (Exception e) {
      return null;
    }
  }

  @Nullable
  private ProjectStatusToolResponse.DuplicationFailure buildDuplicationFailure(
    ProjectStatusToolResponse.Condition condition, String projectKey, @Nullable String pullRequest) {

    try {
      var metricKeys = List.of(METRIC_DUPLICATED_LINES, METRIC_DUPLICATED_BLOCKS, METRIC_DUPLICATED_LINES_DENSITY);
      var params = new ComponentTreeParams(
        projectKey,
        null,
        metricKeys,
        pullRequest,
        "FIL",
        "all",
        "metric",
        METRIC_DUPLICATED_LINES_DENSITY,
        false,
        1,
        MAX_FILES_TO_SHOW,
        null
      );

      var componentTree = serverApiProvider.get().measuresApi().getComponentTree(params);
      var worstFiles = componentTree.components().stream()
        .filter(ProjectStatusTool::hasDuplication)
        .map(ProjectStatusTool::mapToDuplicatedFile)
        .toList();

      return new ProjectStatusToolResponse.DuplicationFailure(
        condition.metricKey(),
        condition.errorThreshold(),
        condition.actualValue(),
        worstFiles
      );
    } catch (Exception e) {
      return null;
    }
  }

  private static boolean hasDuplication(ComponentTreeResponse.Component component) {
    if (component.measures() == null) {
      return false;
    }
    return component.measures().stream()
      .anyMatch(m -> METRIC_DUPLICATED_LINES.equals(m.metric())
        && m.value() != null
        && !"0".equals(m.value())
        && !"0.0".equals(m.value()));
  }

  private static ProjectStatusToolResponse.DuplicatedFile mapToDuplicatedFile(ComponentTreeResponse.Component component) {
    var path = component.path() != null ? component.path() : component.name();
    var measures = component.measures();

    if (measures == null) {
      return new ProjectStatusToolResponse.DuplicatedFile(
        path,
        null,
        null
      );
    }

    var measureMap = measures.stream()
      .collect(Collectors.toMap(ComponentTreeResponse.Measure::metric, m -> m));

    var duplicatedLines = getMeasureIntValue(measureMap, METRIC_DUPLICATED_LINES);
    var duplicatedBlocks = getMeasureIntValue(measureMap, METRIC_DUPLICATED_BLOCKS);

    return new ProjectStatusToolResponse.DuplicatedFile(
      path,
      duplicatedLines,
      duplicatedBlocks
    );
  }

  @Nullable
  private ProjectStatusToolResponse.DependencyRisksFailure buildDependencyRisksFailure(
    ProjectStatusToolResponse.Condition condition, String projectKey, @Nullable String pullRequest) {

    try {
      if (!serverApiProvider.get().scaApi().isScaEnabled()) {
        return null;
      }

      var risksResponse = serverApiProvider.get().scaApi().getDependencyRisks(projectKey, null, pullRequest);

      var risks = risksResponse.issuesReleases().stream()
        .limit(MAX_DEPENDENCY_RISKS_TO_SHOW)
        .map(issue -> new ProjectStatusToolResponse.DependencyRisk(
          issue.severity(),
          issue.vulnerabilityId(),
          issue.release().packageName(),
          issue.release().version()
        ))
        .toList();

      return new ProjectStatusToolResponse.DependencyRisksFailure(
        condition.metricKey(),
        condition.errorThreshold(),
        condition.actualValue(),
        risks
      );
    } catch (Exception e) {
      return null;
    }
  }

  private static boolean isCoverageMetric(String metricKey) {
    return metricKey.contains(METRIC_COVERAGE) ||
      metricKey.equals(METRIC_UNCOVERED_LINES) ||
      metricKey.equals(METRIC_LINES_TO_COVER);
  }

  private static boolean isIssueMetric(String metricKey) {
    return metricKey.contains("violations") ||
      metricKey.contains("bugs") ||
      metricKey.contains("vulnerabilities") ||
      metricKey.contains("code_smells") ||
      metricKey.contains("issues");
  }

  private static boolean isHotspotMetric(String metricKey) {
    return metricKey.contains("security_hotspots") ||
      metricKey.contains("hotspots");
  }

  private static boolean isDuplicationMetric(String metricKey) {
    return metricKey.contains("duplicat");
  }

  private static boolean isDependencyRiskMetric(String metricKey) {
    return metricKey.contains("dependency") ||
      metricKey.contains("sca_") ||
      metricKey.contains("vulnerable");
  }

  private static List<String> getSeveritiesForMetric(String metricKey) {
    if (metricKey.contains("blocker")) {
      return List.of("BLOCKER");
    } else if (metricKey.contains("critical")) {
      return List.of("HIGH");
    } else if (metricKey.contains("major")) {
      return List.of("MEDIUM");
    } else if (metricKey.contains("minor")) {
      return List.of("LOW");
    } else if (metricKey.contains("info")) {
      return List.of("INFO");
    }

    // For generic metrics like "new_violations", return high-severity issues
    return List.of("BLOCKER", "HIGH", "MEDIUM");
  }

  @Nullable
  private static String getMeasureValue(Map<String, ComponentTreeResponse.Measure> measureMap, String metricKey) {
    var measure = measureMap.get(metricKey);
    return measure != null ? measure.value() : null;
  }

  @Nullable
  private static Integer getMeasureIntValue(Map<String, ComponentTreeResponse.Measure> measureMap, String metricKey) {
    var value = getMeasureValue(measureMap, metricKey);
    if (value == null) {
      return null;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

}
