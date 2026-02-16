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
import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectStatusToolResponse(
  @JsonPropertyDescription("Overall quality gate status: OK if passed, ERROR if failed, WARN for warnings") String status,
  @JsonPropertyDescription("List of quality gate conditions with their pass/fail status") List<Condition> conditions,
  @JsonPropertyDescription("Detailed context about failed conditions (null if all passed or detailLevel=basic)") @Nullable FailureDetails failureDetails
) {
  
  public record Condition(
    @JsonPropertyDescription("Metric key (e.g., new_coverage, new_blocker_violations)") String metricKey,
    @JsonPropertyDescription("Condition status: OK if passed, ERROR if failed") String status,
    @JsonPropertyDescription("The threshold value that must not be exceeded") @Nullable String errorThreshold,
    @JsonPropertyDescription("The current actual value of the metric") @Nullable String actualValue
  ) {}
  
  public record FailureDetails(
    @JsonPropertyDescription("Coverage-related failures with list of files needing test coverage") @Nullable List<CoverageFailure> coverageFailures,
    @JsonPropertyDescription("Issue/violation-related failures with list of actual issues") @Nullable List<IssuesFailure> issuesFailures,
    @JsonPropertyDescription("Security hotspot-related failures with list of unreviewed hotspots") @Nullable List<HotspotsFailure> hotspotsFailures,
    @JsonPropertyDescription("Duplication-related failures with list of most duplicated files") @Nullable List<DuplicationFailure> duplicationFailures,
    @JsonPropertyDescription("Dependency risk-related failures with list of vulnerable dependencies") @Nullable List<DependencyRisksFailure> dependencyRisksFailures
  ) {}
  
  public record CoverageFailure(
    @JsonPropertyDescription("The coverage metric that failed (e.g., new_coverage, coverage)") String failedMetric,
    @JsonPropertyDescription("The threshold that was not met") @Nullable String threshold,
    @JsonPropertyDescription("The actual coverage value") @Nullable String actualValue,
    @JsonPropertyDescription("Top 10 files with lowest coverage (sorted worst first)") List<FileCoverage> worstFiles
  ) {}
  
  public record FileCoverage(
    @JsonPropertyDescription("File path relative to project root") String path,
    @JsonPropertyDescription("Coverage percentage (0-100)") @Nullable String coverage,
    @JsonPropertyDescription("Number of lines to cover") @Nullable Integer linesToCover,
    @JsonPropertyDescription("Number of uncovered lines") @Nullable Integer uncoveredLines
  ) {}
  
  public record IssuesFailure(
    @JsonPropertyDescription("The issue metric that failed (e.g., new_blocker_violations)") String failedMetric,
    @JsonPropertyDescription("The threshold that was not met") @Nullable String threshold,
    @JsonPropertyDescription("The actual number of issues") @Nullable String actualValue,
    @JsonPropertyDescription("List of issues that caused the failure") List<IssuePreview> issues
  ) {}
  
  public record IssuePreview(
    @JsonPropertyDescription("Issue key") String key,
    @JsonPropertyDescription("Rule key (e.g., java:S1234)") String rule,
    @JsonPropertyDescription("Issue severity (BLOCKER, CRITICAL, MAJOR, MINOR, INFO)") String severity,
    @JsonPropertyDescription("Issue type (BUG, VULNERABILITY, CODE_SMELL)") @Nullable String type,
    @JsonPropertyDescription("File path") @Nullable String filePath,
    @JsonPropertyDescription("Line number where issue is located") @Nullable Integer line,
    @JsonPropertyDescription("Issue message/description") String message
  ) {}
  
  public record HotspotsFailure(
    @JsonPropertyDescription("The hotspot metric that failed (e.g., new_security_hotspots)") String failedMetric,
    @JsonPropertyDescription("The threshold that was not met") @Nullable String threshold,
    @JsonPropertyDescription("The actual number of hotspots") @Nullable String actualValue,
    @JsonPropertyDescription("List of unreviewed security hotspots") List<HotspotPreview> hotspots
  ) {}
  
  public record HotspotPreview(
    @JsonPropertyDescription("Hotspot key") String key,
    @JsonPropertyDescription("Vulnerability probability (HIGH, MEDIUM, LOW)") String vulnerabilityProbability,
    @JsonPropertyDescription("File path") @Nullable String filePath,
    @JsonPropertyDescription("Line number where hotspot is located") @Nullable Integer line,
    @JsonPropertyDescription("Hotspot message/description") String message
  ) {}
  
  public record DuplicationFailure(
    @JsonPropertyDescription("The duplication metric that failed (e.g., new_duplicated_lines_density)") String failedMetric,
    @JsonPropertyDescription("The threshold that was not met") @Nullable String threshold,
    @JsonPropertyDescription("The actual duplication value") @Nullable String actualValue,
    @JsonPropertyDescription("Top 10 files with highest duplication (sorted worst first)") List<DuplicatedFile> worstFiles
  ) {}
  
  public record DuplicatedFile(
    @JsonPropertyDescription("File path relative to project root") String path,
    @JsonPropertyDescription("Number of duplicated lines") @Nullable Integer duplicatedLines,
    @JsonPropertyDescription("Number of duplicated blocks") @Nullable Integer duplicatedBlocks
  ) {}
  
  public record DependencyRisksFailure(
    @JsonPropertyDescription("The dependency risk metric that failed") String failedMetric,
    @JsonPropertyDescription("The threshold that was not met") @Nullable String threshold,
    @JsonPropertyDescription("The actual number of risks") @Nullable String actualValue,
    @JsonPropertyDescription("List of vulnerable dependencies") List<DependencyRisk> risks
  ) {}
  
  public record DependencyRisk(
    @JsonPropertyDescription("Severity (BLOCKER, CRITICAL, HIGH, MEDIUM, LOW, INFO)") String severity,
    @JsonPropertyDescription("CVE or vulnerability ID") @Nullable String vulnerabilityId,
    @JsonPropertyDescription("Package name") String packageName,
    @JsonPropertyDescription("Package version") String version
  ) {}
}

