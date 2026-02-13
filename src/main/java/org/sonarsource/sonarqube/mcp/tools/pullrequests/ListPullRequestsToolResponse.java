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
package org.sonarsource.sonarqube.mcp.tools.pullrequests;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

public record ListPullRequestsToolResponse(
  @JsonPropertyDescription("Project key") String projectKey,
  @JsonPropertyDescription("Total number of pull requests") int totalPullRequests,
  @JsonPropertyDescription("List of pull requests for this project") List<PullRequest> pullRequests
) {

  public record PullRequest(
    @JsonPropertyDescription("Pull request key/ID") String key,
    @JsonPropertyDescription("Pull request title") String title,
    @JsonPropertyDescription("Source branch name") String branch
  ) {
  }
}
