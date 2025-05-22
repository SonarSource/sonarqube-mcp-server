/*
 * Sonar MCP Server
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
package org.sonar.mcp.tools.issues;

import com.github.tomakehurst.wiremock.http.Body;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.sonar.mcp.harness.MockWebServer;
import org.sonar.mcp.harness.ReceivedRequest;
import org.sonar.mcp.harness.SonarMcpServerTest;
import org.sonar.mcp.harness.SonarMcpServerTestHarness;
import org.sonar.mcp.serverapi.issues.IssuesApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;

class SearchIssuesToolTests {

  private final MockWebServer mockServer = new MockWebServer();

  @BeforeEach
  void setup() {
    mockServer.start();
  }

  @AfterEach
  void teardown() {
    mockServer.stop();
  }

  @SonarMcpServerTest
  void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarMcpServerTestHarness harness) {
    var mcpClient = harness.newClient(Map.of(
      "SONARQUBE_CLOUD_URL", mockServer.baseUrl(),
      "SONARQUBE_CLOUD_TOKEN", "token",
      "SONARQUBE_CLOUD_ORG", "org"
    ));

    var result = mcpClient.callTool(new McpSchema.CallToolRequest(
      SearchIssuesTool.TOOL_NAME,
      Map.of()));

    assertThat(result)
      .isEqualTo(new McpSchema.CallToolResult("Failed to fetch all projects: Make sure your token is valid.", true));
  }

  @SonarMcpServerTest
  void it_should_succeed_when_no_issues(SonarMcpServerTestHarness harness) {
    mockServer.stubFor(get(IssuesApi.SEARCH_PATH + "?organization=org")
      .willReturn(aResponse().withResponseBody(
        Body.fromJsonBytes("""
          {
              "paging": {
                "pageIndex": 1,
                "pageSize": 100,
                "total": 1
              },
              "issues": [],
              "components": [],
              "rules": [],
              "users": []
            }
          """.getBytes(StandardCharsets.UTF_8))
      )));
    var mcpClient = harness.newClient(Map.of(
      "SONARQUBE_CLOUD_URL", mockServer.baseUrl(),
      "SONARQUBE_CLOUD_TOKEN", "token",
      "SONARQUBE_CLOUD_ORG", "org"
    ));

    var result = mcpClient.callTool(new McpSchema.CallToolRequest(
      SearchIssuesTool.TOOL_NAME,
      Map.of()));

    assertThat(result).isEqualTo(new McpSchema.CallToolResult("No issues were found.", false));
  }

  @SonarMcpServerTest
  void it_should_fetch_issues_for_specific_projects(SonarMcpServerTestHarness harness) {
    var issueKey = "issueKey1";
    var ruleName = "ruleName1";
    var projectName = "projectName1";
    mockServer.stubFor(get(IssuesApi.SEARCH_PATH + "?organization=org&projects=project1,project2")
      .willReturn(aResponse().withResponseBody(
        Body.fromJsonBytes("""
          {
              "paging": {
                "pageIndex": 1,
                "pageSize": 100,
                "total": 1
              },
              "issues": [%s],
              "components": [],
              "rules": [],
              "users": []
            }
          """.formatted(generateIssue(issueKey, ruleName, projectName)).getBytes(StandardCharsets.UTF_8))
      )));
    var mcpClient = harness.newClient(Map.of(
      "SONARQUBE_CLOUD_URL", mockServer.baseUrl(),
      "SONARQUBE_CLOUD_TOKEN", "token",
      "SONARQUBE_CLOUD_ORG", "org"
    ));

    var result = mcpClient.callTool(new McpSchema.CallToolRequest(
      SearchIssuesTool.TOOL_NAME,
      Map.of(SearchIssuesTool.PROJECTS_PROPERTY, new String[]{"project1", "project2"})));

    assertThat(result)
      .isEqualTo(new McpSchema.CallToolResult("""
        Found 1 issues.
        Issue key: %s | Rule name: %s | Project name: %s
        """.formatted(issueKey, ruleName, projectName), false));
    assertThat(mockServer.getReceivedRequests())
      .containsExactly(new ReceivedRequest("Bearer token", ""));
  }

  @SonarMcpServerTest
  void it_should_return_the_issues(SonarMcpServerTestHarness harness) {
    var issueKey = "issueKey1";
    var ruleName = "ruleName1";
    var projectName = "projectName1";
    mockServer.stubFor(get(IssuesApi.SEARCH_PATH + "?organization=org")
      .willReturn(aResponse().withResponseBody(
        Body.fromJsonBytes("""
          {
              "paging": {
                "pageIndex": 1,
                "pageSize": 100,
                "total": 1
              },
              "issues": [%s],
              "components": [],
              "rules": [],
              "users": []
            }
          """.formatted(generateIssue(issueKey, ruleName, projectName)).getBytes(StandardCharsets.UTF_8))
      )));
    var mcpClient = harness.newClient(Map.of(
      "SONARQUBE_CLOUD_URL", mockServer.baseUrl(),
      "SONARQUBE_CLOUD_TOKEN", "token",
      "SONARQUBE_CLOUD_ORG", "org"
    ));

    var result = mcpClient.callTool(new McpSchema.CallToolRequest(
      SearchIssuesTool.TOOL_NAME,
      Map.of()));

    assertThat(result)
      .isEqualTo(new McpSchema.CallToolResult("""
        Found 1 issues.
        Issue key: %s | Rule name: %s | Project name: %s
        """.formatted(issueKey, ruleName, projectName), false));
    assertThat(mockServer.getReceivedRequests())
      .containsExactly(new ReceivedRequest("Bearer token", ""));
  }

  @SonarMcpServerTest
  void it_should_return_issues_from_a_pull_request(SonarMcpServerTestHarness harness) {
    var issueKey = "issueKey1";
    var ruleName = "ruleName1";
    var projectName = "projectName1";
    mockServer.stubFor(get(IssuesApi.SEARCH_PATH + "?organization=org&pullRequest=1")
      .willReturn(aResponse().withResponseBody(
        Body.fromJsonBytes("""
          {
              "paging": {
                "pageIndex": 1,
                "pageSize": 100,
                "total": 1
              },
              "issues": [%s],
              "components": [],
              "rules": [],
              "users": []
            }
          """.formatted(generateIssue(issueKey, ruleName, projectName)).getBytes(StandardCharsets.UTF_8))
      )));
    var mcpClient = harness.newClient(Map.of(
      "SONARQUBE_CLOUD_URL", mockServer.baseUrl(),
      "SONARQUBE_CLOUD_TOKEN", "token",
      "SONARQUBE_CLOUD_ORG", "org"
    ));

    var result = mcpClient.callTool(new McpSchema.CallToolRequest(
      SearchIssuesTool.TOOL_NAME,
      Map.of("pullRequestId", "1")));

    assertThat(result)
      .isEqualTo(new McpSchema.CallToolResult("""
        Found 1 issues.
        Issue key: %s | Rule name: %s | Project name: %s
        """.formatted(issueKey, ruleName, projectName), false));
    assertThat(mockServer.getReceivedRequests())
      .containsExactly(new ReceivedRequest("Bearer token", ""));
  }

  private static String generateIssue(String issueKey, String ruleName, String projectName) {
    return """
        {
        "key": "%s",
        "component": "com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest",
        "project": "%s",
        "rule": "%s",
        "issueStatus": "CLOSED",
        "status": "RESOLVED",
        "resolution": "FALSE-POSITIVE",
        "severity": "MINOR",
        "message": "'3' is a magic number.",
        "line": 81,
        "hash": "a227e508d6646b55a086ee11d63b21e9",
        "author": "Developer 1",
        "effort": "2h1min",
        "creationDate": "2013-05-13T17:55:39+0200",
        "updateDate": "2013-05-13T17:55:39+0200",
        "tags": [
          "bug"
        ],
        "type": "RELIABILITY",
        "comments": [
          {
            "key": "7d7c56f5-7b5a-41b9-87f8-36fa70caa5ba",
            "login": "john.smith",
            "htmlText": "Must be &quot;final&quot;!",
            "markdown": "Must be \\"final\\"!",
            "updatable": false,
            "createdAt": "2013-05-13T18:08:34+0200"
          }
        ],
        "attr": {
          "jira-issue-key": "SONAR-1234"
        },
        "transitions": [
          "unconfirm",
          "resolve",
          "falsepositive"
        ],
        "actions": [
          "comment"
        ],
        "textRange": {
          "startLine": 2,
          "endLine": 2,
          "startOffset": 0,
          "endOffset": 204
        },
        "flows": [],
        "ruleDescriptionContextKey": "spring",
        "cleanCodeAttributeCategory": "INTENTIONAL",
        "cleanCodeAttribute": "CLEAR",
        "impacts": [
          {
            "softwareQuality": "MAINTAINABILITY",
            "severity": "HIGH"
          }
        ]
      }""".formatted(issueKey, projectName, ruleName);
  }

}
