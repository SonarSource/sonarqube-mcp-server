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
package org.sonarsource.sonarqube.mcp.tools.qualitygates;

import com.github.tomakehurst.wiremock.http.Body;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Nested;
import org.sonarsource.sonarqube.mcp.harness.ReceivedRequest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.qualitygates.QualityGatesApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.serverapi.UrlUtils.urlEncode;

class ProjectStatusToolTests {

  @Nested
  class WithSonarCloudServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        ProjectStatusTool.TOOL_NAME,
        Map.of(ProjectStatusTool.ANALYSIS_ID_PROPERTY, "12345"));

      assertThat(result.isError()).isTrue();
      var content = result.content().getFirst().toString();
      assertThat(content).contains("An error occurred during the tool execution:");
      assertThat(content).contains("Please verify your token is valid.");
    }

    @SonarQubeMcpServerTest
    void it_should_show_error_when_no_parameter_is_provided(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(ProjectStatusTool.TOOL_NAME);

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Either 'analysisId', 'projectId' or 'projectKey' must be provided", true));
    }

    @SonarQubeMcpServerTest
    void it_should_show_error_when_no_project_id_and_branch_are_provided(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        ProjectStatusTool.TOOL_NAME,
        Map.of(ProjectStatusTool.PROJECT_ID_PROPERTY, "123", ProjectStatusTool.BRANCH_PROPERTY, "branch"));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Project ID doesn't work with branches or pull requests", true));
    }

    @SonarQubeMcpServerTest
    void it_should_show_error_when_request_fails(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer()
        .stubFor(get(QualityGatesApi.PROJECT_STATUS_PATH + "?analysisId=12345").willReturn(aResponse().withStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        ProjectStatusTool.TOOL_NAME,
        Map.of(ProjectStatusTool.ANALYSIS_ID_PROPERTY, "12345"));

      assertThat(result)
        .isEqualTo(
          new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Error 500 on " + harness.getMockSonarQubeServer().baseUrl() + "/api" +
            "/qualitygates/project_status?analysisId=12345", true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_project_status_with_project_key(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(QualityGatesApi.PROJECT_STATUS_PATH + "?projectKey=pkey")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generatePayload().getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        ProjectStatusTool.TOOL_NAME,
        Map.of(ProjectStatusTool.PROJECT_KEY_PROPERTY, "pkey"));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          The Quality Gate status is ERROR. Here are the following conditions:
          new_coverage is ERROR, the threshold is 85 and the actual value is 82.50562381034781
          new_blocker_violations is ERROR, the threshold is 0 and the actual value is 14
          new_critical_violations is ERROR, the threshold is 0 and the actual value is 1
          new_sqale_debt_ratio is OK, the threshold is 5 and the actual value is 0.6562109862671661
          reopened_issues is OK, the threshold is null and the actual value is 0
          open_issues is ERROR, the threshold is null and the actual value is 17
          skipped_tests is OK, the threshold is null and the actual value is 0""", false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_project_status_with_analysis_id(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(QualityGatesApi.PROJECT_STATUS_PATH + "?analysisId=id")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generatePayload().getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        ProjectStatusTool.TOOL_NAME,
        Map.of(ProjectStatusTool.ANALYSIS_ID_PROPERTY, "id"));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          The Quality Gate status is ERROR. Here are the following conditions:
          new_coverage is ERROR, the threshold is 85 and the actual value is 82.50562381034781
          new_blocker_violations is ERROR, the threshold is 0 and the actual value is 14
          new_critical_violations is ERROR, the threshold is 0 and the actual value is 1
          new_sqale_debt_ratio is OK, the threshold is 5 and the actual value is 0.6562109862671661
          reopened_issues is OK, the threshold is null and the actual value is 0
          open_issues is ERROR, the threshold is null and the actual value is 17
          skipped_tests is OK, the threshold is null and the actual value is 0""", false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_project_status_with_project_id(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(QualityGatesApi.PROJECT_STATUS_PATH + "?projectId=" + urlEncode("AU-Tpxb--iU5OvuD2FLy"))
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generatePayload().getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        ProjectStatusTool.TOOL_NAME,
        Map.of(ProjectStatusTool.PROJECT_ID_PROPERTY, "AU-Tpxb--iU5OvuD2FLy"));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          The Quality Gate status is ERROR. Here are the following conditions:
          new_coverage is ERROR, the threshold is 85 and the actual value is 82.50562381034781
          new_blocker_violations is ERROR, the threshold is 0 and the actual value is 14
          new_critical_violations is ERROR, the threshold is 0 and the actual value is 1
          new_sqale_debt_ratio is OK, the threshold is 5 and the actual value is 0.6562109862671661
          reopened_issues is OK, the threshold is null and the actual value is 0
          open_issues is ERROR, the threshold is null and the actual value is 17
          skipped_tests is OK, the threshold is null and the actual value is 0""", false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_project_status_with_project_key_and_branch(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(QualityGatesApi.PROJECT_STATUS_PATH + "?branch=" + urlEncode("feature/my-branch") + "&projectKey=pkey")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generatePayload().getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        ProjectStatusTool.TOOL_NAME,
        Map.of(ProjectStatusTool.PROJECT_KEY_PROPERTY, "pkey", ProjectStatusTool.BRANCH_PROPERTY, "feature/my-branch"));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          The Quality Gate status is ERROR. Here are the following conditions:
          new_coverage is ERROR, the threshold is 85 and the actual value is 82.50562381034781
          new_blocker_violations is ERROR, the threshold is 0 and the actual value is 14
          new_critical_violations is ERROR, the threshold is 0 and the actual value is 1
          new_sqale_debt_ratio is OK, the threshold is 5 and the actual value is 0.6562109862671661
          reopened_issues is OK, the threshold is null and the actual value is 0
          open_issues is ERROR, the threshold is null and the actual value is 17
          skipped_tests is OK, the threshold is null and the actual value is 0""", false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }
  }

  @Nested
  class WithSonarQubeServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(QualityGatesApi.PROJECT_STATUS_PATH + "?projectKey=pkey").willReturn(aResponse().withStatus(HttpStatus.SC_FORBIDDEN)));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        ProjectStatusTool.TOOL_NAME,
        Map.of(ProjectStatusTool.PROJECT_KEY_PROPERTY, "pkey"));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Forbidden. Please verify your token has the required permissions for this operation.", true));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_project_status_with_project_key(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(QualityGatesApi.PROJECT_STATUS_PATH + "?projectKey=pkey")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generatePayload().getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        ProjectStatusTool.TOOL_NAME,
        Map.of(ProjectStatusTool.PROJECT_KEY_PROPERTY, "pkey"));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          The Quality Gate status is ERROR. Here are the following conditions:
          new_coverage is ERROR, the threshold is 85 and the actual value is 82.50562381034781
          new_blocker_violations is ERROR, the threshold is 0 and the actual value is 14
          new_critical_violations is ERROR, the threshold is 0 and the actual value is 1
          new_sqale_debt_ratio is OK, the threshold is 5 and the actual value is 0.6562109862671661
          reopened_issues is OK, the threshold is null and the actual value is 0
          open_issues is ERROR, the threshold is null and the actual value is 17
          skipped_tests is OK, the threshold is null and the actual value is 0""", false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_project_status_with_analysis_id(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(QualityGatesApi.PROJECT_STATUS_PATH + "?analysisId=id")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generatePayload().getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        ProjectStatusTool.TOOL_NAME,
        Map.of(ProjectStatusTool.ANALYSIS_ID_PROPERTY, "id"));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          The Quality Gate status is ERROR. Here are the following conditions:
          new_coverage is ERROR, the threshold is 85 and the actual value is 82.50562381034781
          new_blocker_violations is ERROR, the threshold is 0 and the actual value is 14
          new_critical_violations is ERROR, the threshold is 0 and the actual value is 1
          new_sqale_debt_ratio is OK, the threshold is 5 and the actual value is 0.6562109862671661
          reopened_issues is OK, the threshold is null and the actual value is 0
          open_issues is ERROR, the threshold is null and the actual value is 17
          skipped_tests is OK, the threshold is null and the actual value is 0""", false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_the_project_status_with_project_key_and_branch(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(QualityGatesApi.PROJECT_STATUS_PATH + "?branch=" + urlEncode("feature/my-branch") + "&projectKey=pkey")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generatePayload().getBytes(StandardCharsets.UTF_8)))));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        ProjectStatusTool.TOOL_NAME,
        Map.of(ProjectStatusTool.PROJECT_KEY_PROPERTY, "pkey", ProjectStatusTool.BRANCH_PROPERTY, "feature/my-branch"));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          The Quality Gate status is ERROR. Here are the following conditions:
          new_coverage is ERROR, the threshold is 85 and the actual value is 82.50562381034781
          new_blocker_violations is ERROR, the threshold is 0 and the actual value is 14
          new_critical_violations is ERROR, the threshold is 0 and the actual value is 1
          new_sqale_debt_ratio is OK, the threshold is 5 and the actual value is 0.6562109862671661
          reopened_issues is OK, the threshold is null and the actual value is 0
          open_issues is ERROR, the threshold is null and the actual value is 17
          skipped_tests is OK, the threshold is null and the actual value is 0""", false));
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }
  }

  private static String generatePayload() {
    return """
        {
        "projectStatus": {
          "status": "ERROR",
          "ignoredConditions": false,
          "conditions": [
            {
              "status": "ERROR",
              "metricKey": "new_coverage",
              "comparator": "LT",
              "periodIndex": 1,
              "errorThreshold": "85",
              "actualValue": "82.50562381034781"
            },
            {
              "status": "ERROR",
              "metricKey": "new_blocker_violations",
              "comparator": "GT",
              "periodIndex": 1,
              "errorThreshold": "0",
              "actualValue": "14"
            },
            {
              "status": "ERROR",
              "metricKey": "new_critical_violations",
              "comparator": "GT",
              "periodIndex": 1,
              "errorThreshold": "0",
              "actualValue": "1"
            },
            {
              "status": "OK",
              "metricKey": "new_sqale_debt_ratio",
              "comparator": "GT",
              "periodIndex": 1,
              "errorThreshold": "5",
              "actualValue": "0.6562109862671661"
            },
            {
              "status": "OK",
              "metricKey": "reopened_issues",
              "comparator": "GT",
              "periodIndex": 1,
              "actualValue": "0"
            },
            {
              "status": "ERROR",
              "metricKey": "open_issues",
              "comparator": "GT",
              "periodIndex": 1,
              "actualValue": "17"
            },
            {
              "status": "OK",
              "metricKey": "skipped_tests",
              "comparator": "GT",
              "periodIndex": 1,
              "actualValue": "0"
            }
          ],
          "periods": [
            {
              "index": 1,
              "mode": "last_version",
              "date": "2000-04-27T00:45:23+0200",
              "parameter": "2015-12-07"
            }
          ]
        }
      }""";
  }

}
