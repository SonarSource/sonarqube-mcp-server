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
import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Response object for SearchIssuesTool with structured output.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchIssuesToolResponse(
  @JsonPropertyDescription("Matching issues") List<Issue> issues,
  @JsonPropertyDescription("Pagination") Paging paging
) {
  
  public record Issue(
    @JsonPropertyDescription("Issue key") String key,
    @JsonPropertyDescription("Rule that triggered the issue") String rule,
    @JsonPropertyDescription("Project key") String project,
    @JsonPropertyDescription("File key") String component,
    @JsonPropertyDescription("Severity") String severity,
    @JsonPropertyDescription("Status") String status,
    @JsonPropertyDescription("Message") String message,
    @JsonPropertyDescription("Clean code attribute") String cleanCodeAttribute,
    @JsonPropertyDescription("Clean code attribute category") String cleanCodeAttributeCategory,
    @JsonPropertyDescription("Author") String author,
    @JsonPropertyDescription("Created at") String creationDate,
    @JsonPropertyDescription("Source range") @Nullable TextRange textRange
  ) {}
  
  public record TextRange(
    @JsonPropertyDescription("Starting line number") int startLine,
    @JsonPropertyDescription("Ending line number") int endLine
  ) {}
  
  public record Paging(
    @JsonPropertyDescription("Page index") int pageIndex,
    @JsonPropertyDescription("Page size") int pageSize,
    @JsonPropertyDescription("Total items") int total
  ) {}
}
