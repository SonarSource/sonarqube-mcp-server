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
import org.sonarsource.sonarqube.mcp.tools.Description;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalysisToolResponse(
  @Description("List of issues found in the code snippet") List<Issue> issues,
  @Description("Total number of issues") int issueCount
) {
  
  public record Issue(
    @Description("Rule key that triggered the issue") String ruleKey,
    @Description("Primary issue message") String primaryMessage,
    @Description("Issue severity level") String severity,
    @Description("Clean code attribute") String cleanCodeAttribute,
    @Description("Software quality impacts") String impacts,
    @Description("Whether quick fixes are available") boolean hasQuickFixes,
    @Description("Location in the code") @Nullable TextRange textRange
  ) {}
  
  public record TextRange(
    @Description("Starting line number") int startLine,
    @Description("Ending line number") int endLine
  ) {}
}

