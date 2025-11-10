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
package org.sonarsource.sonarqube.mcp.tools.analysis;
import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalysisToolResponse(
  @JsonPropertyDescription("List of issues found in the code snippet") List<Issue> issues,
  @JsonPropertyDescription("Total number of issues") int issueCount
) {
  
  public record Issue(
    @JsonPropertyDescription("Rule key that triggered the issue") String ruleKey,
    @JsonPropertyDescription("Primary issue message") String primaryMessage,
    @JsonPropertyDescription("Issue severity level") String severity,
    @JsonPropertyDescription("Clean code attribute") String cleanCodeAttribute,
    @JsonPropertyDescription("Software quality impacts") String impacts,
    @JsonPropertyDescription("Whether quick fixes are available") boolean hasQuickFixes,
    @JsonPropertyDescription("Location in the code") @Nullable TextRange textRange
  ) {}
  
  public record TextRange(
    @JsonPropertyDescription("Starting line number") int startLine,
    @JsonPropertyDescription("Ending line number") int endLine
  ) {}
}

