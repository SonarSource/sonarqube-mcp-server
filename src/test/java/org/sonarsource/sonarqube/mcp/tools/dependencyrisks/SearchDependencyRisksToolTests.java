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
package org.sonarsource.sonarqube.mcp.tools.dependencyrisks;

import com.github.tomakehurst.wiremock.http.Body;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.sonarsource.sonarqube.mcp.harness.ReceivedRequest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.features.FeaturesApi;
import org.sonarsource.sonarqube.mcp.serverapi.sca.ScaApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertSchemaEquals;

class SearchDependencyRisksToolTests {

  @SonarQubeMcpServerTest
  void it_should_validate_output_schema_and_annotations(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient();

    var tool = mcpClient.listTools().stream().filter(t -> t.name().equals(SearchDependencyRisksTool.TOOL_NAME)).findFirst().orElseThrow();

    assertThat(tool.annotations()).isNotNull();
    assertThat(tool.annotations().readOnlyHint()).isTrue();
    assertThat(tool.annotations().openWorldHint()).isTrue();
    assertThat(tool.annotations().idempotentHint()).isFalse();
    assertThat(tool.annotations().destructiveHint()).isFalse();

    assertSchemaEquals(tool.outputSchema(), """
      {
         "type":"object",
         "properties":{
            "issuesReleases":{
               "description":"List of dependency risk issues",
               "type":"array",
               "items":{
                  "type":"object",
                  "properties":{
                     "assignee":{
                        "type":"object",
                        "properties":{
                           "name":{
                              "type":"string",
                              "description":"Assignee name"
                           }
                        },
                        "required":[
                           "name"
                        ],
                        "description":"Issue assignee"
                     },
                     "createdAt":{
                        "type":"string",
                        "description":"Creation timestamp"
                     },
                     "cvssScore":{
                        "type":"string",
                        "description":"CVSS score"
                     },
                     "key":{
                        "type":"string",
                        "description":"Issue unique key"
                     },
                     "quality":{
                        "type":"string",
                        "description":"Software quality dimension"
                     },
                     "release":{
                        "type":"object",
                        "properties":{
                           "directSummary":{
                              "type":"boolean",
                              "description":"Direct dependency summary"
                           },
                           "newlyIntroduced":{
                              "type":"boolean",
                              "description":"Whether this dependency was newly introduced"
                           },
                           "packageManager":{
                              "type":"string",
                              "description":"Package manager (npm, maven, etc.)"
                           },
                           "packageName":{
                              "type":"string",
                              "description":"Package name"
                           },
                           "productionScopeSummary":{
                              "type":"boolean",
                              "description":"Production scope summary"
                           },
                           "version":{
                              "type":"string",
                              "description":"Package version"
                           }
                        },
                        "required":[
                           "packageManager",
                           "packageName",
                           "version"
                        ],
                        "description":"Dependency release information"
                     },
                     "severity":{
                        "type":"string",
                        "description":"Issue severity level"
                     },
                     "status":{
                        "type":"string",
                        "description":"Issue status"
                     },
                     "type":{
                        "type":"string",
                        "description":"Issue type"
                     },
                     "vulnerabilityId":{
                        "type":"string",
                        "description":"CVE or vulnerability identifier"
                     }
                  },
                  "required":[
                     "createdAt",
                     "key",
                     "quality",
                     "severity",
                     "status",
                     "type"
                  ]
               }
            }
         },
         "required":[
            "issuesReleases"
         ]
      }
      """);
  }

  @Nested
  class WithSonarCloudServer {

