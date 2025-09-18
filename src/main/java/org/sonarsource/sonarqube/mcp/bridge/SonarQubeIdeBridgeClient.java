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
import org.sonarsource.sonarqube.mcp.http.HttpClient;
import org.sonarsource.sonarqube.mcp.log.McpLogger;

public class SonarQubeIdeBridgeClient {

  private static final McpLogger LOG = McpLogger.getInstance();

  private final String baseUrl;
  private final HttpClient httpClient;
  private final Gson gson;

  public SonarQubeIdeBridgeClient(SonarQubeIdeBridgeConfiguration config, HttpClient httpClient) {
    this.baseUrl = config.getBaseUrl();
    this.httpClient = httpClient;
    this.gson = new GsonBuilder()
      .serializeNulls()
      .create();
  }

  public boolean isAvailable() {
    try {
      var response = httpClient.get(baseUrl + "/sonarlint/api/status");
      return response.isSuccessful();
    } catch (Exception e) {
      LOG.info("SonarQube for IDE availability check failed");
      return false;
    }
  }

  public Optional<AutomaticAnalysisEnablementResponse> requestAutomaticAnalysisEnablement(boolean enabled) {
    LOG.info("Automatic analysis enablement param: " + enabled);
    try (var response = httpClient.post(baseUrl + "/sonarlint/api/analysis/automatic/config?enabled=" + enabled, HttpClient.JSON_CONTENT_TYPE, "")) {
      if (response.isSuccessful()) {
        var analysisResponse = gson.fromJson(response.bodyAsString(), AutomaticAnalysisEnablementResponse.class);
        return Optional.of(analysisResponse);
      } else if (response.code() == 400) {
        LOG.info("Bad analysis request: " + response.bodyAsString());
      } else {
        LOG.info("Analysis request failed: HTTP " + response.code() + " - " + response.bodyAsString());
      }
    } catch (Exception e) {
      LOG.error("Error requesting analysis", e);
    }

    return Optional.empty();
  }

  public Optional<AnalyzeListFilesResponse> requestAnalyzeListFiles(List<String> filePaths) {
    var analysisRequest = new AnalyzeListFilesRequest(filePaths);
    var requestBody = gson.toJson(analysisRequest);
    LOG.info("Analysis request body: " + requestBody);

    try (var response = httpClient.post(baseUrl + "/sonarlint/api/mcp/analyze", HttpClient.JSON_CONTENT_TYPE, gson.toJson(requestBody))) {
      if (response.isSuccessful()) {
        var analysisResponse = gson.fromJson(response.bodyAsString(), AnalyzeListFilesResponse.class);
        return Optional.of(analysisResponse);
      } else if (response.code() == 400) {
        LOG.info("Bad analysis request: " + response.bodyAsString());
      } else {
        LOG.info("Analysis request failed: HTTP " + response.code() + " - " + response.bodyAsString());
      }
    } catch (Exception e) {
      LOG.error("Error requesting analysis", e);
    }

    return Optional.empty();
  }

  public record AnalyzeListFilesRequest(List<String> fileList) {
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

  public record AutomaticAnalysisEnablementResponse(boolean success, String message) {
  }

}
