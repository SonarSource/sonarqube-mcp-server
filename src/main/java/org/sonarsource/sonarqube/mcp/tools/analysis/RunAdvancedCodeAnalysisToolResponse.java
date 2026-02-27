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
package org.sonarsource.sonarqube.mcp.tools.analysis;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.annotation.Nullable;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RunAdvancedCodeAnalysisToolResponse(
  @JsonPropertyDescription("List of issues found in the analysis") List<Issue> issues,
  @JsonPropertyDescription("Result of analyzing a patch, showing new, matched, and closed issues") @Nullable PatchResult patchResult,
  @JsonPropertyDescription("Non-fatal errors that occurred during analysis") @Nullable List<AnalysisError> analysisErrors
) {

  public record Issue(
    @JsonPropertyDescription("Unique identifier of the issue") String id,
    @JsonPropertyDescription("Project-relative path of the file containing the issue") @Nullable String filePath,
    @JsonPropertyDescription("Primary message of the issue") String message,
    @JsonPropertyDescription("The rule key (e.g., java:S1854)") String rule,
    @JsonPropertyDescription("Location of the issue in the source file") @Nullable TextRange textRange,
    @JsonPropertyDescription("Secondary locations and flows for the issue") @Nullable List<Flow> flows
  ) {
  }

  public record TextRange(
    @JsonPropertyDescription("Starting line number (1-based)") int startLine,
    @JsonPropertyDescription("Ending line number (1-based)") int endLine
  ) {
  }

  public record Flow(
    @JsonPropertyDescription("The type of flow: UNDEFINED, DATA, or EXECUTION") String type,
    @JsonPropertyDescription("Description of the flow, if any") @Nullable String description,
    @JsonPropertyDescription("List of locations in this flow") List<Location> locations
  ) {
  }

  public record Location(
    @JsonPropertyDescription("Text range of this location") @Nullable TextRange textRange,
    @JsonPropertyDescription("Message explaining this location in the flow") @Nullable String message,
    @JsonPropertyDescription("File path for this location") @Nullable String file
  ) {
  }

  public record PatchResult(
    @JsonPropertyDescription("Issues that appear only in the patched version") List<Issue> newIssues,
    @JsonPropertyDescription("Issues that exist in both original and patched versions") List<Issue> matchedIssues,
    @JsonPropertyDescription("Issue IDs that were closed/fixed by the patch") List<String> closedIssues
  ) {
  }

  public record AnalysisError(
    @JsonPropertyDescription("Error code identifying the type of failure") String code,
    @JsonPropertyDescription("Human-readable description of what went wrong") String message
  ) {
  }

}
