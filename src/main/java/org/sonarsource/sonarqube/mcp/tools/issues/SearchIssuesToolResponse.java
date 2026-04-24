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
package org.sonarsource.sonarqube.mcp.tools.issues;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.annotation.Nullable;
import java.util.List;

/**
 * Unified response for the {@link SearchIssuesTool}.
 *
 * <p>Each group ({@code issues}, {@code hotspots}, {@code dependencyRisks}) is populated
 * only when the corresponding type was requested (or defaulted). When a group's underlying
 * search fails or is unavailable (e.g. SCA not enabled), the group is left null and an
 * entry describing the failure is appended to {@code errors}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchIssuesToolResponse(
  @JsonPropertyDescription("Code quality issues (bugs, vulnerabilities, code smells) found by the issues API") @Nullable IssuesGroup issues,
  @JsonPropertyDescription("Security Hotspots requiring review") @Nullable HotspotsGroup hotspots,
  @JsonPropertyDescription("Software Composition Analysis (SCA) dependency risks") @Nullable DependencyRisksGroup dependencyRisks,
  @JsonPropertyDescription("Human-readable error messages for issue types that could not be fetched") @Nullable List<String> errors
) {

  public record IssuesGroup(
    @JsonPropertyDescription("List of issues found in the search") List<Issue> items,
    @JsonPropertyDescription("Pagination information for the results") Paging paging
  ) {}

  public record HotspotsGroup(
    @JsonPropertyDescription("List of Security Hotspots found in the search") List<Hotspot> items,
    @JsonPropertyDescription("Pagination information for the results") Paging paging
  ) {}

  public record DependencyRisksGroup(
    @JsonPropertyDescription("List of dependency risk issues") List<IssueRelease> items
  ) {}

  public record Issue(
    @JsonPropertyDescription("Unique issue identifier") String key,
    @JsonPropertyDescription("Rule that triggered the issue") String rule,
    @JsonPropertyDescription("Project key where the issue was found") String project,
    @JsonPropertyDescription("Component (file) where the issue is located") String component,
    @JsonPropertyDescription("Issue severity level") String severity,
    @JsonPropertyDescription("Current status of the issue") String status,
    @JsonPropertyDescription("Issue description message") String message,
    @JsonPropertyDescription("Clean code attribute associated with the issue") String cleanCodeAttribute,
    @JsonPropertyDescription("Clean code attribute category") String cleanCodeAttributeCategory,
    @JsonPropertyDescription("Author who introduced the issue") String author,
    @JsonPropertyDescription("Date when the issue was created") String creationDate,
    @JsonPropertyDescription("Location of the issue in the source file") @Nullable IssueTextRange textRange
  ) {}

  public record IssueTextRange(
    @JsonPropertyDescription("Starting line number") int startLine,
    @JsonPropertyDescription("Ending line number") int endLine
  ) {}

  public record Hotspot(
    @JsonPropertyDescription("Unique Security Hotspot identifier") String key,
    @JsonPropertyDescription("Component (file) where the Security Hotspot is located") String component,
    @JsonPropertyDescription("Project key where the Security Hotspot was found") String project,
    @JsonPropertyDescription("Security category (e.g., sql-injection, xss, weak-cryptography)") String securityCategory,
    @JsonPropertyDescription("Vulnerability probability (HIGH, MEDIUM, LOW)") String vulnerabilityProbability,
    @JsonPropertyDescription("Review status (TO_REVIEW, REVIEWED)") String status,
    @JsonPropertyDescription("Resolution when status is REVIEWED (FIXED, SAFE, ACKNOWLEDGED)") @Nullable String resolution,
    @JsonPropertyDescription("Line number where the Security Hotspot is located") @Nullable Integer line,
    @JsonPropertyDescription("Security Hotspot description message") String message,
    @JsonPropertyDescription("User assigned to review the Security Hotspot") @Nullable String assignee,
    @JsonPropertyDescription("Author who introduced the Security Hotspot") String author,
    @JsonPropertyDescription("Date when the Security Hotspot was created") String creationDate,
    @JsonPropertyDescription("Date when the Security Hotspot was last updated") String updateDate,
    @JsonPropertyDescription("Location of the Security Hotspot in the source file") @Nullable HotspotTextRange textRange,
    @JsonPropertyDescription("Rule key that triggered this Security Hotspot") @Nullable String ruleKey
  ) {}

  public record HotspotTextRange(
    @JsonPropertyDescription("Starting line number") Integer startLine,
    @JsonPropertyDescription("Ending line number") Integer endLine,
    @JsonPropertyDescription("Starting offset in the line") Integer startOffset,
    @JsonPropertyDescription("Ending offset in the line") Integer endOffset
  ) {}

  public record IssueRelease(
    @JsonPropertyDescription("Issue unique key") String key,
    @JsonPropertyDescription("Issue severity level") String severity,
    @JsonPropertyDescription("Issue type") String type,
    @JsonPropertyDescription("Software quality dimension") String quality,
    @JsonPropertyDescription("Issue status") String status,
    @JsonPropertyDescription("Creation timestamp") String createdAt,
    @JsonPropertyDescription("CVE or vulnerability identifier") @Nullable String vulnerabilityId,
    @JsonPropertyDescription("CVSS score") @Nullable String cvssScore,
    @JsonPropertyDescription("Dependency release information") @Nullable Release release,
    @JsonPropertyDescription("Issue assignee") @Nullable Assignee assignee
  ) {}

  public record Release(
    @JsonPropertyDescription("Package name") String packageName,
    @JsonPropertyDescription("Package version") String version,
    @JsonPropertyDescription("Package manager (npm, maven, etc.)") String packageManager,
    @JsonPropertyDescription("Whether this dependency was newly introduced") @Nullable Boolean newlyIntroduced,
    @JsonPropertyDescription("Direct dependency summary") @Nullable Boolean directSummary
  ) {}

  public record Assignee(
    @JsonPropertyDescription("Assignee name") String name
  ) {}

  public record Paging(
    @JsonPropertyDescription("Current page index (1-based)") int pageIndex,
    @JsonPropertyDescription("Number of items per page") int pageSize,
    @JsonPropertyDescription("Total number of items across all pages") int total
  ) {}
}
