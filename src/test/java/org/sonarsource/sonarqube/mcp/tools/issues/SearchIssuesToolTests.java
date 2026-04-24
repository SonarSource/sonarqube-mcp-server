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
package org.sonarsource.sonarqube.mcp.tools.issues;

import com.github.tomakehurst.wiremock.http.Body;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.features.FeaturesApi;
import org.sonarsource.sonarqube.mcp.serverapi.hotspots.HotspotsApi;
import org.sonarsource.sonarqube.mcp.serverapi.issues.IssuesApi;
import org.sonarsource.sonarqube.mcp.serverapi.sca.ScaApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;

class SearchIssuesToolTests {

  @SonarQubeMcpServerTest
  void it_should_expose_tool_with_read_only_annotations(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient();

    var tool = mcpClient.listTools().stream().filter(t -> t.name().equals(SearchIssuesTool.TOOL_NAME)).findFirst().orElseThrow();

    assertThat(tool.name()).isEqualTo("search_sonar_issues");
    assertThat(tool.annotations()).isNotNull();
    assertThat(tool.annotations().readOnlyHint()).isTrue();
    assertThat(tool.annotations().openWorldHint()).isTrue();
    assertThat(tool.annotations().idempotentHint()).isFalse();
    assertThat(tool.annotations().destructiveHint()).isFalse();
    assertThat(tool.inputSchema().properties())
      .containsKeys(
        SearchIssuesTool.ISSUE_TYPES_PROPERTY,
        SearchIssuesTool.PROJECTS_PROPERTY,
        SearchIssuesTool.FILES_PROPERTY,
        SearchIssuesTool.PULL_REQUEST_ID_PROPERTY,
        SearchIssuesTool.BRANCH_KEY_PROPERTY,
        SearchIssuesTool.PAGE_PROPERTY,
        SearchIssuesTool.PAGE_SIZE_PROPERTY,
        SearchIssuesTool.SEVERITIES_PROPERTY,
        SearchIssuesTool.IMPACT_SOFTWARE_QUALITIES_PROPERTY,
        SearchIssuesTool.ISSUE_STATUSES_PROPERTY,
        SearchIssuesTool.ISSUE_KEYS_PROPERTY,
        SearchIssuesTool.HOTSPOT_STATUS_PROPERTY,
        SearchIssuesTool.HOTSPOT_RESOLUTION_PROPERTY);
  }

  @Nested
  class IssuesOnly {

