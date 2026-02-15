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
package org.sonarsource.sonarqube.mcp.tools.dependencyrisks;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.Nullable;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchDependencyRisksToolResponse(
  @JsonPropertyDescription("List of dependency risk issues") List<IssueRelease> issuesReleases
) {
  
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
}

