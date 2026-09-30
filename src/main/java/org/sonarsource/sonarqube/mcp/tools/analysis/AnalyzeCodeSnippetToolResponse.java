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
import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalyzeCodeSnippetToolResponse(
  List<Issue> issues,
  int issueCount,
  String deprecationNotice
) {
  
  public record Issue(
    String ruleKey,
    String primaryMessage,
    String severity,
    String cleanCodeAttribute,
    String impacts,
    boolean hasQuickFixes,
    @Nullable TextRange textRange
  ) {}
  
  public record TextRange(
    int startLine,
    int endLine
  ) {}
}

