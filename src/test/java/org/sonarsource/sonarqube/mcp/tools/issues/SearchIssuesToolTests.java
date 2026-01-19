/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
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
package org.sonarsource.sonarqube.mcp.tools.issues;

import io.modelcontextprotocol.spec.McpSchema;
import com.github.tomakehurst.wiremock.http.Body;
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
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertSchemaEquals;

class SearchIssuesToolTests {

  @SonarQubeMcpServerTest
  void it_should_validate_output_schema_and_annotations(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient();

    var tool = mcpClient.listTools().stream().filter(t -> t.name().equals(SearchIssuesTool.TOOL_NAME)).findFirst().orElseThrow();

    assertThat(tool.annotations()).isNotNull();
    assertThat(tool.annotations().readOnlyHint()).isTrue();
    assertThat(tool.annotations().openWorldHint()).isTrue();
    assertThat(tool.annotations().idempotentHint()).isFalse();
    assertThat(tool.annotations().destructiveHint()).isFalse();

    assertSchemaEquals(tool.outputSchema(), """
      {
         "type":"object",
         "properties":{
            "issues":{
               "description":"List of issues found in the search",
               "type":"array",
               "items":{
                  "type":"object",
                  "properties":{
                     "author":{
                        "type":"string",
                        "description":"Author who introduced the issue"
                     },
                     "cleanCodeAttribute":{
                        "type":"string",
                        "description":"Clean code attribute associated with the issue"
                     },
                     "cleanCodeAttributeCategory":{
                        "type":"string",
                        "description":"Clean code attribute category"
                     },
                     "component":{
                        "type":"string",
                        "description":"Component (file) where the issue is located"
                     },
                     "creationDate":{
                        "type":"string",
                        "description":"Date when the issue was created"
                     },
                     "key":{
                        "type":"string",
                        "description":"Unique issue identifier"
                     },
                     "message":{
                        "type":"string",
                        "description":"Issue description message"
                     },
                     "project":{
                        "type":"string",
                        "description":"Project key where the issue was found"
                     },
                     "rule":{
                        "type":"string",
                        "description":"Rule that triggered the issue"
                     },
                     "severity":{
                        "type":"string",
                        "description":"Issue severity level"
                     },
                     "status":{
                        "type":"string",
                        "description":"Current status of the issue"
                     },
                     "textRange":{
                        "type":"object",
                        "properties":{
                           "endLine":{
                              "type":"integer",
                              "description":"Ending line number"
                           },
                           "startLine":{
                              "type":"integer",
                              "description":"Starting line number"
                           }
                        },
                        "required":[
                           "endLine",
                           "startLine"
                        ],
                        "description":"Location of the issue in the source file"
                     }
                  },
                  "required":[
                     "author",
                     "cleanCodeAttribute",
                     "cleanCodeAttributeCategory",
                     "component",
                     "creationDate",
                     "key",
                     "message",
                     "project",
                     "rule",
                     "severity",
                     "status"
                  ]
               }
            },
            "paging":{
               "type":"object",
               "properties":{
                  "pageIndex":{
                     "type":"integer",
                     "description":"Current page index (1-based)"
                  },
                  "pageSize":{
                     "type":"integer",
                     "description":"Number of items per page"
                  },
                  "total":{
                     "type":"integer",
                     "description":"Total number of items across all pages"
                  }
               },
               "required":[
                  "pageIndex",
                  "pageSize",
                  "total"
               ],
               "description":"Pagination information for the results"
            }
         },
         "required":[
            "issues",
            "paging"
         ]
      }
      """);
  }

  @Nested
  class WithSonarQubeCloud {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(IssuesApi.SEARCH_PATH + "?organization=org").willReturn(aResponse().withStatus(403)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(SearchIssuesTool.TOOL_NAME);

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true).addTextContent("An error occurred during the tool execution: SonarQube answered with Forbidden. Please verify your token has the required permissions for this operation.").build());
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

      assertResultEquals(result, """
        {
          "issues" : [ {
            "key" : "issueKey1",
            "rule" : "ruleName1",
            "project" : "projectName1",
            "component" : "com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest",
            "severity" : "MINOR, HIGH",
            "status" : "RESOLVED",
            "message" : "'3' is a magic number.",
            "cleanCodeAttribute" : "CLEAR",
            "cleanCodeAttributeCategory" : "INTENTIONAL",
            "author" : "Developer 1",
            "creationDate" : "2013-05-13T17:55:39+0200",
            "textRange" : {
              "startLine" : 2,
              "endLine" : 2
            }
          } ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 100,
            "total" : 1
          }
        }""");
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

