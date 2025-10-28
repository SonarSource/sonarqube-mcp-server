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
package org.sonarsource.sonarqube.mcp.tools.issues;

import com.github.tomakehurst.wiremock.http.Body;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.sonarsource.sonarqube.mcp.harness.ReceivedRequest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.issues.IssuesApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;

class SearchIssuesToolTests {

  @Nested
  class WithSonarQubeCloud {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(IssuesApi.SEARCH_PATH + "?organization=org").willReturn(aResponse().withStatus(403)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(SearchIssuesTool.TOOL_NAME);

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Forbidden. Please verify your token has the required permissions for this operation.", true));
    }

    @SonarQubeMcpServerTest
    void it_should_fetch_issues_for_specific_projects(SonarQubeMcpServerTestHarness harness) {
      var issueKey = "issueKey1";
      var ruleName = "ruleName1";
      var projectName = "projectName1";
      harness.getMockSonarQubeServer().stubFor(get(IssuesApi.SEARCH_PATH + "?projects=project1,project2&organization=org")
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
            """.formatted(generateIssue(issueKey, ruleName, projectName)).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        SearchIssuesTool.TOOL_NAME,
        Map.of(SearchIssuesTool.PROJECTS_PROPERTY, new String[] {"project1", "project2"}));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult(
          """
            Found 1 issues.
            This response is paginated and this is the page 1 out of 1 total pages. There is a maximum of 100 issues per page.
            Issue key: %s | Rule: %s | Project: %s | Component: com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest | Severity: MINOR | Status: RESOLVED | Message: '3' is a magic number. | Attribute: CLEAR | Category: INTENTIONAL | Author: Developer 1 | Start Line: 2 | End Line: 2 | Created: 2013-05-13T17:55:39+0200
            """
            .formatted(issueKey, ruleName, projectName).trim(),
          false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_filer_by_severity(SonarQubeMcpServerTestHarness harness) {
      var issueKey = "issueKey1";
      var ruleName = "ruleName1";
      var projectName = "projectName1";
      harness.getMockSonarQubeServer().stubFor(get(IssuesApi.SEARCH_PATH + "?projects=project1,project2&impactSeverities=HIGH,BLOCKER&organization=org")
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
            """.formatted(generateIssue(issueKey, ruleName, projectName)).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        SearchIssuesTool.TOOL_NAME,
        Map.of(SearchIssuesTool.PROJECTS_PROPERTY, new String[] {"project1", "project2"},
          SearchIssuesTool.SEVERITIES_PROPERTY, new String[] {"HIGH", "BLOCKER"}));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult(
          """
            Found 1 issues.
            This response is paginated and this is the page 1 out of 1 total pages. There is a maximum of 100 issues per page.
            Issue key: %s | Rule: %s | Project: %s | Component: com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest | Severity: MINOR | Status: RESOLVED | Message: '3' is a magic number. | Attribute: CLEAR | Category: INTENTIONAL | Author: Developer 1 | Start Line: 2 | End Line: 2 | Created: 2013-05-13T17:55:39+0200
            """
            .formatted(issueKey, ruleName, projectName).trim(),
          false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_fetch_issues_with_pagination_when_page_is_provided(SonarQubeMcpServerTestHarness harness) {
      var issueKey = "issueKey1";
      var ruleName = "ruleName1";
      var projectName = "projectName1";
      harness.getMockSonarQubeServer().stubFor(get(IssuesApi.SEARCH_PATH + "?p=2&organization=org")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
                "paging": {
                  "pageIndex": 2,
                  "pageSize": 100,
                  "total": 200
                },
                "issues": [%s],
                "components": [],
                "rules": [],
                "users": []
              }
            """.formatted(generateIssue(issueKey, ruleName, projectName)).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        SearchIssuesTool.TOOL_NAME,
        Map.of(SearchIssuesTool.PAGE_PROPERTY, 2));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult(
          """
            Found 1 issues.
            This response is paginated and this is the page 2 out of 2 total pages. There is a maximum of 100 issues per page.
            Issue key: %s | Rule: %s | Project: %s | Component: com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest | Severity: MINOR | Status: RESOLVED | Message: '3' is a magic number. | Attribute: CLEAR | Category: INTENTIONAL | Author: Developer 1 | Start Line: 2 | End Line: 2 | Created: 2013-05-13T17:55:39+0200
            """
            .formatted(issueKey, ruleName, projectName).trim(),
          false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_fetch_issues_with_pagination_when_no_page_is_provided(SonarQubeMcpServerTestHarness harness) {
      var issueKey = "issueKey1";
      var ruleName = "ruleName1";
      var projectName = "projectName1";
      harness.getMockSonarQubeServer().stubFor(get(IssuesApi.SEARCH_PATH + "?organization=org")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
                "paging": {
                  "pageIndex": 1,
                  "pageSize": 100,
                  "total": 200
                },
                "issues": [%s],
                "components": [],
                "rules": [],
                "users": []
              }
            """.formatted(generateIssue(issueKey, ruleName, projectName)).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(SearchIssuesTool.TOOL_NAME);

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult(
          """
            Found 1 issues.
            This response is paginated and this is the page 1 out of 2 total pages. There is a maximum of 100 issues per page.
            Issue key: %s | Rule: %s | Project: %s | Component: com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest | Severity: MINOR | Status: RESOLVED | Message: '3' is a magic number. | Attribute: CLEAR | Category: INTENTIONAL | Author: Developer 1 | Start Line: 2 | End Line: 2 | Created: 2013-05-13T17:55:39+0200
            """
            .formatted(issueKey, ruleName, projectName).trim(),
          false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_fetch_issues_with_page_size_when_provided(SonarQubeMcpServerTestHarness harness) {
      var issueKey = "issueKey1";
      var ruleName = "ruleName1";
      var projectName = "projectName1";
      harness.getMockSonarQubeServer().stubFor(get(IssuesApi.SEARCH_PATH + "?ps=20&organization=org")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
                "paging": {
                  "pageIndex": 1,
                  "pageSize": 20,
                  "total": 1
                },
                "issues": [%s],
                "components": [],
                "rules": [],
                "users": []
              }
            """.formatted(generateIssue(issueKey, ruleName, projectName)).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        SearchIssuesTool.TOOL_NAME,
        Map.of(SearchIssuesTool.PAGE_SIZE_PROPERTY, 20));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult(
          """
            Found 1 issues.
            This response is paginated and this is the page 1 out of 1 total pages. There is a maximum of 20 issues per page.
            Issue key: %s | Rule: %s | Project: %s | Component: com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest | Severity: MINOR | Status: RESOLVED | Message: '3' is a magic number. | Attribute: CLEAR | Category: INTENTIONAL | Author: Developer 1 | Start Line: 2 | End Line: 2 | Created: 2013-05-13T17:55:39+0200
            """
            .formatted(issueKey, ruleName, projectName).trim(),
          false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_fetch_issues_with_both_page_and_page_size_when_provided(SonarQubeMcpServerTestHarness harness) {
      var issueKey = "issueKey1";
      var ruleName = "ruleName1";
      var projectName = "projectName1";
      harness.getMockSonarQubeServer().stubFor(get(IssuesApi.SEARCH_PATH + "?p=2&ps=50&organization=org")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
                "paging": {
                  "pageIndex": 2,
                  "pageSize": 50,
                  "total": 175
                },
                "issues": [%s],
                "components": [],
                "rules": [],
                "users": []
              }
            """.formatted(generateIssue(issueKey, ruleName, projectName)).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        SearchIssuesTool.TOOL_NAME,
        Map.of(
          SearchIssuesTool.PAGE_PROPERTY, 2,
          SearchIssuesTool.PAGE_SIZE_PROPERTY, 50));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult(
          """
            Found 1 issues.
            This response is paginated and this is the page 2 out of 4 total pages. There is a maximum of 50 issues per page.
            Issue key: %s | Rule: %s | Project: %s | Component: com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest | Severity: MINOR | Status: RESOLVED | Message: '3' is a magic number. | Attribute: CLEAR | Category: INTENTIONAL | Author: Developer 1 | Start Line: 2 | End Line: 2 | Created: 2013-05-13T17:55:39+0200
            """
            .formatted(issueKey, ruleName, projectName).trim(),
          false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_handle_issues_with_null_text_range(SonarQubeMcpServerTestHarness harness) {
      var issueKey = "issueKey1";
      var ruleName = "ruleName1";
      var projectName = "projectName1";
      harness.getMockSonarQubeServer().stubFor(get(IssuesApi.SEARCH_PATH + "?organization=org")
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
            """.formatted(generateIssueWithNullTextRange(issueKey, ruleName, projectName)).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(SearchIssuesTool.TOOL_NAME);

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult(
          """
            Found 1 issues.
            This response is paginated and this is the page 1 out of 1 total pages. There is a maximum of 100 issues per page.
            Issue key: %s | Rule: %s | Project: %s | Component: com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest | Severity: MINOR | Status: RESOLVED | Message: '3' is a magic number. | Attribute: CLEAR | Category: INTENTIONAL | Author: Developer 1 | Created: 2013-05-13T17:55:39+0200
            """
            .formatted(issueKey, ruleName, projectName).trim(),
          false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }
  }

  @Nested
  class WithSonarQubeServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(IssuesApi.SEARCH_PATH).willReturn(aResponse().withStatus(403)));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(SearchIssuesTool.TOOL_NAME);

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Forbidden. Please verify your token has the required permissions for this operation.", true));
    }

    @SonarQubeMcpServerTest
    void it_should_fetch_issues_for_specific_projects(SonarQubeMcpServerTestHarness harness) {
      var issueKey = "issueKey1";
      var ruleName = "ruleName1";
      var projectName = "projectName1";
      harness.getMockSonarQubeServer().stubFor(get(IssuesApi.SEARCH_PATH + "?projects=project1,project2")
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
            """.formatted(generateIssue(issueKey, ruleName, projectName)).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        SearchIssuesTool.TOOL_NAME,
        Map.of(SearchIssuesTool.PROJECTS_PROPERTY, new String[] {"project1", "project2"}));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult(
          """
            Found 1 issues.
            This response is paginated and this is the page 1 out of 1 total pages. There is a maximum of 100 issues per page.
            Issue key: %s | Rule: %s | Project: %s | Component: com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest | Severity: MINOR | Status: RESOLVED | Message: '3' is a magic number. | Attribute: CLEAR | Category: INTENTIONAL | Author: Developer 1 | Start Line: 2 | End Line: 2 | Created: 2013-05-13T17:55:39+0200
            """
            .formatted(issueKey, ruleName, projectName).trim(),
          false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_filer_by_severity(SonarQubeMcpServerTestHarness harness) {
      var issueKey = "issueKey1";
      var ruleName = "ruleName1";
      var projectName = "projectName1";
      harness.getMockSonarQubeServer().stubFor(get(IssuesApi.SEARCH_PATH + "?projects=project1,project2&impactSeverities=HIGH,BLOCKER")
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
            """.formatted(generateIssue(issueKey, ruleName, projectName)).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        SearchIssuesTool.TOOL_NAME,
        Map.of(SearchIssuesTool.PROJECTS_PROPERTY, new String[] {"project1", "project2"},
          SearchIssuesTool.SEVERITIES_PROPERTY, new String[] {"HIGH", "BLOCKER"}));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult(
          """
            Found 1 issues.
            This response is paginated and this is the page 1 out of 1 total pages. There is a maximum of 100 issues per page.
            Issue key: %s | Rule: %s | Project: %s | Component: com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest | Severity: MINOR | Status: RESOLVED | Message: '3' is a magic number. | Attribute: CLEAR | Category: INTENTIONAL | Author: Developer 1 | Start Line: 2 | End Line: 2 | Created: 2013-05-13T17:55:39+0200
            """
            .formatted(issueKey, ruleName, projectName).trim(),
          false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_issues_and_files_from_a_pull_request(SonarQubeMcpServerTestHarness harness) {
      var issueKey = "issueKey1";
      var ruleName = "ruleName1";
      var projectName = "projectName1";
      harness.getMockSonarQubeServer().stubFor(get(IssuesApi.SEARCH_PATH + "?pullRequest=1")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
                "paging": {
                  "pageIndex": 1,
                  "pageSize": 100,
                  "total": 1
                },
                "issues": [%s],
                "components": [
                    {
                      "key": "com.github.kevinsawicki:http-request:src/main/java/com/github/kevinsawicki/http/HttpRequest.java",
                      "enabled": true,
                      "qualifier": "FIL",
                      "name": "HttpRequest.java",
                      "longName": "src/main/java/com/github/kevinsawicki/http/HttpRequest.java",
                      "path": "src/main/java/com/github/kevinsawicki/http/HttpRequest.java"
                    },
                    {
                      "key": "com.github.kevinsawicki:http-request",
                      "enabled": true,
                      "qualifier": "TRK",
                      "name": "http-request",
                      "longName": "http-request"
                    }
                ],
                "rules": [],
                "users": []
              }
            """.formatted(generateIssue(issueKey, ruleName, projectName)).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        SearchIssuesTool.TOOL_NAME,
        Map.of("pullRequestId", "1"));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult(
          """
            Found 1 issues.
            This response is paginated and this is the page 1 out of 1 total pages. There is a maximum of 100 issues per page.
            Issue key: %s | Rule: %s | Project: %s | Component: com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest | Severity: MINOR | Status: RESOLVED | Message: '3' is a magic number. | Attribute: CLEAR | Category: INTENTIONAL | Author: Developer 1 | Start Line: 2 | End Line: 2 | Created: 2013-05-13T17:55:39+0200
            """
            .formatted(issueKey, ruleName, projectName).trim(),
          false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_fetch_issues_with_pagination_when_page_is_provided(SonarQubeMcpServerTestHarness harness) {
      var issueKey = "issueKey1";
      var ruleName = "ruleName1";
      var projectName = "projectName1";
      harness.getMockSonarQubeServer().stubFor(get(IssuesApi.SEARCH_PATH + "?p=2&organization=org")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
                "paging": {
                  "pageIndex": 2,
                  "pageSize": 100,
                  "total": 200
                },
                "issues": [%s],
                "components": [],
                "rules": [],
                "users": []
              }
            """.formatted(generateIssue(issueKey, ruleName, projectName)).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        SearchIssuesTool.TOOL_NAME,
        Map.of(SearchIssuesTool.PAGE_PROPERTY, 2));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult(
          """
            Found 1 issues.
            This response is paginated and this is the page 2 out of 2 total pages. There is a maximum of 100 issues per page.
            Issue key: %s | Rule: %s | Project: %s | Component: com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest | Severity: MINOR | Status: RESOLVED | Message: '3' is a magic number. | Attribute: CLEAR | Category: INTENTIONAL | Author: Developer 1 | Start Line: 2 | End Line: 2 | Created: 2013-05-13T17:55:39+0200
            """
            .formatted(issueKey, ruleName, projectName).trim(),
          false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_fetch_issues_with_pagination_when_no_page_is_provided(SonarQubeMcpServerTestHarness harness) {
      var issueKey = "issueKey1";
      var ruleName = "ruleName1";
      var projectName = "projectName1";
      harness.getMockSonarQubeServer().stubFor(get(IssuesApi.SEARCH_PATH + "?organization=org")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
                "paging": {
                  "pageIndex": 1,
                  "pageSize": 100,
                  "total": 200
                },
                "issues": [%s],
                "components": [],
                "rules": [],
                "users": []
              }
            """.formatted(generateIssue(issueKey, ruleName, projectName)).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(SearchIssuesTool.TOOL_NAME);

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult(
          """
            Found 1 issues.
            This response is paginated and this is the page 1 out of 2 total pages. There is a maximum of 100 issues per page.
            Issue key: %s | Rule: %s | Project: %s | Component: com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest | Severity: MINOR | Status: RESOLVED | Message: '3' is a magic number. | Attribute: CLEAR | Category: INTENTIONAL | Author: Developer 1 | Start Line: 2 | End Line: 2 | Created: 2013-05-13T17:55:39+0200
            """
            .formatted(issueKey, ruleName, projectName).trim(),
          false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_fetch_issues_with_page_size_when_provided(SonarQubeMcpServerTestHarness harness) {
      var issueKey = "issueKey1";
      var ruleName = "ruleName1";
      var projectName = "projectName1";
      harness.getMockSonarQubeServer().stubFor(get(IssuesApi.SEARCH_PATH + "?ps=20")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
                "paging": {
                  "pageIndex": 1,
                  "pageSize": 20,
                  "total": 1
                },
                "issues": [%s],
                "components": [],
                "rules": [],
                "users": []
              }
            """.formatted(generateIssue(issueKey, ruleName, projectName)).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        SearchIssuesTool.TOOL_NAME,
        Map.of(SearchIssuesTool.PAGE_SIZE_PROPERTY, 20));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult(
          """
            Found 1 issues.
            This response is paginated and this is the page 1 out of 1 total pages. There is a maximum of 20 issues per page.
            Issue key: %s | Rule: %s | Project: %s | Component: com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest | Severity: MINOR | Status: RESOLVED | Message: '3' is a magic number. | Attribute: CLEAR | Category: INTENTIONAL | Author: Developer 1 | Start Line: 2 | End Line: 2 | Created: 2013-05-13T17:55:39+0200
            """
            .formatted(issueKey, ruleName, projectName).trim(),
          false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_fetch_issues_with_both_page_and_page_size_when_provided(SonarQubeMcpServerTestHarness harness) {
      var issueKey = "issueKey1";
      var ruleName = "ruleName1";
      var projectName = "projectName1";
      harness.getMockSonarQubeServer().stubFor(get(IssuesApi.SEARCH_PATH + "?p=2&ps=50")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
                "paging": {
                  "pageIndex": 2,
                  "pageSize": 50,
                  "total": 200
                },
                "issues": [%s],
                "components": [],
                "rules": [],
                "users": []
              }
            """.formatted(generateIssue(issueKey, ruleName, projectName)).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        SearchIssuesTool.TOOL_NAME,
        Map.of(
          SearchIssuesTool.PAGE_PROPERTY, 2,
          SearchIssuesTool.PAGE_SIZE_PROPERTY, 50));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult(
          """
            Found 1 issues.
            This response is paginated and this is the page 2 out of 4 total pages. There is a maximum of 50 issues per page.
            Issue key: %s | Rule: %s | Project: %s | Component: com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest | Severity: MINOR | Status: RESOLVED | Message: '3' is a magic number. | Attribute: CLEAR | Category: INTENTIONAL | Author: Developer 1 | Start Line: 2 | End Line: 2 | Created: 2013-05-13T17:55:39+0200
            """
            .formatted(issueKey, ruleName, projectName).trim(),
          false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }
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

  private static String generateIssueWithNullTextRange(String issueKey, String ruleName, String projectName) {
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
        "textRange": null,
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
