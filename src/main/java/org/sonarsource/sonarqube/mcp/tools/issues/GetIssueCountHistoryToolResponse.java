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

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

public record GetIssueCountHistoryToolResponse(
  @JsonPropertyDescription("Issue count history for the resolved target entity") List<IssueCountHistory> issueCountHistory
) {

  public record IssueCountHistory(
    @JsonPropertyDescription("Date the issue counts were recorded") String date,
    @JsonPropertyDescription("Issue count distribution at this date") List<Distribution> distribution
  ) {
  }

  public record Distribution(
    @JsonPropertyDescription("Distribution key, or total bucket when no sliceBy was requested") String key,
    @JsonPropertyDescription("Issue count") Integer value
  ) {
  }
}