      assertResultEquals(result, """
        {
          "issues" : [ {
            "key" : "issueKey1",
            "rule" : "ruleName1",
            "project" : "projectName1",
            "component" : "com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest",
            "severity" : "MINOR, HIGH",
            "status" : "RESOLVED",
            "message" : "'3' is a magic number.",
            "cleanCodeAttribute" : "CLEAR",
            "cleanCodeAttributeCategory" : "INTENTIONAL",
            "author" : "Developer 1",
            "creationDate" : "2013-05-13T17:55:39+0200",
            "textRange" : {
              "startLine" : 2,
              "endLine" : 2
            }
          } ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 100,
            "total" : 1
          }
        }""");
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

      assertResultEquals(result, """
        {
          "issues" : [ {
            "key" : "issueKey1",
            "rule" : "ruleName1",
            "project" : "projectName1",
            "component" : "com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest",
            "severity" : "MINOR, HIGH",
            "status" : "RESOLVED",
            "message" : "'3' is a magic number.",
            "cleanCodeAttribute" : "CLEAR",
            "cleanCodeAttributeCategory" : "INTENTIONAL",
            "author" : "Developer 1",
            "creationDate" : "2013-05-13T17:55:39+0200",
            "textRange" : {
              "startLine" : 2,
              "endLine" : 2
            }
          } ],
          "paging" : {
            "pageIndex" : 2,
            "pageSize" : 100,
            "total" : 200
          }
        }""");
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

      assertResultEquals(result, """
        {
          "issues" : [ {
            "key" : "issueKey1",
            "rule" : "ruleName1",
            "project" : "projectName1",
            "component" : "com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest",
            "severity" : "MINOR, HIGH",
            "status" : "RESOLVED",
            "message" : "'3' is a magic number.",
            "cleanCodeAttribute" : "CLEAR",
            "cleanCodeAttributeCategory" : "INTENTIONAL",
            "author" : "Developer 1",
            "creationDate" : "2013-05-13T17:55:39+0200",
            "textRange" : {
              "startLine" : 2,
              "endLine" : 2
            }
          } ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 100,
            "total" : 200
          }
        }""");
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

      assertResultEquals(result, """
        {
          "issues" : [ {
            "key" : "issueKey1",
            "rule" : "ruleName1",
            "project" : "projectName1",
            "component" : "com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest",
            "severity" : "MINOR, HIGH",
            "status" : "RESOLVED",
            "message" : "'3' is a magic number.",
            "cleanCodeAttribute" : "CLEAR",
            "cleanCodeAttributeCategory" : "INTENTIONAL",
            "author" : "Developer 1",
            "creationDate" : "2013-05-13T17:55:39+0200",
            "textRange" : {
              "startLine" : 2,
              "endLine" : 2
            }
          } ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 20,
            "total" : 1
          }
        }""");
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

      assertResultEquals(result, """
        {
          "issues" : [ {
            "key" : "issueKey1",
            "rule" : "ruleName1",
            "project" : "projectName1",
            "component" : "com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest",
            "severity" : "MINOR, HIGH",
            "status" : "RESOLVED",
            "message" : "'3' is a magic number.",
            "cleanCodeAttribute" : "CLEAR",
            "cleanCodeAttributeCategory" : "INTENTIONAL",
            "author" : "Developer 1",
            "creationDate" : "2013-05-13T17:55:39+0200",
            "textRange" : {
              "startLine" : 2,
              "endLine" : 2
            }
          } ],
          "paging" : {
            "pageIndex" : 2,
            "pageSize" : 50,
            "total" : 175
          }
        }""");
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

