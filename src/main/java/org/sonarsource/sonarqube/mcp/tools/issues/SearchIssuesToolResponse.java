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
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Response object for SearchIssuesTool with structured output.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchIssuesToolResponse(
  @JsonPropertyDescription("List of issues found in the search") List<Issue> issues,
  @JsonPropertyDescription("Pagination information for the results") Paging paging
) {
  
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
    @JsonPropertyDescription("Location of the issue in the source file") @Nullable TextRange textRange
  ) {}
  
  public record TextRange(
    @JsonPropertyDescription("Starting line number") int startLine,
    @JsonPropertyDescription("Ending line number") int endLine
  ) {}
  
  public record Paging(
    @JsonPropertyDescription("Current page index (1-based)") int pageIndex,
    @JsonPropertyDescription("Number of items per page") int pageSize,
    @JsonPropertyDescription("Total number of items across all pages") int total
  ) {}
}

