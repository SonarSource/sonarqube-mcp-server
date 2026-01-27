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
package org.sonarsource.sonarqube.mcp.serverapi.a3s.response;

import java.util.List;
import javax.annotation.Nullable;

public record AnalysisResponse(String id, List<Issue> issues, @Nullable PatchResult patchResult) {

  public record Issue(String id, String filePath, String message, String rule, @Nullable TextRange textRange, @Nullable List<Flow> flows) {
  }

  public record TextRange(Integer startLine, Integer endLine, Integer startOffset, Integer endOffset) {
  }

  public record Flow(String type, @Nullable String description, List<Location> locations) {
  }

  public record Location(@Nullable TextRange textRange, @Nullable String message, @Nullable String file) {
  }

  public record PatchResult(List<Issue> newIssues, List<Issue> matchedIssues, List<String> closedIssues) {
  }
}
