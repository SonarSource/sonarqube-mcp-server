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
package org.sonarsource.sonarqube.mcp.tools.languages;

import com.github.tomakehurst.wiremock.http.Body;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.sonarsource.sonarqube.mcp.harness.MockWebServer;
import org.sonarsource.sonarqube.mcp.harness.ReceivedRequest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.languages.LanguagesApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;

class ListLanguagesToolTests {

  @Nested
  class MissingPrerequisite {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_sonarqube_token_is_missing(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        ListLanguagesTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Not connected to SonarQube, please provide valid credentials", true));
    }

  }

  @Nested
  class WithSonarCloudServer {

    private final MockWebServer mockServer = new MockWebServer();

    @BeforeEach
    void setup() {
      mockServer.start();
    }

    @AfterEach
    void teardown() {
      mockServer.stop();
    }

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_URL", mockServer.baseUrl(),
        "SONARQUBE_TOKEN", "token",
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        ListLanguagesTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: Make sure your token is valid.", true));
    }

    @SonarQubeMcpServerTest
    void it_should_show_error_when_request_fails(SonarQubeMcpServerTestHarness harness) {
      mockServer.stubFor(get(LanguagesApi.LIST_PATH).willReturn(aResponse().withStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_URL", mockServer.baseUrl(),
        "SONARQUBE_TOKEN", "token",
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        ListLanguagesTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Error 500 on " + mockServer.baseUrl() + "/api" +
          "/languages/list", true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_languages_list(SonarQubeMcpServerTestHarness harness) {
      mockServer.stubFor(get(LanguagesApi.LIST_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generatePayload().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_URL", mockServer.baseUrl(),
        "SONARQUBE_TOKEN", "token",
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        ListLanguagesTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          Supported Languages:

          C (c)
          C++ (cpp)
          Java (java)
          JavaScript (js)
          Python (python)""", false));
      assertThat(mockServer.getReceivedRequests())
        .containsExactly(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_languages_list_with_query(SonarQubeMcpServerTestHarness harness) {
      mockServer.stubFor(get(LanguagesApi.LIST_PATH + "?q=java")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateFilteredPayload().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_URL", mockServer.baseUrl(),
        "SONARQUBE_TOKEN", "token",
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        ListLanguagesTool.TOOL_NAME,
        Map.of(ListLanguagesTool.QUERY_PROPERTY, "java")));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          Supported Languages:

          Java (java)
          JavaScript (js)""", false));
      assertThat(mockServer.getReceivedRequests())
        .containsExactly(new ReceivedRequest("Bearer token", ""));
    }
  }

  @Nested
  class WithSonarQubeServer {

    private final MockWebServer mockServer = new MockWebServer();

    @BeforeEach
    void setup() {
      mockServer.start();
    }

    @AfterEach
    void teardown() {
      mockServer.stop();
    }

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      mockServer.stubFor(get(LanguagesApi.LIST_PATH).willReturn(aResponse().withStatus(HttpStatus.SC_FORBIDDEN)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_URL", mockServer.baseUrl(),
        "SONARQUBE_TOKEN", "token"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        ListLanguagesTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Forbidden", true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_languages_list(SonarQubeMcpServerTestHarness harness) {
      mockServer.stubFor(get(LanguagesApi.LIST_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generatePayload().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_URL", mockServer.baseUrl(),
        "SONARQUBE_TOKEN", "token"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        ListLanguagesTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          Supported Languages:

          C (c)
          C++ (cpp)
          Java (java)
          JavaScript (js)
          Python (python)""", false));
      assertThat(mockServer.getReceivedRequests())
        .containsExactly(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_languages_list_with_query(SonarQubeMcpServerTestHarness harness) {
      mockServer.stubFor(get(LanguagesApi.LIST_PATH + "?q=java")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateFilteredPayload().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_URL", mockServer.baseUrl(),
        "SONARQUBE_TOKEN", "token"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        ListLanguagesTool.TOOL_NAME,
        Map.of(ListLanguagesTool.QUERY_PROPERTY, "java")));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          Supported Languages:

          Java (java)
          JavaScript (js)""", false));
      assertThat(mockServer.getReceivedRequests())
        .containsExactly(new ReceivedRequest("Bearer token", ""));
    }
  }

  private static String generatePayload() {
    return """
        {
          "languages": [
            {"key": "c", "name": "C"},
            {"key": "cpp", "name": "C++"},
            {"key": "java", "name": "Java"},
            {"key": "js", "name": "JavaScript"},
            {"key": "python", "name": "Python"}
          ]
        }""";
  }

  private static String generateFilteredPayload() {
    return """
        {
          "languages": [
            {"key": "java", "name": "Java"},
            {"key": "js", "name": "JavaScript"}
          ]
        }""";
  }
} 