    @SonarQubeMcpServerTest
    void it_should_fetch_issues_for_specific_projects(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(IssuesApi.SEARCH_PATH + "?componentKeys=project1,project2&organization=org")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
                "paging": {"pageIndex": 1, "pageSize": 100, "total": 1},
                "issues": [%s],
                "components": [],
                "rules": [],
                "users": []
              }
            """.formatted(generateIssue("issueKey1", "ruleName1", "projectName1")).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        SearchIssuesTool.TOOL_NAME,
        Map.of(
          SearchIssuesTool.ISSUE_TYPES_PROPERTY, new String[] {SearchIssuesTool.TYPE_ISSUE},
          SearchIssuesTool.PROJECTS_PROPERTY, new String[] {"project1", "project2"}));

      assertResultEquals(result, """
        {
          "issues" : {
            "items" : [ {
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
          }
        }""");
    }

    @SonarQubeMcpServerTest
    void it_should_forward_filters_and_pagination(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer()
        .stubFor(get(IssuesApi.SEARCH_PATH
          + "?components=project1&impactSeverities=HIGH,BLOCKER&impactSoftwareQualities=SECURITY"
          + "&issueStatuses=OPEN,CONFIRMED&issues=k1&p=2&ps=50")
          .willReturn(aResponse().withResponseBody(
            Body.fromJsonBytes("""
              {
                  "paging": {"pageIndex": 2, "pageSize": 50, "total": 200},
                  "issues": [%s],
                  "components": [],
                  "rules": [],
                  "users": []
                }
              """.formatted(generateIssue("issueKey1", "ruleName1", "projectName1")).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        SearchIssuesTool.TOOL_NAME,
        Map.of(
          SearchIssuesTool.ISSUE_TYPES_PROPERTY, new String[] {SearchIssuesTool.TYPE_ISSUE},
          SearchIssuesTool.PROJECTS_PROPERTY, new String[] {"project1"},
          SearchIssuesTool.SEVERITIES_PROPERTY, new String[] {"HIGH", "BLOCKER"},
          SearchIssuesTool.IMPACT_SOFTWARE_QUALITIES_PROPERTY, new String[] {"SECURITY"},
          SearchIssuesTool.ISSUE_STATUSES_PROPERTY, new String[] {"OPEN", "CONFIRMED"},
          SearchIssuesTool.ISSUE_KEYS_PROPERTY, List.of("k1"),
          SearchIssuesTool.PAGE_PROPERTY, 2,
          SearchIssuesTool.PAGE_SIZE_PROPERTY, 50));

      assertThat(result.isError()).isFalse();
    }

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_issues_request_fails(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(IssuesApi.SEARCH_PATH).willReturn(aResponse().withStatus(403)));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        SearchIssuesTool.TOOL_NAME,
        Map.of(SearchIssuesTool.ISSUE_TYPES_PROPERTY, new String[] {SearchIssuesTool.TYPE_ISSUE}));

      assertThat(result.isError()).isFalse();
      assertThat(((McpSchema.TextContent) result.content().get(0)).text())
        .contains("\"errors\"")
        .contains("Failed to search issues");
    }
  }

  @Nested
  class SecurityHotspotsOnly {

    @SonarQubeMcpServerTest
    void it_should_fetch_hotspots_for_a_project(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(HotspotsApi.SEARCH_PATH + "?projectKey=my-project")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
                "paging": {"pageIndex": 1, "pageSize": 100, "total": 1},
                "hotspots": [%s],
                "components": []
              }
            """.formatted(generateHotspot("AXJMFm6ERa2AinNL_0fP")).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        SearchIssuesTool.TOOL_NAME,
        Map.of(
          SearchIssuesTool.ISSUE_TYPES_PROPERTY, new String[] {SearchIssuesTool.TYPE_SECURITY_HOTSPOT},
          SearchIssuesTool.PROJECTS_PROPERTY, new String[] {"my-project"}));

      assertResultEquals(result, """
        {
          "hotspots" : {
            "items" : [ {
              "key" : "AXJMFm6ERa2AinNL_0fP",
              "component" : "com.example:project:src/main/java/com/example/Service.java",
              "project" : "com.example:project",
              "securityCategory" : "sql-injection",
              "vulnerabilityProbability" : "HIGH",
              "status" : "TO_REVIEW",
              "line" : 42,
              "message" : "Make sure using this hardcoded IP address is safe here.",
              "assignee" : "john.doe",
              "author" : "jane.smith",
              "creationDate" : "2023-05-13T17:55:39+0200",
              "updateDate" : "2023-05-14T10:20:15+0200",
              "textRange" : {
                "startLine" : 42,
                "endLine" : 42,
                "startOffset" : 15,
                "endOffset" : 30
              },
              "ruleKey" : "java:S1313"
            } ],
            "paging" : {
              "pageIndex" : 1,
              "pageSize" : 100,
              "total" : 1
            }
          }
        }""");
    }

    @SonarQubeMcpServerTest
    void it_should_report_error_when_projects_is_missing_for_hotspots(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        SearchIssuesTool.TOOL_NAME,
        Map.of(SearchIssuesTool.ISSUE_TYPES_PROPERTY, new String[] {SearchIssuesTool.TYPE_SECURITY_HOTSPOT}));

      assertThat(result.isError()).isFalse();
      assertThat(((McpSchema.TextContent) result.content().get(0)).text())
        .contains("'projects' must contain exactly one project key when SECURITY_HOTSPOT is requested");
    }

    @SonarQubeMcpServerTest
    void it_should_report_error_when_multiple_projects_given_for_hotspots(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        SearchIssuesTool.TOOL_NAME,
        Map.of(
          SearchIssuesTool.ISSUE_TYPES_PROPERTY, new String[] {SearchIssuesTool.TYPE_SECURITY_HOTSPOT},
          SearchIssuesTool.PROJECTS_PROPERTY, new String[] {"p1", "p2"}));

