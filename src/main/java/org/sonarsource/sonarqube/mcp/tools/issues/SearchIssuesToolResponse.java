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
package org.sonarsource.sonarqube.mcp.tools.issues;
import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.sonarsource.sonarqube.mcp.tools.Description;

/**
 * Response object for SearchIssuesTool with structured output.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchIssuesToolResponse(
  @Description("List of issues found in the search") List<Issue> issues,
  @Description("Pagination information for the results") Paging paging
) {
  
  public record Issue(
    @Description("Unique issue identifier") String key,
    @Description("Rule that triggered the issue") String rule,
    @Description("Project key where the issue was found") String project,
    @Description("Component (file) where the issue is located") String component,
    @Description("Issue severity level") String severity,
    @Description("Current status of the issue") String status,
    @Description("Issue description message") String message,
    @Description("Clean code attribute associated with the issue") String cleanCodeAttribute,
    @Description("Clean code attribute category") String cleanCodeAttributeCategory,
    @Description("Author who introduced the issue") String author,
    @Description("Date when the issue was created") String creationDate,
    @Description("Location of the issue in the source file") @Nullable TextRange textRange
  ) {}
  
  public record TextRange(
    @Description("Starting line number") int startLine,
    @Description("Ending line number") int endLine
  ) {}
  
  public record Paging(
    @Description("Current page index (1-based)") int pageIndex,
    @Description("Number of items per page") int pageSize,
    @Description("Total number of items across all pages") int total
  ) {}
}

