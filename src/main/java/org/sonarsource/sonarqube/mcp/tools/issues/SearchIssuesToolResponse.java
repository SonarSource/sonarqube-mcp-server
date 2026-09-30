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

/**
 * Response object for SearchIssuesTool.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchIssuesToolResponse(
  List<Issue> issues,
  Paging paging
) {
  
  public record Issue(
    String key,
    String rule,
    String project,
    String component,
    String severity,
    String status,
    String message,
    String cleanCodeAttribute,
    String cleanCodeAttributeCategory,
    String author,
    String creationDate,
    @Nullable TextRange textRange
  ) {}
  
  public record TextRange(
    int startLine,
    int endLine
  ) {}
  
  public record Paging(
    int pageIndex,
    int pageSize,
    int total
  ) {}
}

