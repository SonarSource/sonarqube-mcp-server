/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
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
import org.sonarsource.sonarqube.mcp.tools.Description;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchDependencyRisksToolResponse(
  @Description("List of dependency risk issues") List<IssueRelease> issuesReleases
) {
  
  public record IssueRelease(
    @Description("Issue unique key") String key,
    @Description("Issue severity level") String severity,
    @Description("Issue type") String type,
    @Description("Software quality dimension") String quality,
    @Description("Issue status") String status,
    @Description("Creation timestamp") String createdAt,
    @Description("CVE or vulnerability identifier") @Nullable String vulnerabilityId,
    @Description("CVSS score") @Nullable String cvssScore,
    @Description("Dependency release information") @Nullable Release release,
    @Description("Issue assignee") @Nullable Assignee assignee
  ) {}
  
  public record Release(
    @Description("Package name") String packageName,
    @Description("Package version") String version,
    @Description("Package manager (npm, maven, etc.)") String packageManager,
    @Description("Whether this dependency was newly introduced") @Nullable Boolean newlyIntroduced,
    @Description("Direct dependency summary") @Nullable Boolean directSummary,
    @Description("Production scope summary") @Nullable Boolean productionScopeSummary
  ) {}
  
  public record Assignee(
    @Description("Assignee name") String name
  ) {}
}

