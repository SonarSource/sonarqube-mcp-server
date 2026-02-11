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
package org.sonarsource.sonarqube.mcp.serverapi.hotspots.response;

import java.util.List;

public record ShowResponse(String key, String component, String project, String securityCategory, String vulnerabilityProbability,
                           String status, String resolution, Integer line, String message, String assignee, String author,
                           String creationDate, String updateDate, TextRange textRange, List<Flow> flows, List<Comment> comments,
                           List<ChangelogEntry> changelog, List<User> users, Rule rule, String canChangeStatus) {

  public record TextRange(Integer startLine, Integer endLine, Integer startOffset, Integer endOffset) {
  }

  public record Flow(List<Location> locations) {
  }

  public record Location(String component, TextRange textRange, String msg) {
  }

  public record Comment(String key, String login, String htmlText, String markdown, boolean updatable, String createdAt) {
  }

  public record ChangelogEntry(String user, String userName, String creationDate, List<Diff> diffs, boolean isUserActive,
                               String avatar) {
  }

  public record Diff(String key, String oldValue, String newValue) {
  }

  public record User(String login, String name, boolean active) {
  }

  public record Rule(String key, String name, String securityCategory, String vulnerabilityProbability, String riskDescription,
                     String vulnerabilityDescription, String fixRecommendations) {
  }

}
