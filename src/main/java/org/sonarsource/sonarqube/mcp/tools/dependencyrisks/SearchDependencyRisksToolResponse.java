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
package org.sonarsource.sonarqube.mcp.tools.dependencyrisks;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.Nullable;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchDependencyRisksToolResponse(
  List<IssueRelease> issuesReleases,
  Paging paging
) {
  
  public record IssueRelease(
    String key,
    String severity,
    String type,
    String quality,
    String status,
    String createdAt,
    @Nullable String vulnerabilityId,
    @Nullable String cvssScore,
    @Nullable Release release,
    @Nullable Assignee assignee
  ) {}
  
  public record Release(
    String packageName,
    String version,
    String packageManager,
    @Nullable Boolean newlyIntroduced,
    @Nullable Boolean directSummary
  ) {}
  
  public record Assignee(
    String name
  ) {}

  public record Paging(
    int pageIndex,
    int pageSize,
    int total
  ) {}
}

