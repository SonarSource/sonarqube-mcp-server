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
package org.sonarsource.sonarqube.mcp.serverapi.issues;

import com.google.gson.Gson;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;

import static org.sonarsource.sonarlint.core.http.HttpClient.FORM_URL_ENCODED_CONTENT_TYPE;
import static org.sonarsource.sonarlint.core.serverapi.UrlUtils.urlEncode;

public class IssuesApi {

  public static final String SEARCH_PATH = "/api/issues/search";

  private final ServerApiHelper helper;
  private final String organization;

  public IssuesApi(ServerApiHelper helper, @Nullable String organization) {
    this.helper = helper;
    this.organization = organization;
  }

  public SearchResponse search(@Nullable List<String> projects, @Nullable String pullRequestId) {
    try (var response = helper.get(buildPath(projects, pullRequestId))) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, SearchResponse.class);
    }
  }

  public void doTransition(String issueKey, Transition transition) {
    var body = "issue=" + urlEncode(issueKey) + "&transition=" + urlEncode(transition.getStatus());
    helper.post("/api/issues/do_transition", FORM_URL_ENCODED_CONTENT_TYPE, body);
  }

  private String buildPath(@Nullable List<String> projects, @Nullable String pullRequestId) {
    return new UrlBuilder(SEARCH_PATH)
      .addParam("organization", organization)
      .addParam("projects", projects)
      .addParam("pullRequest", pullRequestId)
      .build();
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
