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
package org.sonarsource.sonarqube.mcp.serverapi.organizations.history;

import com.google.gson.Gson;
import jakarta.annotation.Nullable;
import java.util.List;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.history.response.IssueCountHistoryResponse;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.history.response.MeasuresHistoryResponse;

public class OrganizationsHistoryApi {

  public static final String MEASURES_HISTORY_PATH = "/organizations/measures-history";
  public static final String ISSUE_COUNT_HISTORY_PATH = "/organizations/issue-count-history";

  private static final Gson GSON = new Gson();

  private final ServerApiHelper helper;

  public OrganizationsHistoryApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public MeasuresHistoryResponse getMeasuresHistory(String entityType, String entityId, List<String> metricKeys, String startDate,
    @Nullable String endDate) {
    var path = new UrlBuilder(MEASURES_HISTORY_PATH)
      .addParam("entityType", entityType)
      .addParam("entityId", entityId)
      .addParam("metricKeys", metricKeys)
      .addParam("startDate", startDate)
      .addParam("endDate", endDate)
      .build();

    try (var response = helper.getApiSubdomain(path)) {
      return GSON.fromJson(response.bodyAsString(), MeasuresHistoryResponse.class);
    }
  }

  public IssueCountHistoryResponse getIssueCountHistory(String entityId, String entityType, String startDate, @Nullable String endDate,
    @Nullable List<String> impacts, @Nullable List<String> issueTypes, @Nullable List<String> ruleKeys, @Nullable List<String> severities,
    @Nullable String sliceBy, @Nullable List<String> statuses) {
    var path = new UrlBuilder(ISSUE_COUNT_HISTORY_PATH)
      .addParam("entityId", entityId)
      .addParam("entityType", entityType)
      .addParam("startDate", startDate)
      .addParam("endDate", endDate)
      .addParam("impacts", impacts)
      .addParam("issueTypes", issueTypes)
      .addParam("ruleKeys", ruleKeys)
      .addParam("severities", severities)
      .addParam("sliceBy", sliceBy)
      .addParam("statuses", statuses)
      .build();

    try (var response = helper.getApiSubdomain(path)) {
      return GSON.fromJson(response.bodyAsString(), IssueCountHistoryResponse.class);
    }
  }

}