      assertThat(result.isError()).isFalse();
      assertThat(((McpSchema.TextContent) result.content().get(0)).text())
        .contains("'projects' must contain exactly one project key when SECURITY_HOTSPOT is requested");
    }

    @SonarQubeMcpServerTest
    void it_should_forward_hotspot_status_and_pagination(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer()
        .stubFor(get(HotspotsApi.SEARCH_PATH + "?projectKey=my-project&status=REVIEWED&resolution=SAFE&p=2&ps=50")
          .willReturn(aResponse().withResponseBody(
            Body.fromJsonBytes("""
              {
                  "paging": {"pageIndex": 2, "pageSize": 50, "total": 150},
                  "hotspots": [%s],
                  "components": []
                }
              """.formatted(generateHotspot("AXJMFm6ERa2AinNL_0fP")).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        SearchIssuesTool.TOOL_NAME,
        Map.of(
          SearchIssuesTool.ISSUE_TYPES_PROPERTY, new String[] {SearchIssuesTool.TYPE_SECURITY_HOTSPOT},
          SearchIssuesTool.PROJECTS_PROPERTY, new String[] {"my-project"},
          SearchIssuesTool.HOTSPOT_STATUS_PROPERTY, "REVIEWED",
          SearchIssuesTool.HOTSPOT_RESOLUTION_PROPERTY, "SAFE",
          SearchIssuesTool.PAGE_PROPERTY, 2,
          SearchIssuesTool.PAGE_SIZE_PROPERTY, 50));

      assertThat(result.isError()).isFalse();
      assertThat(((McpSchema.TextContent) result.content().get(0)).text())
        .contains("\"hotspots\"");
    }
  }

  @Nested
  class DependencyRisksOnly {

    @SonarQubeMcpServerTest
    void it_should_fetch_dependency_risks_for_a_project(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get("/api/v2" + ScaApi.DEPENDENCY_RISKS_PATH + "?projectKey=my-project")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "issuesReleases": [%s],
              "branches": [],
              "countWithoutFilters": 1,
              "page": {"pageIndex": 1, "pageSize": 100, "total": 1}
            }
            """.formatted(generateIssueRelease()).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        SearchIssuesTool.TOOL_NAME,
        Map.of(
          SearchIssuesTool.ISSUE_TYPES_PROPERTY, new String[] {SearchIssuesTool.TYPE_DEPENDENCY_RISK},
          SearchIssuesTool.PROJECTS_PROPERTY, new String[] {"my-project"}));

      assertResultEquals(result, """
        {
          "dependencyRisks" : {
            "items" : [ {
              "key" : "issue-123",
              "severity" : "HIGH",
              "type" : "VULNERABILITY",
              "quality" : "SECURITY",
              "status" : "OPEN",
              "createdAt" : "2024-01-15T10:30:00Z",
              "vulnerabilityId" : "CVE-2023-1234",
              "cvssScore" : "7.5",
              "release" : {
                "packageName" : "lodash",
                "version" : "1.2.3",
                "packageManager" : "npm",
                "newlyIntroduced" : true,
                "directSummary" : true
              },
              "assignee" : {
                "name" : "John Doe"
              }
            } ]
          }
        }""");
    }

    @SonarQubeMcpServerTest
    void it_should_report_error_when_sca_is_not_enabled_on_sonarcloud(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(ScaApi.FEATURE_ENABLED_PATH + "?organization=org")
        .willReturn(okJson("""
          {"enabled": false}
          """)));
      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        SearchIssuesTool.TOOL_NAME,
        Map.of(
          SearchIssuesTool.ISSUE_TYPES_PROPERTY, new String[] {SearchIssuesTool.TYPE_DEPENDENCY_RISK},
          SearchIssuesTool.PROJECTS_PROPERTY, new String[] {"my-project"}));

      assertThat(result.isError()).isFalse();
      var payload = ((McpSchema.TextContent) result.content().get(0)).text();
      assertThat(payload)
        .contains("\"errors\"")
        .contains("Advanced Security is not enabled")
        .doesNotContain("\"dependencyRisks\"");
    }

    @SonarQubeMcpServerTest
    void it_should_report_error_when_sca_feature_not_in_sonarqube_server(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(FeaturesApi.FEATURES_LIST_PATH).willReturn(okJson("""
        ["prioritized-rules"]
        """)));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        SearchIssuesTool.TOOL_NAME,
        Map.of(
          SearchIssuesTool.ISSUE_TYPES_PROPERTY, new String[] {SearchIssuesTool.TYPE_DEPENDENCY_RISK},
          SearchIssuesTool.PROJECTS_PROPERTY, new String[] {"my-project"}));

      assertThat(result.isError()).isFalse();
      assertThat(((McpSchema.TextContent) result.content().get(0)).text())
        .contains("Advanced Security is not enabled");
    }

    @SonarQubeMcpServerTest
    void it_should_report_error_when_sonarqube_server_version_is_too_old(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of("SONARQUBE_VERSION", "2025.1"));

      var result = mcpClient.callTool(
        SearchIssuesTool.TOOL_NAME,
        Map.of(
          SearchIssuesTool.ISSUE_TYPES_PROPERTY, new String[] {SearchIssuesTool.TYPE_DEPENDENCY_RISK},
          SearchIssuesTool.PROJECTS_PROPERTY, new String[] {"my-project"}));

      assertThat(result.isError()).isFalse();
      assertThat(((McpSchema.TextContent) result.content().get(0)).text())
        .contains("requires SonarQube Server 2025.4");
    }
  }

  @Nested
  class AllTypesAtOnce {

    @SonarQubeMcpServerTest
    void it_should_query_all_three_apis_by_default(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(IssuesApi.SEARCH_PATH + "?components=my-project")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
                "paging": {"pageIndex": 1, "pageSize": 100, "total": 1},
                "issues": [%s],
                "components": [],
                "rules": [],
                "users": []
              }
            """.formatted(generateIssue("issueKey1", "ruleName1", "projectName1")).getBytes(StandardCharsets.UTF_8)))));
      harness.getMockSonarQubeServer().stubFor(get(HotspotsApi.SEARCH_PATH + "?projectKey=my-project")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
                "paging": {"pageIndex": 1, "pageSize": 100, "total": 1},
                "hotspots": [%s],
                "components": []
              }
            """.formatted(generateHotspot("AXJMFm6ERa2AinNL_0fP")).getBytes(StandardCharsets.UTF_8)))));
      harness.getMockSonarQubeServer().stubFor(get("/api/v2" + ScaApi.DEPENDENCY_RISKS_PATH + "?projectKey=my-project")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "issuesReleases": [%s],
              "branches": [],
              "countWithoutFilters": 1,
              "page": {"pageIndex": 1, "pageSize": 100, "total": 1}
            }
            """.formatted(generateIssueRelease()).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        SearchIssuesTool.TOOL_NAME,
        Map.of(SearchIssuesTool.PROJECTS_PROPERTY, new String[] {"my-project"}));

      assertThat(result.isError()).isFalse();
      var payload = ((McpSchema.TextContent) result.content().get(0)).text();
      assertThat(payload)
        .contains("\"issues\"")
        .contains("\"hotspots\"")
        .contains("\"dependencyRisks\"")
        .doesNotContain("\"errors\"");
    }

    @SonarQubeMcpServerTest
    void it_should_record_per_group_errors_and_still_return_other_groups(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(IssuesApi.SEARCH_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
                "paging": {"pageIndex": 1, "pageSize": 100, "total": 0},
                "issues": [],
                "components": [],
                "rules": [],
                "users": []
              }
            """.getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(SearchIssuesTool.TOOL_NAME);

      assertThat(result.isError()).isFalse();
      var payload = ((McpSchema.TextContent) result.content().get(0)).text();
      assertThat(payload)
        .contains("\"issues\"")
        .contains("\"errors\"")
        .contains("SECURITY_HOTSPOT")
        .contains("DEPENDENCY_RISK");
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
        "tags": ["bug"],
        "type": "RELIABILITY",
        "comments": [],
        "attr": {},
        "transitions": ["unconfirm", "resolve", "falsepositive"],
        "actions": ["comment"],
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
        "impacts": [{"softwareQuality": "MAINTAINABILITY", "severity": "HIGH"}]
      }""".formatted(issueKey, projectName, ruleName);
  }

  private static String generateHotspot(String hotspotKey) {
    return """
        {
          "key": "%s",
          "component": "com.example:project:src/main/java/com/example/Service.java",
          "project": "com.example:project",
          "securityCategory": "sql-injection",
          "vulnerabilityProbability": "HIGH",
          "status": "TO_REVIEW",
          "line": 42,
          "message": "Make sure using this hardcoded IP address is safe here.",
          "assignee": "john.doe",
          "author": "jane.smith",
          "creationDate": "2023-05-13T17:55:39+0200",
          "updateDate": "2023-05-14T10:20:15+0200",
          "textRange": {
            "startLine": 42,
            "endLine": 42,
            "startOffset": 15,
            "endOffset": 30
          },
          "ruleKey": "java:S1313",
          "flows": []
        }""".formatted(hotspotKey);
  }

  private static String generateIssueRelease() {
    return """
      {
        "key": "issue-123",
        "severity": "HIGH",
        "originalSeverity": "HIGH",
        "manualSeverity": null,
        "showIncreasedSeverityWarning": false,
        "release": {
          "key": "release-123",
          "branchUuid": "branch-uuid",
          "packageUrl": "pkg:npm/lodash@1.2.3",
          "packageManager": "npm",
          "packageName": "lodash",
          "version": "1.2.3",
          "licenseExpression": "MIT",
          "known": true,
          "knownPackage": true,
          "newlyIntroduced": true,
          "directSummary": true,
          "scopeSummary": "production",
          "productionScopeSummary": true,
          "dependencyFilePaths": ["package.json"]
        },
        "type": "VULNERABILITY",
        "quality": "SECURITY",
        "status": "OPEN",
        "createdAt": "2024-01-15T10:30:00Z",
        "assignee": {
          "login": "john.doe",
          "name": "John Doe",
          "avatar": "avatar.png",
          "active": true
        },
        "commentCount": 2,
        "vulnerabilityId": "CVE-2023-1234",
        "cweIds": ["CWE-89"],
        "cvssScore": "7.5",
        "withdrawn": false,
        "spdxLicenseId": "MIT",
        "transitions": ["CONFIRM", "ACCEPT"],
        "actions": ["COMMENT", "ASSIGN"]
      }
      """;
  }

}
