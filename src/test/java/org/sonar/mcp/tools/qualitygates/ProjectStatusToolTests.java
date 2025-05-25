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
package org.sonar.mcp.tools.qualitygates;

import com.github.tomakehurst.wiremock.http.Body;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.sonar.mcp.harness.MockWebServer;
import org.sonar.mcp.harness.ReceivedRequest;
import org.sonar.mcp.harness.SonarMcpServerTest;
import org.sonar.mcp.harness.SonarMcpServerTestHarness;
import org.sonar.mcp.serverapi.qualitygates.QualityGatesApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;

class ProjectStatusToolTests {

  @Nested
  class MissingPrerequisite {

    @SonarMcpServerTest
    void it_should_return_an_error_if_sonarqube_cloud_token_is_missing(SonarMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of("SONARQUBE_CLOUD_ORG", "org"));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        ProjectStatusTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Not connected to SonarQube Cloud, please provide 'SONARQUBE_CLOUD_TOKEN' and " +
          "'SONARQUBE_CLOUD_ORG'", true));
    }

    @SonarMcpServerTest
    void it_should_return_an_error_if_sonarqube_cloud_org_is_missing(SonarMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of("SONARQUBE_CLOUD_TOKEN", "token"));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        ProjectStatusTool.TOOL_NAME,
        Map.of("prefix", "proj")));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Not connected to SonarQube Cloud, please provide 'SONARQUBE_CLOUD_TOKEN' and " +
          "'SONARQUBE_CLOUD_ORG'", true));
    }
  }

  @Nested
  class WithServer {

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
        ProjectStatusTool.TOOL_NAME,
        Map.of(ProjectStatusTool.ANALYSIS_ID_PROPERTY, "12345")));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: Make sure your token is valid.", true));
    }

    @SonarMcpServerTest
    void it_should_show_error_when_no_parameter_is_provided(SonarMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_CLOUD_URL", mockServer.baseUrl(),
        "SONARQUBE_CLOUD_TOKEN", "token",
        "SONARQUBE_CLOUD_ORG", "org"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        ProjectStatusTool.TOOL_NAME,
        Map.of()));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Either 'analysisId', 'projectId' or 'projectKey' must be provided", true));
    }

    @SonarMcpServerTest
    void it_should_show_error_when_no_project_id_and_branch_are_provided(SonarMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_CLOUD_URL", mockServer.baseUrl(),
        "SONARQUBE_CLOUD_TOKEN", "token",
        "SONARQUBE_CLOUD_ORG", "org"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        ProjectStatusTool.TOOL_NAME,
        Map.of(ProjectStatusTool.PROJECT_ID_PROPERTY, "123", ProjectStatusTool.BRANCH_PROPERTY, "branch")));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Project ID doesn't work with branches or pull requests", true));
    }

    @SonarMcpServerTest
    void it_should_show_error_when_request_fails(SonarMcpServerTestHarness harness) {
      mockServer.stubFor(get(QualityGatesApi.PROJECT_STATUS_PATH + "?analysisId=12345").willReturn(aResponse().withStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR)));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_CLOUD_URL", mockServer.baseUrl(),
        "SONARQUBE_CLOUD_TOKEN", "token",
        "SONARQUBE_CLOUD_ORG", "org"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        ProjectStatusTool.TOOL_NAME,
        Map.of(ProjectStatusTool.ANALYSIS_ID_PROPERTY, "12345")));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: SonarQube answered with Error 500 on " + mockServer.baseUrl() + "/api" +
          "/qualitygates/project_status?analysisId=12345", true));
    }

    @SonarMcpServerTest
    void it_should_return_the_project_status_with_project_key(SonarMcpServerTestHarness harness) {
      mockServer.stubFor(get(QualityGatesApi.PROJECT_STATUS_PATH + "?projectKey=pkey")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generatePayload().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_CLOUD_URL", mockServer.baseUrl(),
        "SONARQUBE_CLOUD_TOKEN", "token",
        "SONARQUBE_CLOUD_ORG", "org"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        ProjectStatusTool.TOOL_NAME,
        Map.of(ProjectStatusTool.PROJECT_KEY_PROPERTY, "pkey")));

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
      assertThat(mockServer.getReceivedRequests())
        .containsExactly(new ReceivedRequest("Bearer token", ""));
    }

    @SonarMcpServerTest
    void it_should_return_the_project_status_with_analysis_id(SonarMcpServerTestHarness harness) {
      mockServer.stubFor(get(QualityGatesApi.PROJECT_STATUS_PATH + "?analysisId=id")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generatePayload().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_CLOUD_URL", mockServer.baseUrl(),
        "SONARQUBE_CLOUD_TOKEN", "token",
        "SONARQUBE_CLOUD_ORG", "org"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        ProjectStatusTool.TOOL_NAME,
        Map.of(ProjectStatusTool.ANALYSIS_ID_PROPERTY, "id")));

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
      assertThat(mockServer.getReceivedRequests())
        .containsExactly(new ReceivedRequest("Bearer token", ""));
    }

    @SonarMcpServerTest
    void it_should_return_the_project_status_with_project_id(SonarMcpServerTestHarness harness) {
      mockServer.stubFor(get(QualityGatesApi.PROJECT_STATUS_PATH + "?projectId=" + URLEncoder.encode("AU-Tpxb--iU5OvuD2FLy", StandardCharsets.UTF_8))
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generatePayload().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_CLOUD_URL", mockServer.baseUrl(),
        "SONARQUBE_CLOUD_TOKEN", "token",
        "SONARQUBE_CLOUD_ORG", "org"
      ));

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        ProjectStatusTool.TOOL_NAME,
        Map.of(ProjectStatusTool.PROJECT_ID_PROPERTY, "AU-Tpxb--iU5OvuD2FLy")));

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
      assertThat(mockServer.getReceivedRequests())
        .containsExactly(new ReceivedRequest("Bearer token", ""));
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
