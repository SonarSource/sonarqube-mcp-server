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
package org.sonarsource.sonarqube.mcp.tools.rules;

import io.modelcontextprotocol.spec.McpSchema;
import com.github.tomakehurst.wiremock.http.Body;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.sonarsource.sonarqube.mcp.harness.ReceivedRequest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.rules.RulesApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;

class ListRuleRepositoriesToolTests {

  @Nested
  class WithSonarCloudServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(RulesApi.REPOSITORIES_PATH).willReturn(aResponse().withStatus(403)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(ListRuleRepositoriesTool.TOOL_NAME);

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Forbidden. Please verify your token has the required permissions for this operation.", true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_empty_message_when_no_repositories(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(RulesApi.REPOSITORIES_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "repositories": []
            }
            """.getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(ListRuleRepositoriesTool.TOOL_NAME);

      assertResultEquals(result, """
        {
          "repositories" : [ ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_repositories_with_language_filter(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(RulesApi.REPOSITORIES_PATH + "?language=java")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "repositories": [
                {"key": "java", "name": "SonarJava", "language": "java"},
                {"key": "pmd", "name": "PMD", "language": "java"}
              ]
            }
            """.getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        ListRuleRepositoriesTool.TOOL_NAME,
        Map.of("language", "java"));

      assertResultEquals(result, """
        {
          "repositories" : [ {
            "key" : "java",
            "name" : "SonarJava",
            "language" : "java"
          }, {
            "key" : "pmd",
            "name" : "PMD",
            "language" : "java"
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_repositories_with_query_filter(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(RulesApi.REPOSITORIES_PATH + "?q=sonar")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "repositories": [
                {"key": "java", "name": "SonarJava", "language": "java"},
                {"key": "js", "name": "SonarJS", "language": "js"}
              ]
            }
            """.getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        ListRuleRepositoriesTool.TOOL_NAME,
        Map.of("q", "sonar"));

      assertResultEquals(result, """
        {
          "repositories" : [ {
            "key" : "java",
            "name" : "SonarJava",
            "language" : "java"
          }, {
            "key" : "js",
            "name" : "SonarJS",
            "language" : "js"
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_repositories_with_both_filters(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(RulesApi.REPOSITORIES_PATH + "?language=java&q=sonar")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "repositories": [
                {"key": "java", "name": "SonarJava", "language": "java"}
              ]
            }
            """.getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        ListRuleRepositoriesTool.TOOL_NAME,
        Map.of(
          "language", "java",
          "q", "sonar"));

      assertResultEquals(result, """
        {
          "repositories" : [ {
            "key" : "java",
            "name" : "SonarJava",
            "language" : "java"
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }
  }

  @Nested
  class WithSonarQubeServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(RulesApi.REPOSITORIES_PATH).willReturn(aResponse().withStatus(403)));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(ListRuleRepositoriesTool.TOOL_NAME);

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Forbidden. Please verify your token has the required permissions for this operation.", true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_repositories_with_language_filter(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(RulesApi.REPOSITORIES_PATH + "?language=java")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "repositories": [
                {"key": "java", "name": "SonarJava", "language": "java"},
                {"key": "pmd", "name": "PMD", "language": "java"}
              ]
            }
            """.getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        ListRuleRepositoriesTool.TOOL_NAME,
        Map.of("language", "java"));

      assertResultEquals(result, """
        {
          "repositories" : [ {
            "key" : "java",
            "name" : "SonarJava",
            "language" : "java"
          }, {
            "key" : "pmd",
            "name" : "PMD",
            "language" : "java"
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_repositories_with_both_filters(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(RulesApi.REPOSITORIES_PATH + "?language=java&q=sonar")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "repositories": [
                {"key": "java", "name": "SonarJava", "language": "java"}
              ]
            }
            """.getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        ListRuleRepositoriesTool.TOOL_NAME,
        Map.of(
          "language", "java",
          "q", "sonar"));

      assertResultEquals(result, """
        {
          "repositories" : [ {
            "key" : "java",
            "name" : "SonarJava",
            "language" : "java"
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }
  }
}
