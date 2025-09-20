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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SonarQubeIdeBridgeClientTests {

  private HttpClient httpClient;
  private HttpClient.Response response;
  private SonarQubeIdeBridgeClient underTest;

  @BeforeEach
  void setUp() {
    httpClient = mock(HttpClient.class);
    response = mock(HttpClient.Response.class);
    var config = mock(SonarQubeIdeBridgeConfiguration.class);
    when(config.getBaseUrl()).thenReturn("http://localhost:64120");
    underTest = new SonarQubeIdeBridgeClient(config, httpClient);
  }

  @Nested
  class IsAvailable {
    @Test
    void it_should_return_true_when_status_endpoint_returns_success() {
      when(httpClient.get("http://localhost:64120/sonarlint/api/status")).thenReturn(response);
      when(response.isSuccessful()).thenReturn(true);

      boolean result = underTest.isAvailable();

      assertThat(result).isTrue();
    }

    @Test
    void it_should_return_false_when_status_endpoint_returns_error() {
      when(httpClient.get("http://localhost:64120/sonarlint/api/status")).thenReturn(response);
      when(response.isSuccessful()).thenReturn(false);

      boolean result = underTest.isAvailable();

      assertThat(result).isFalse();
    }

    @Test
    void it_should_return_false_when_exception_is_thrown() {
      when(httpClient.get("http://localhost:64120/sonarlint/api/status")).thenThrow(new RuntimeException("Network error"));

      boolean result = underTest.isAvailable();

      assertThat(result).isFalse();
    }
  }

  @Nested
  class RequestAnalyzeListFiles {
    @Test
    void it_should_return_empty_when_request_fails() {
      when(httpClient.post(eq("http://localhost:64120/sonarlint/api/mcp/analyze"), eq(HttpClient.JSON_CONTENT_TYPE), anyString()))
        .thenReturn(response);
      when(response.isSuccessful()).thenReturn(false);
      when(response.code()).thenReturn(500);
      when(response.bodyAsString()).thenReturn("Internal server error");

      var result = underTest.requestAnalyzeListFiles(List.of("file1.java"));

      assertThat(result).isEmpty();
    }

    @Test
    void it_should_return_empty_when_bad_request() {
      when(httpClient.post(eq("http://localhost:64120/sonarlint/api/mcp/analyze"), eq(HttpClient.JSON_CONTENT_TYPE), anyString()))
        .thenReturn(response);
      when(response.isSuccessful()).thenReturn(false);
      when(response.code()).thenReturn(400);
      when(response.bodyAsString()).thenReturn("Bad request");

      var result = underTest.requestAnalyzeListFiles(List.of("file1.java"));

      assertThat(result).isEmpty();
    }

    @Test
    void it_should_return_empty_when_exception_is_thrown() {
      when(httpClient.post(eq("http://localhost:64120/sonarlint/api/mcp/analyze"), eq(HttpClient.JSON_CONTENT_TYPE), anyString()))
        .thenThrow(new RuntimeException("Network error"));

      var result = underTest.requestAnalyzeListFiles(List.of("file1.java"));

      assertThat(result).isEmpty();
    }

    @Test
    void it_should_return_analysis_response_when_successful_with_no_findings() {
      String jsonResponse = """
        {
          "findings": []
        }
        """;
      
      when(httpClient.post(eq("http://localhost:64120/sonarlint/api/mcp/analyze"), eq(HttpClient.JSON_CONTENT_TYPE), anyString()))
        .thenReturn(response);
      when(response.isSuccessful()).thenReturn(true);
      when(response.bodyAsString()).thenReturn(jsonResponse);

      var result = underTest.requestAnalyzeListFiles(List.of("file1.java"));

      assertThat(result).isPresent();
      assertThat(result.get().findings()).isEmpty();
    }

    @Test
    void it_should_return_analysis_response_when_successful_with_findings() {
      String jsonResponse = """
        {
          "findings": [
            {
              "ruleKey": "java:S1234",
              "message": "Test issue message",
              "severity": "MAJOR",
              "filePath": "src/main/java/Test.java",
              "textRange": {
                "startLine": 10,
                "startOffset": 0,
                "endLine": 10,
                "endOffset": 20
              }
            },
            {
              "ruleKey": "java:S5678",
              "message": "Another issue",
              "severity": "MINOR",
              "filePath": "src/main/java/Another.java",
              "textRange": null
            }
          ]
        }
        """;
      
      when(httpClient.post(eq("http://localhost:64120/sonarlint/api/mcp/analyze"), eq(HttpClient.JSON_CONTENT_TYPE), anyString()))
        .thenReturn(response);
      when(response.isSuccessful()).thenReturn(true);
      when(response.bodyAsString()).thenReturn(jsonResponse);

      var result = underTest.requestAnalyzeListFiles(List.of("file1.java", "file2.java"));

      assertThat(result).isPresent();
      assertThat(result.get().findings()).hasSize(2);
      
      var firstIssue = result.get().findings().getFirst();
      assertThat(firstIssue.ruleKey()).isEqualTo("java:S1234");
      assertThat(firstIssue.message()).isEqualTo("Test issue message");
      assertThat(firstIssue.severity()).isEqualTo("MAJOR");
      assertThat(firstIssue.filePath()).isEqualTo("src/main/java/Test.java");
      assertThat(firstIssue.textRange()).isNotNull();
      assertThat(firstIssue.textRange().getStartLine()).isEqualTo(10);
      assertThat(firstIssue.textRange().getEndLine()).isEqualTo(10);
      
      var secondIssue = result.get().findings().get(1);
      assertThat(secondIssue.ruleKey()).isEqualTo("java:S5678");
      assertThat(secondIssue.message()).isEqualTo("Another issue");
      assertThat(secondIssue.severity()).isEqualTo("MINOR");
      assertThat(secondIssue.filePath()).isEqualTo("src/main/java/Another.java");
      assertThat(secondIssue.textRange()).isNull();
    }
  }

}
