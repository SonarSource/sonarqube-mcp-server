/*
 * Sonar MCP Server
 * Copyright (C) 2025 SonarSource
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.mcp.serverapi.issues;

import com.google.gson.Gson;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.sonar.mcp.serverapi.ServerApiHelper;

public class IssuesApi {

  public static final String SEARCH_PATH = "/api/issues/search";

  private final ServerApiHelper helper;
  private final String organization;

  public IssuesApi(ServerApiHelper helper, @Nullable String organization) {
    this.helper = helper;
    this.organization = organization;
  }

  public SearchResponse searchIssuesInProject(@Nullable String[] projects) {
    try (var response = helper.get(buildPath(projects))) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, SearchResponse.class);
    }
  }

  private String buildPath(@Nullable String[] projects) {
    var path = new StringBuilder(SEARCH_PATH);
    boolean hasQueryParams = false;

    if (organization != null) {
      path.append("?organization=").append(organization);
      hasQueryParams = true;
    }
    if (projects != null && projects.length > 0) {
      path.append(hasQueryParams ? "&" : "?").append("projects=").append(String.join(",", projects));
    }

    return path.toString();
  }

  public record SearchResponse(Paging paging, List<Issue> issues, List<Component> components, List<Rule> rules, List<User> users) {
  }

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
