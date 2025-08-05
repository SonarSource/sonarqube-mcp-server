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
package org.sonarsource.sonarqube.mcp.tools.sca;

import com.github.tomakehurst.wiremock.http.Body;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.sonarsource.sonarqube.mcp.harness.ReceivedRequest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.sca.ScaApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;

class DependencyRisksToolTests {

  @Nested
  class WithSonarCloudServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(ScaApi.ISSUES_RELEASES_PATH).willReturn(aResponse().withStatus(403)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(DependencyRisksTool.TOOL_NAME);

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Forbidden", true));
    }

    @SonarQubeMcpServerTest
    void it_should_fetch_dependency_risks_for_project(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(ScaApi.ISSUES_RELEASES_PATH + "?projectKey=my-project")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "dependencyRisks": [
                {
                  "key": "risk-key-1",
                  "component": "my-project:src/main/java/App.java",
                  "severity": "HIGH",
                  "status": "OPEN",
                  "message": "Vulnerable dependency detected",
                  "rule": "java:S6018",
                  "packageName": "log4j-core",
                  "packageVersion": "2.14.1",
                  "riskType": "VULNERABILITY",
                  "cwe": ["CWE-502"],
                  "cve": ["CVE-2021-44228"],
                  "creationDate": "2024-01-15T10:30:00+0000",
                  "updateDate": "2024-01-15T10:30:00+0000"
                }
              ]
            }
            """.getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(
        DependencyRisksTool.TOOL_NAME,
        Map.of(DependencyRisksTool.PROJECT_KEY_PROPERTY, "my-project"));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          Found 1 dependency risks.

          Risk Key: risk-key-1 | Package: log4j-core | Version: 2.14.1 | Severity: HIGH | Status: OPEN | Type: VULNERABILITY | Component: my-project:src/main/java/App.java | Rule: java:S6018 | Message: Vulnerable dependency detected | CVE: CVE-2021-44228 | CWE: CWE-502 | Created: 2024-01-15T10:30:00+0000 | Updated: 2024-01-15T10:30:00+0000
          """.trim(), false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_fetch_dependency_risks_for_branch(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(ScaApi.ISSUES_RELEASES_PATH + "?projectKey=my-project&branchKey=feature-branch")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "dependencyRisks": [
                {
                  "key": "risk-key-2",
                  "component": "my-project:pom.xml",
                  "severity": "MEDIUM",
                  "status": "OPEN",
                  "message": "License incompatibility detected",
                  "rule": "java:S6019",
                  "packageName": "spring-boot-starter",
                  "packageVersion": "2.5.0",
                  "riskType": "LICENSE",
                  "cwe": [],
                  "cve": [],
                  "creationDate": "2024-01-20T14:15:00+0000",
                  "updateDate": "2024-01-20T14:15:00+0000"
                }
              ]
            }
            """.getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(
        DependencyRisksTool.TOOL_NAME,
        Map.of(
          DependencyRisksTool.PROJECT_KEY_PROPERTY, "my-project",
          DependencyRisksTool.BRANCH_KEY_PROPERTY, "feature-branch"));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          Found 1 dependency risks.

          Risk Key: risk-key-2 | Package: spring-boot-starter | Version: 2.5.0 | Severity: MEDIUM | Status: OPEN | Type: LICENSE | Component: my-project:pom.xml | Rule: java:S6019 | Message: License incompatibility detected | Created: 2024-01-20T14:15:00+0000 | Updated: 2024-01-20T14:15:00+0000
          """.trim(), false));
    }

    @SonarQubeMcpServerTest
    void it_should_fetch_dependency_risks_for_pull_request(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(ScaApi.ISSUES_RELEASES_PATH + "?projectKey=my-project&pullRequestKey=123")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "dependencyRisks": [
                {
                  "key": "risk-key-3",
                  "component": "my-project:package.json",
                  "severity": "CRITICAL",
                  "status": "OPEN",
                  "message": "Critical vulnerability in dependency",
                  "rule": "javascript:S6020",
                  "packageName": "lodash",
                  "packageVersion": "4.17.10",
                  "riskType": "VULNERABILITY",
                  "cwe": ["CWE-400"],
                  "cve": ["CVE-2019-10744"],
                  "creationDate": "2024-02-01T09:00:00+0000",
                  "updateDate": "2024-02-01T09:00:00+0000"
                }
              ]
            }
            """.getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(
        DependencyRisksTool.TOOL_NAME,
        Map.of(
          DependencyRisksTool.PROJECT_KEY_PROPERTY, "my-project",
          DependencyRisksTool.PULL_REQUEST_KEY_PROPERTY, "123"));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          Found 1 dependency risks.

          Risk Key: risk-key-3 | Package: lodash | Version: 4.17.10 | Severity: CRITICAL | Status: OPEN | Type: VULNERABILITY | Component: my-project:package.json | Rule: javascript:S6020 | Message: Critical vulnerability in dependency | CVE: CVE-2019-10744 | CWE: CWE-400 | Created: 2024-02-01T09:00:00+0000 | Updated: 2024-02-01T09:00:00+0000
          """.trim(), false));
    }

    @SonarQubeMcpServerTest
    void it_should_return_no_risks_found_message_when_empty_response(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(ScaApi.ISSUES_RELEASES_PATH + "?projectKey=clean-project")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "dependencyRisks": []
            }
            """.getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(
        DependencyRisksTool.TOOL_NAME,
        Map.of(DependencyRisksTool.PROJECT_KEY_PROPERTY, "clean-project"));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("No dependency risks were found.", false));
    }

    @SonarQubeMcpServerTest
    void it_should_handle_multiple_dependency_risks(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(ScaApi.ISSUES_RELEASES_PATH + "?projectKey=vulnerable-project")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "dependencyRisks": [
                {
                  "key": "risk-1",
                  "component": "vulnerable-project:pom.xml",
                  "severity": "HIGH",
                  "status": "OPEN",
                  "message": "High severity vulnerability",
                  "rule": "java:S6018",
                  "packageName": "jackson-core",
                  "packageVersion": "2.12.0",
                  "riskType": "VULNERABILITY",
                  "cwe": ["CWE-502"],
                  "cve": ["CVE-2021-29425"],
                  "creationDate": "2024-01-10T08:00:00+0000",
                  "updateDate": "2024-01-10T08:00:00+0000"
                },
                {
                  "key": "risk-2",
                  "component": "vulnerable-project:package.json",
                  "severity": "MEDIUM",
                  "status": "RESOLVED",
                  "message": "Outdated dependency",
                  "rule": "javascript:S6021",
                  "packageName": "express",
                  "packageVersion": "4.15.0",
                  "riskType": "OUTDATED",
                  "cwe": [],
                  "cve": [],
                  "creationDate": "2024-01-05T12:30:00+0000",
                  "updateDate": "2024-01-12T16:45:00+0000"
                }
              ]
            }
            """.getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(
        DependencyRisksTool.TOOL_NAME,
        Map.of(DependencyRisksTool.PROJECT_KEY_PROPERTY, "vulnerable-project"));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          Found 2 dependency risks.

          Risk Key: risk-1 | Package: jackson-core | Version: 2.12.0 | Severity: HIGH | Status: OPEN | Type: VULNERABILITY | Component: vulnerable-project:pom.xml | Rule: java:S6018 | Message: High severity vulnerability | CVE: CVE-2021-29425 | CWE: CWE-502 | Created: 2024-01-10T08:00:00+0000 | Updated: 2024-01-10T08:00:00+0000
          Risk Key: risk-2 | Package: express | Version: 4.15.0 | Severity: MEDIUM | Status: RESOLVED | Type: OUTDATED | Component: vulnerable-project:package.json | Rule: javascript:S6021 | Message: Outdated dependency | Created: 2024-01-05T12:30:00+0000 | Updated: 2024-01-12T16:45:00+0000
          """.trim(), false));
    }
  }

  @Nested
  class WithSonarQubeServer {

    @SonarQubeMcpServerTest
    void it_should_fetch_dependency_risks_from_server_without_organization(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(ScaApi.ISSUES_RELEASES_PATH + "?projectKey=server-project")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "dependencyRisks": [
                {
                  "key": "server-risk-1",
                  "component": "server-project:src/main/resources/application.properties",
                  "severity": "LOW",
                  "status": "OPEN",
                  "message": "Minor security issue",
                  "rule": "java:S6022",
                  "packageName": "commons-lang3",
                  "packageVersion": "3.10",
                  "riskType": "VULNERABILITY",
                  "cwe": ["CWE-79"],
                  "cve": [],
                  "creationDate": "2024-02-15T11:00:00+0000",
                  "updateDate": "2024-02-15T11:00:00+0000"
                }
              ]
            }
            """.getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_URL", "http://localhost:9000"
      ));

      var result = mcpClient.callTool(
        DependencyRisksTool.TOOL_NAME,
        Map.of(DependencyRisksTool.PROJECT_KEY_PROPERTY, "server-project"));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          Found 1 dependency risks.

          Risk Key: server-risk-1 | Package: commons-lang3 | Version: 3.10 | Severity: LOW | Status: OPEN | Type: VULNERABILITY | Component: server-project:src/main/resources/application.properties | Rule: java:S6022 | Message: Minor security issue | CWE: CWE-79 | Created: 2024-02-15T11:00:00+0000 | Updated: 2024-02-15T11:00:00+0000
          """.trim(), false));
    }
  }
}