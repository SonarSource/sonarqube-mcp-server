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
package org.sonarsource.sonarqube.mcp.serverapi.agenticreadiness;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;
import jakarta.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;

public class AgenticReadinessApi {

  public static final String ASSESSMENTS_PATH = "/was-experiments/agentic-readiness-assessments";

  private static final String JSON_CONTENT_TYPE = "application/json";
  private static final Gson GSON = new Gson();

  private final ServerApiHelper helper;

  public AgenticReadinessApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public AssessmentResponse createAssessment(String projectId, @Nullable String branch) {
    var body = new JsonObject();
    body.addProperty("projectId", projectId);
    if (branch != null) {
      body.addProperty("branch", branch);
    }
    try (var response = helper.postApiSubdomain(ASSESSMENTS_PATH, JSON_CONTENT_TYPE, body.toString())) {
      return GSON.fromJson(response.bodyAsString(), AssessmentResponse.class);
    }
  }

  public AssessmentResponse getAssessment(String assessmentId) {
    var path = ASSESSMENTS_PATH + "/" + assessmentId;
    try (var response = helper.getApiSubdomain(path)) {
      return GSON.fromJson(response.bodyAsString(), AssessmentResponse.class);
    }
  }

  public List<AssessmentResponse> listAssessments(String projectId, @Nullable String branch,
    @Nullable Integer pageIndex, @Nullable Integer pageSize) {
    var path = new UrlBuilder(ASSESSMENTS_PATH)
      .addParam("projectId", projectId)
      .addParam("branch", branch)
      .addParam("pageIndex", pageIndex)
      .addParam("pageSize", pageSize)
      .build();
    try (var response = helper.getApiSubdomain(path)) {
      return GSON.fromJson(response.bodyAsString(), AssessmentsListResponse.class).assessments();
    }
  }

  record AssessmentsListResponse(List<AssessmentResponse> assessments) {
  }

  public record AssessmentResponse(
    String id,
    String projectId,
    String createdAt,
    @Nullable String updatedAt,
    String status,
    @Nullable AssessmentResult result,
    @Nullable String branch,
    @Nullable String error,
    @Nullable List<PillarExecution> pillarExecutions) {

    public record AssessmentResult(
      String overallLevel,
      @Nullable String message) {
    }

    public record PillarExecution(
      String id,
      String pillarId,
      String pillarName,
      int pillarNumber,
      String status,
      @Nullable String level,
      @Nullable String message,
      @Nullable Map<String, SubSignal> subSignals,
      @Nullable List<Action> actions,
      @Nullable String error) {
    }

    public record Action(String text) {
    }

    public record SubSignal(
      String level,
      @SerializedName("evidence") List<EvidenceItem> evidence) {
    }

    public record EvidenceItem(
      String text,
      String type) {
    }
  }
}