    @SonarQubeMcpServerTest
    void it_should_find_tool_even_if_sca_is_disabled(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(ScaApi.FEATURE_ENABLED_PATH + "?organization=org").willReturn(aResponse().withResponseBody(
        Body.fromJsonBytes("""
          {
            "enabled": false
          }
          """.getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_CLOUD_URL", harness.getMockSonarQubeServer().baseUrl(),
        "SONARQUBE_ORG", "org"));

      assertThat(mcpClient.listTools())
        .extracting(McpSchema.Tool::name)
        .contains(SearchDependencyRisksTool.TOOL_NAME);
    }

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_sca_feature_is_disabled(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(ScaApi.FEATURE_ENABLED_PATH + "?organization=org").willReturn(aResponse().withResponseBody(
        Body.fromJsonBytes("""
          {
            "enabled": false
          }
          """.getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_CLOUD_URL", harness.getMockSonarQubeServer().baseUrl(),
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(SearchDependencyRisksTool.TOOL_NAME, Map.of(SearchDependencyRisksTool.PROJECT_KEY_PROPERTY, "my-project"));

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("Search Dependency Risks tool is not available in your SonarQube Cloud organization because Advanced Security is not enabled.", true));
    }
  }

  @Nested
  class WithSonarQubeServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get("/api/v2" + ScaApi.DEPENDENCY_RISKS_PATH + "?projectKey=my-project").willReturn(aResponse().withStatus(403)));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(SearchDependencyRisksTool.TOOL_NAME, Map.of(SearchDependencyRisksTool.PROJECT_KEY_PROPERTY, "my-project"));

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Forbidden. Please verify your token has the required permissions for this operation.", true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_sca_feature_is_disabled(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(FeaturesApi.FEATURES_LIST_PATH).willReturn(okJson("""
          [""]
          """)));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(SearchDependencyRisksTool.TOOL_NAME, Map.of(SearchDependencyRisksTool.PROJECT_KEY_PROPERTY, "my-project"));

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("Search Dependency Risks tool is not available for SonarQube Server because Advanced Security is not enabled.", true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_project_key_is_missing(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(SearchDependencyRisksTool.TOOL_NAME);

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: Missing required argument: projectKey", true));
    }

    @SonarQubeMcpServerTest
    void it_should_not_find_tool_if_version_not_sufficient(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of("SONARQUBE_VERSION", "2025.1"));

      var result = mcpClient.callTool(SearchDependencyRisksTool.TOOL_NAME, Map.of(SearchDependencyRisksTool.PROJECT_KEY_PROPERTY, "my-project"));

      assertThat(result).isEqualTo(new McpSchema.CallToolResult("Search Dependency Risks tool is not available because it requires SonarQube Server 2025.4 Enterprise or higher.", true));
    }

    @SonarQubeMcpServerTest
    void it_should_find_tool_even_if_sca_is_disabled(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(FeaturesApi.FEATURES_LIST_PATH).willReturn(aResponse().withResponseBody(
        Body.fromJsonBytes("""
          ["prioritized-rules","from-sonarqube-update","multiple-alm"]
          """.getBytes(StandardCharsets.UTF_8)))));

      var mcpClient = harness.newClient();

      assertThat(mcpClient.listTools())
        .extracting(McpSchema.Tool::name)
        .contains(SearchDependencyRisksTool.TOOL_NAME);
    }

    @SonarQubeMcpServerTest
    void it_should_fetch_dependency_risks_for_project_key_only(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get("/api/v2" + ScaApi.DEPENDENCY_RISKS_PATH + "?projectKey=my-project")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "issuesReleases": [%s],
              "branches": [],
              "countWithoutFilters": 1,
              "page": {
                "pageIndex": 1,
                "pageSize": 100,
                "total": 1
              }
            }
            """.formatted(generateIssueRelease()).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(SearchDependencyRisksTool.TOOL_NAME, Map.of(SearchDependencyRisksTool.PROJECT_KEY_PROPERTY, "my-project"));

      assertResultEquals(result, """
        {
          "issuesReleases" : [ {
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
              "directSummary" : true,
              "productionScopeSummary" : true
            },
            "assignee" : {
              "name" : "John Doe"
            }
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_fetch_dependency_risks_with_branch_key(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get("/api/v2" + ScaApi.DEPENDENCY_RISKS_PATH + "?projectKey=my-project&branchKey=feature%2Fnew-feature")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "issuesReleases": [%s],
              "branches": [],
              "countWithoutFilters": 1,
              "page": {
                "pageIndex": 1,
                "pageSize": 100,
                "total": 1
              }
            }
            """.formatted(generateIssueRelease()).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(SearchDependencyRisksTool.TOOL_NAME, Map.of(
        SearchDependencyRisksTool.PROJECT_KEY_PROPERTY, "my-project",
        SearchDependencyRisksTool.BRANCH_KEY_PROPERTY, "feature/new-feature"));

      assertResultEquals(result, """
        {
          "issuesReleases" : [ {
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
              "directSummary" : true,
              "productionScopeSummary" : true
            },
            "assignee" : {
              "name" : "John Doe"
            }
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_fetch_dependency_risks_with_pull_request_key(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get("/api/v2" + ScaApi.DEPENDENCY_RISKS_PATH + "?projectKey=my-project&pullRequestKey=123")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "issuesReleases": [%s],
              "branches": [],
              "countWithoutFilters": 1,
              "page": {
                "pageIndex": 1,
                "pageSize": 100,
                "total": 1
              }
            }
            """.formatted(generateIssueRelease()).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(SearchDependencyRisksTool.TOOL_NAME, Map.of(
        SearchDependencyRisksTool.PROJECT_KEY_PROPERTY, "my-project",
        SearchDependencyRisksTool.PULL_REQUEST_KEY_PROPERTY, "123"));

      assertResultEquals(result, """
        {
          "issuesReleases" : [ {
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
              "directSummary" : true,
              "productionScopeSummary" : true
            },
            "assignee" : {
              "name" : "John Doe"
            }
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_handle_empty_dependency_risks_response(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get("/api/v2" + ScaApi.DEPENDENCY_RISKS_PATH + "?projectKey=my-project")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "issuesReleases": [],
              "branches": [],
              "countWithoutFilters": 0,
              "page": {
                "pageIndex": 1,
                "pageSize": 100,
                "total": 0
              }
            }
            """.getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(SearchDependencyRisksTool.TOOL_NAME, Map.of(SearchDependencyRisksTool.PROJECT_KEY_PROPERTY, "my-project"));

      assertResultEquals(result, """
        {
          "issuesReleases" : [ ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_handle_dependency_risks_with_minimal_data(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get("/api/v2" + ScaApi.DEPENDENCY_RISKS_PATH + "?projectKey=my-project")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "issuesReleases": [%s],
              "branches": [],
              "countWithoutFilters": 1,
              "page": {
                "pageIndex": 1,
                "pageSize": 100,
                "total": 1
              }
            }
            """.formatted(generateMinimalIssueRelease()).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(SearchDependencyRisksTool.TOOL_NAME, Map.of(SearchDependencyRisksTool.PROJECT_KEY_PROPERTY, "my-project"));

      assertResultEquals(result, """
        {
          "issuesReleases" : [ {
            "key" : "issue-456",
            "severity" : "MEDIUM",
            "type" : "PROHIBITED_LICENSE",
            "quality" : "MAINTAINABILITY",
            "status" : "OPEN",
            "createdAt" : "2024-01-20T14:45:00Z",
            "release" : {
              "packageName" : "package-name",
              "version" : "2.0.0",
              "packageManager" : "maven",
              "newlyIntroduced" : false,
              "directSummary" : false,
              "productionScopeSummary" : false
            }
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_handle_multiple_dependency_risks(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get("/api/v2" + ScaApi.DEPENDENCY_RISKS_PATH + "?projectKey=my-project")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "issuesReleases": [%s, %s],
              "branches": [],
              "countWithoutFilters": 2,
              "page": {
                "pageIndex": 1,
                "pageSize": 100,
                "total": 2
              }
            }
            """.formatted(generateIssueRelease(), generateMinimalIssueRelease()).getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(SearchDependencyRisksTool.TOOL_NAME, Map.of(SearchDependencyRisksTool.PROJECT_KEY_PROPERTY, "my-project"));

      assertResultEquals(result, """
        {
          "issuesReleases" : [ {
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
              "directSummary" : true,
              "productionScopeSummary" : true
            },
            "assignee" : {
              "name" : "John Doe"
            }
          }, {
            "key" : "issue-456",
            "severity" : "MEDIUM",
            "type" : "PROHIBITED_LICENSE",
            "quality" : "MAINTAINABILITY",
            "status" : "OPEN",
            "createdAt" : "2024-01-20T14:45:00Z",
            "release" : {
              "packageName" : "package-name",
              "version" : "2.0.0",
              "packageManager" : "maven",
              "newlyIntroduced" : false,
              "directSummary" : false,
              "productionScopeSummary" : false
            }
          } ]
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }
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

  private static String generateMinimalIssueRelease() {
    return """
      {
        "key": "issue-456",
        "severity": "MEDIUM",
        "originalSeverity": "MEDIUM",
        "manualSeverity": null,
        "showIncreasedSeverityWarning": false,
        "release": {
          "key": "release-456",
          "branchUuid": "branch-uuid-2",
          "packageUrl": "pkg:maven/com.example/package-name@2.0.0",
          "packageManager": "maven",
          "packageName": "package-name",
          "version": "2.0.0",
          "licenseExpression": "Apache-2.0",
          "known": true,
          "knownPackage": true,
          "newlyIntroduced": false,
          "directSummary": false,
          "scopeSummary": "test",
          "productionScopeSummary": false,
          "dependencyFilePaths": ["pom.xml"]
        },
        "type": "PROHIBITED_LICENSE",
        "quality": "MAINTAINABILITY",
        "status": "OPEN",
        "createdAt": "2024-01-20T14:45:00Z",
        "assignee": null,
        "commentCount": 0,
        "vulnerabilityId": null,
        "cweIds": [],
        "cvssScore": null,
        "withdrawn": false,
        "spdxLicenseId": "Apache-2.0",
        "transitions": ["CONFIRM"],
        "actions": ["COMMENT"]
      }
      """;
  }

}
