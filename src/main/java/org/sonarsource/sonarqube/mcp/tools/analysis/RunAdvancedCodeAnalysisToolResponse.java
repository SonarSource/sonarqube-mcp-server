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
package org.sonarsource.sonarqube.mcp.tools.analysis;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.Nullable;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RunAdvancedCodeAnalysisToolResponse(
  List<Issue> issues,
  @Nullable PatchResult patchResult,
  @Nullable List<AnalysisError> analysisErrors
) {

  public record Issue(
    String id,
    @Nullable String filePath,
    String message,
    String rule,
    @Nullable TextRange textRange,
    @Nullable List<Flow> flows
  ) {
  }

  public record TextRange(
    int startLine,
    int endLine
  ) {
  }

  public record Flow(
    @Nullable String type,
    @Nullable String description,
    @Nullable List<Location> locations
  ) {
  }

  public record Location(
    @Nullable TextRange textRange,
    @Nullable String message,
    @Nullable String file
  ) {
  }

  public record PatchResult(
    List<Issue> newIssues,
    List<Issue> matchedIssues,
    List<String> closedIssues
  ) {
  }

  public record AnalysisError(
    String code,
    String message
  ) {
  }

}
