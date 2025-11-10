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
package org.sonarsource.sonarqube.mcp.serverapi.issues.response;

import java.util.List;
import java.util.Map;

public record SearchResponse(Paging paging, List<Issue> issues, List<Component> components, List<Rule> rules, List<User> users) {

  public record Paging(Integer pageIndex, Integer pageSize, Integer total) {
  }

  public record Issue(String key, String component, String project, String rule, String issueStatus, String status, String resolution,
                      String severity, String message, Integer line, String hash, String author, String effort, String creationDate,
                      String updateDate, List<String> tags, String type, List<Comment> comments, Map<String, String> attr,
                      List<String> transitions, List<String> actions, TextRange textRange, List<Flow> flows,
                      String ruleDescriptionContextKey, String cleanCodeAttributeCategory, String cleanCodeAttribute,
                      List<Impact> impacts) {
  }

  public record Comment(String key, String login, String htmlText, String markdown, boolean updatable, String createdAt) {
  }

  public record TextRange(Integer startLine, Integer endLine, Integer startOffset, Integer endOffset) {
  }

  public record Flow(List<Location> locations) {
  }

  public record Location(TextRange textRange, String msg) {
  }

  public record Impact(String softwareQuality, String severity) {
  }

  public record Component(String key, boolean enabled, String qualifier, String name, String longName, String path) {
  }

  public record Rule(String key, String name, String status, String lang, String langName) {
  }

  public record User(String login, String name, boolean active, String avatar) {
  }

}