      assertResultEquals(result, """
        {
          "issues" : [ {
            "key" : "issueKey1",
            "rule" : "ruleName1",
            "project" : "projectName1",
            "component" : "com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest",
            "severity" : "MINOR",
            "status" : "RESOLVED",
            "message" : "'3' is a magic number.",
            "cleanCodeAttribute" : "CLEAR",
            "cleanCodeAttributeCategory" : "INTENTIONAL",
            "author" : "Developer 1",
            "creationDate" : "2013-05-13T17:55:39+0200"
          } ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 100,
            "total" : 1
          }
        }""");
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

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true).addTextContent("An error occurred during the tool execution: SonarQube answered with Forbidden. Please verify your token has the required permissions for this operation.").build());
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

      assertResultEquals(result, """
        {
          "issues" : [ {
            "key" : "issueKey1",
            "rule" : "ruleName1",
            "project" : "projectName1",
            "component" : "com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest",
            "severity" : "MINOR, HIGH",
            "status" : "RESOLVED",
            "message" : "'3' is a magic number.",
            "cleanCodeAttribute" : "CLEAR",
            "cleanCodeAttributeCategory" : "INTENTIONAL",
            "author" : "Developer 1",
            "creationDate" : "2013-05-13T17:55:39+0200",
            "textRange" : {
              "startLine" : 2,
              "endLine" : 2
            }
          } ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 100,
            "total" : 1
          }
        }""");
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

      assertResultEquals(result, """
        {
          "issues" : [ {
            "key" : "issueKey1",
            "rule" : "ruleName1",
            "project" : "projectName1",
            "component" : "com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest",
            "severity" : "MINOR, HIGH",
            "status" : "RESOLVED",
            "message" : "'3' is a magic number.",
            "cleanCodeAttribute" : "CLEAR",
            "cleanCodeAttributeCategory" : "INTENTIONAL",
            "author" : "Developer 1",
            "creationDate" : "2013-05-13T17:55:39+0200",
            "textRange" : {
              "startLine" : 2,
              "endLine" : 2
            }
          } ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 100,
            "total" : 1
          }
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_issues_from_a_branch(SonarQubeMcpServerTestHarness harness) {
      var issueKey = "issueKey1";
      var ruleName = "ruleName1";
      var projectName = "projectName1";
      harness.getMockSonarQubeServer().stubFor(get(IssuesApi.SEARCH_PATH + "?branch=feature%2Fmy-branch")
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
        Map.of(SearchIssuesTool.BRANCH_PROPERTY, "feature/my-branch"));

      assertResultEquals(result, """
        {
          "issues" : [ {
            "key" : "issueKey1",
            "rule" : "ruleName1",
            "project" : "projectName1",
            "component" : "com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest",
            "severity" : "MINOR, HIGH",
            "status" : "RESOLVED",
            "message" : "'3' is a magic number.",
            "cleanCodeAttribute" : "CLEAR",
            "cleanCodeAttributeCategory" : "INTENTIONAL",
            "author" : "Developer 1",
            "creationDate" : "2013-05-13T17:55:39+0200",
            "textRange" : {
              "startLine" : 2,
              "endLine" : 2
            }
          } ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 100,
            "total" : 1
          }
        }""");
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

      assertResultEquals(result, """
        {
          "issues" : [ {
            "key" : "issueKey1",
            "rule" : "ruleName1",
            "project" : "projectName1",
            "component" : "com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest",
            "severity" : "MINOR, HIGH",
            "status" : "RESOLVED",
            "message" : "'3' is a magic number.",
            "cleanCodeAttribute" : "CLEAR",
            "cleanCodeAttributeCategory" : "INTENTIONAL",
            "author" : "Developer 1",
            "creationDate" : "2013-05-13T17:55:39+0200",
            "textRange" : {
              "startLine" : 2,
              "endLine" : 2
            }
          } ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 100,
            "total" : 1
          }
        }""");
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

      assertResultEquals(result, """
        {
          "issues" : [ {
            "key" : "issueKey1",
            "rule" : "ruleName1",
            "project" : "projectName1",
            "component" : "com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest",
            "severity" : "MINOR, HIGH",
            "status" : "RESOLVED",
            "message" : "'3' is a magic number.",
            "cleanCodeAttribute" : "CLEAR",
            "cleanCodeAttributeCategory" : "INTENTIONAL",
            "author" : "Developer 1",
            "creationDate" : "2013-05-13T17:55:39+0200",
            "textRange" : {
              "startLine" : 2,
              "endLine" : 2
            }
          } ],
          "paging" : {
            "pageIndex" : 2,
            "pageSize" : 100,
            "total" : 200
          }
        }""");
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

      assertResultEquals(result, """
        {
          "issues" : [ {
            "key" : "issueKey1",
            "rule" : "ruleName1",
            "project" : "projectName1",
            "component" : "com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest",
            "severity" : "MINOR, HIGH",
            "status" : "RESOLVED",
            "message" : "'3' is a magic number.",
            "cleanCodeAttribute" : "CLEAR",
            "cleanCodeAttributeCategory" : "INTENTIONAL",
            "author" : "Developer 1",
            "creationDate" : "2013-05-13T17:55:39+0200",
            "textRange" : {
              "startLine" : 2,
              "endLine" : 2
            }
          } ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 100,
            "total" : 200
          }
        }""");
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

      assertResultEquals(result, """
        {
          "issues" : [ {
            "key" : "issueKey1",
            "rule" : "ruleName1",
            "project" : "projectName1",
            "component" : "com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest",
            "severity" : "MINOR, HIGH",
            "status" : "RESOLVED",
            "message" : "'3' is a magic number.",
            "cleanCodeAttribute" : "CLEAR",
            "cleanCodeAttributeCategory" : "INTENTIONAL",
            "author" : "Developer 1",
            "creationDate" : "2013-05-13T17:55:39+0200",
            "textRange" : {
              "startLine" : 2,
              "endLine" : 2
            }
          } ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 20,
            "total" : 1
          }
        }""");
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

      assertResultEquals(result, """
        {
          "issues" : [ {
            "key" : "issueKey1",
            "rule" : "ruleName1",
            "project" : "projectName1",
            "component" : "com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest",
            "severity" : "MINOR, HIGH",
            "status" : "RESOLVED",
            "message" : "'3' is a magic number.",
            "cleanCodeAttribute" : "CLEAR",
            "cleanCodeAttributeCategory" : "INTENTIONAL",
            "author" : "Developer 1",
            "creationDate" : "2013-05-13T17:55:39+0200",
            "textRange" : {
              "startLine" : 2,
              "endLine" : 2
            }
          } ],
          "paging" : {
            "pageIndex" : 2,
            "pageSize" : 50,
            "total" : 200
          }
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_filter_by_impact_software_qualities(SonarQubeMcpServerTestHarness harness) {
      var issueKey = "issueKey1";
      var ruleName = "ruleName1";
      var projectName = "projectName1";
      harness.getMockSonarQubeServer().stubFor(get(IssuesApi.SEARCH_PATH + "?impactSoftwareQualities=MAINTAINABILITY,SECURITY")
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
        Map.of(SearchIssuesTool.IMPACT_SOFTWARE_QUALITIES_PROPERTY, new String[] {"MAINTAINABILITY", "SECURITY"}));

      assertResultEquals(result, """
        {
          "issues" : [ {
            "key" : "issueKey1",
            "rule" : "ruleName1",
            "project" : "projectName1",
            "component" : "com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest",
            "severity" : "MINOR, HIGH",
            "status" : "RESOLVED",
            "message" : "'3' is a magic number.",
            "cleanCodeAttribute" : "CLEAR",
            "cleanCodeAttributeCategory" : "INTENTIONAL",
            "author" : "Developer 1",
            "creationDate" : "2013-05-13T17:55:39+0200",
            "textRange" : {
              "startLine" : 2,
              "endLine" : 2
            }
          } ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 100,
            "total" : 1
          }
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_filter_by_multiple_issue_statuses_as_comma_separated_list(SonarQubeMcpServerTestHarness harness) {
      var issueKey = "issueKey1";
      var ruleName = "ruleName1";
      var projectName = "projectName1";
      // Verify that multiple statuses are passed as comma-separated string in URL
      harness.getMockSonarQubeServer().stubFor(get(IssuesApi.SEARCH_PATH + "?issueStatuses=OPEN,CONFIRMED,FALSE_POSITIVE")
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
        Map.of(SearchIssuesTool.ISSUE_STATUSES_PROPERTY, new String[] {"OPEN", "CONFIRMED", "FALSE_POSITIVE"}));

      assertResultEquals(result, """
        {
          "issues" : [ {
            "key" : "issueKey1",
            "rule" : "ruleName1",
            "project" : "projectName1",
            "component" : "com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest",
            "severity" : "MINOR, HIGH",
            "status" : "RESOLVED",
            "message" : "'3' is a magic number.",
            "cleanCodeAttribute" : "CLEAR",
            "cleanCodeAttributeCategory" : "INTENTIONAL",
            "author" : "Developer 1",
            "creationDate" : "2013-05-13T17:55:39+0200",
            "textRange" : {
              "startLine" : 2,
              "endLine" : 2
            }
          } ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 100,
            "total" : 1
          }
        }""");
      // The test passes only if the URL was correctly formed with comma-separated statuses
      // because we stubbed the exact URL: "?issueStatuses=OPEN,CONFIRMED,FALSE_POSITIVE"
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_fetch_issue_by_key(SonarQubeMcpServerTestHarness harness) {
      var issueKey = "issueKey1";
      var ruleName = "ruleName1";
      var projectName = "projectName1";
      harness.getMockSonarQubeServer().stubFor(get(IssuesApi.SEARCH_PATH + "?issues=issueKey1")
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
        Map.of(SearchIssuesTool.ISSUE_KEY_PROPERTY, "issueKey1"));

      assertResultEquals(result, """
        {
          "issues" : [ {
            "key" : "issueKey1",
            "rule" : "ruleName1",
            "project" : "projectName1",
            "component" : "com.github.kevinsawicki:http-request:com.github.kevinsawicki.http.HttpRequest",
            "severity" : "MINOR, HIGH",
            "status" : "RESOLVED",
            "message" : "'3' is a magic number.",
            "cleanCodeAttribute" : "CLEAR",
            "cleanCodeAttributeCategory" : "INTENTIONAL",
            "author" : "Developer 1",
            "creationDate" : "2013-05-13T17:55:39+0200",
            "textRange" : {
              "startLine" : 2,
              "endLine" : 2
            }
          } ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 100,
            "total" : 1
          }
        }""");
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
        "severity": "MINOR, HIGH",
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
