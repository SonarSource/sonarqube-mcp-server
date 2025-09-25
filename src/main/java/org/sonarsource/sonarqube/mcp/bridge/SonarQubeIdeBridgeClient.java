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
package org.sonarsource.sonarqube.mcp.bridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.commons.api.TextRange;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;
import org.sonarsource.sonarqube.mcp.http.HttpClient;

public class SonarQubeIdeBridgeClient {

  private static final McpLogger LOG = McpLogger.getInstance();

  public static final String AUTOMATIC_ANALYSIS_ENABLEMENT_PATH = "/sonarlint/api/analysis/automatic/config";
  public static final String ANALYZE_LIST_FILES_PATH = "/sonarlint/api/analysis/files";
  public static final String STATUS_PATH = "/sonarlint/api/status";

  private final ServerApiHelper helper;
  private final Gson gson;

  public SonarQubeIdeBridgeClient(ServerApiHelper helper) {
    this.helper = helper;
    this.gson = new GsonBuilder()
      .serializeNulls()
      .create();
  }

  public boolean isAvailable() {
    try {
      var response = helper.rawGet(STATUS_PATH);
      return response.isSuccessful();
    } catch (Exception e) {
      LOG.info("SonarQube for IDE availability check failed, reason: " + e.getMessage());
      return false;
    }
  }

  public AutomaticAnalysisEnablementResponse requestAutomaticAnalysisEnablement(boolean enabled) {
    var url = new UrlBuilder(AUTOMATIC_ANALYSIS_ENABLEMENT_PATH)
      .addParam("enabled", enabled)
      .build();

    try (var response = helper.post(url, HttpClient.JSON_CONTENT_TYPE, "")) {
      if (response.isSuccessful()) {
        return new AutomaticAnalysisEnablementResponse(true, null);
      } else {
        String errorMessage = "Failed to change automatic analysis. Check logs for details.";
        var errorResponse = gson.fromJson(response.bodyAsString(), AutomaticAnalysisEnablementResponseError.class);
        if (errorResponse != null && errorResponse.message() != null) {
          errorMessage = errorResponse.message();
        }
        return new AutomaticAnalysisEnablementResponse(false, errorMessage);
      }
    } catch (Exception e) {
      LOG.error("Error update automatic analysis enablement", e);
      return new AutomaticAnalysisEnablementResponse(false, "Failed to change automatic analysis: " + e.getMessage());
    }
  }

  public Optional<AnalyzeListFilesResponse> requestAnalyzeListFiles(List<String> filePaths) {
    var analysisRequest = new AnalyzeListFilesRequest(filePaths);
    var requestBody = gson.toJson(analysisRequest);

    try (var response = helper.post(ANALYZE_LIST_FILES_PATH, HttpClient.JSON_CONTENT_TYPE, requestBody)) {
      var analysisResponse = gson.fromJson(response.bodyAsString(), AnalyzeListFilesResponse.class);
      return Optional.of(analysisResponse);
    } catch (Exception e) {
      LOG.error("Error requesting file analysis", e);
      return Optional.empty();
    }
  }

  public record AnalyzeListFilesRequest(List<String> fileAbsolutePaths) {
  }

  public record AnalyzeListFilesResponse(List<AnalyzeListFilesIssueResponse> findings) {
  }

  public record AnalyzeListFilesIssueResponse(
    String ruleKey,
    String message,
    @Nullable String severity,
    @Nullable String filePath,
    @Nullable TextRange textRange
  ) {
  }

  public record AutomaticAnalysisEnablementResponseError(String message) {
  }

  public record AutomaticAnalysisEnablementResponse(boolean isSuccessful, @Nullable String errorMessage) {
  }

}
