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

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.http.HttpClient;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SonarQubeIdeBridgeClientTests {

  private ServerApiHelper helper;
  private HttpClient.Response response;
  private SonarQubeIdeBridgeClient underTest;

  @BeforeEach
  void setUp() {
    helper = mock(ServerApiHelper.class);
    response = mock(HttpClient.Response.class);
    underTest = new SonarQubeIdeBridgeClient(helper);
  }

  @Nested
  class IsAvailable {
    @Test
    void it_should_return_true_when_status_endpoint_returns_success() {
      when(helper.rawGet("/sonarlint/api/status")).thenReturn(response);
      when(response.isSuccessful()).thenReturn(true);

      boolean result = underTest.isAvailable();

      assertThat(result).isTrue();
    }

    @Test
    void it_should_return_false_when_status_endpoint_returns_error() {
      when(helper.rawGet("/sonarlint/api/status")).thenReturn(response);
      when(response.isSuccessful()).thenReturn(false);

      boolean result = underTest.isAvailable();

      assertThat(result).isFalse();
    }

    @Test
    void it_should_return_false_when_exception_is_thrown() {
      when(helper.rawGet("/sonarlint/api/status")).thenThrow(new RuntimeException("Network error"));

      boolean result = underTest.isAvailable();

      assertThat(result).isFalse();
    }
  }

  @Nested
  class RequestAnalyzeListFiles {
    @Test
    void it_should_return_empty_when_request_fails() {
      when(helper.post("/sonarlint/api/analysis/files", HttpClient.JSON_CONTENT_TYPE, "{\"fileList\":[\"file1.java\"]}"))
        .thenThrow(new RuntimeException("Server error"));

      var result = underTest.requestAnalyzeListFiles(List.of("file1.java"));

      assertThat(result).isEmpty();
    }

    @Test
    void it_should_return_success_when_analysis_succeeds() {
      when(helper.post("/sonarlint/api/analysis/files", HttpClient.JSON_CONTENT_TYPE, "{\"fileList\":[\"file1.java\"]}"))
        .thenReturn(response);
      when(response.bodyAsString()).thenReturn("{\"findings\":[]}");

      var result = underTest.requestAnalyzeListFiles(List.of("file1.java"));

      assertThat(result).isPresent();
      assertThat(result.get().findings()).isEmpty();
    }

    @Test
    void it_should_return_empty_when_exception_is_thrown() {
      when(helper.post("/sonarlint/api/analysis/files", HttpClient.JSON_CONTENT_TYPE, "{\"fileList\":[\"file1.java\"]}"))
        .thenThrow(new RuntimeException("Network error"));

      var result = underTest.requestAnalyzeListFiles(List.of("file1.java"));

      assertThat(result).isEmpty();
    }
  }

  @Nested
  class RequestAutomaticAnalysisEnablement {
    @Test
    void it_should_return_empty_when_request_fails_with_server_error() {
      when(helper.post("/sonarlint/api/analysis/automatic/config?enabled=true", HttpClient.JSON_CONTENT_TYPE, ""))
        .thenThrow(new RuntimeException("Server error"));

      var result = underTest.requestAutomaticAnalysisEnablement(true);

      assertThat(result).isEmpty();
    }

    @Test
    void it_should_return_success_when_enablement_succeeds() {
      when(helper.post("/sonarlint/api/analysis/automatic/config?enabled=false", HttpClient.JSON_CONTENT_TYPE, ""))
        .thenReturn(response);
      when(response.bodyAsString()).thenReturn("{\"success\":true,\"message\":\"Analysis disabled\"}");

      var result = underTest.requestAutomaticAnalysisEnablement(false);

      assertThat(result).isPresent();
      assertThat(result.get().success()).isTrue();
      assertThat(result.get().message()).isEqualTo("Analysis disabled");
    }

    @Test
    void it_should_return_empty_when_exception_is_thrown() {
      when(helper.post("/sonarlint/api/analysis/automatic/config?enabled=true", HttpClient.JSON_CONTENT_TYPE, ""))
        .thenThrow(new RuntimeException("Network error"));

      var result = underTest.requestAutomaticAnalysisEnablement(true);

      assertThat(result).isEmpty();
    }
  }

}
