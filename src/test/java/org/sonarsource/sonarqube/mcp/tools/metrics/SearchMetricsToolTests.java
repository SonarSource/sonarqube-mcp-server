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
package org.sonarsource.sonarqube.mcp.tools.metrics;

import com.github.tomakehurst.wiremock.http.Body;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.sonarsource.sonarqube.mcp.harness.ReceivedRequest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient;
import org.sonarsource.sonarqube.mcp.serverapi.metrics.MetricsApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;

class SearchMetricsToolTests {

  @Nested
  class WithSonarCloudServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(SearchMetricsTool.TOOL_NAME);

      assertThat(result.isError()).isTrue();
      var content = result.content().getFirst().toString();
      assertThat(content).contains("An error occurred during the tool execution:");
      assertThat(content).contains("Please verify your token is valid and the requested resource exists.");
    }

    @SonarQubeMcpServerTest
    void it_should_succeed_when_no_metrics_found(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(MetricsApi.SEARCH_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "metrics": [],
              "total": 0,
              "p": 1,
              "ps": 100
            }
            """.getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(SearchMetricsTool.TOOL_NAME);

      SonarQubeMcpTestClient.assertResultEquals(result, """
        Search Results: 0 total metrics
        Page: 1 | Page Size: 100
        
        No metrics found.""", false);
    }

    @SonarQubeMcpServerTest
    void it_should_search_metrics_with_default_parameters(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(MetricsApi.SEARCH_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateSearchMetricsResponse().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(SearchMetricsTool.TOOL_NAME);

      SonarQubeMcpTestClient.assertResultEquals(result, """
          Search Results: 2 total metrics
          Page: 1 | Page Size: 100
          
          Metrics:
            - Team size (team_size)
              ID: 23
              Description: Number of people in the team
              Domain: Management
              Type: INT
              Direction: 0 (no direction)
              Qualitative: false
              Hidden: false
              Custom: true
          
            - Uncovered lines (uncovered_lines)
              ID: 2
              Description: Uncovered lines
              Domain: Tests
              Type: INT
              Direction: 1 (higher values are better)
              Qualitative: true
              Hidden: false
              Custom: false""", false);
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_search_metrics_with_page_parameters(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(MetricsApi.SEARCH_PATH + "?p=2&ps=20")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateSearchMetricsResponse().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient(Map.of(
        "SONARQUBE_ORG", "org"
      ));

      var result = mcpClient.callTool(
        SearchMetricsTool.TOOL_NAME,
        Map.of(
          SearchMetricsTool.PAGE_PROPERTY, 2,
          SearchMetricsTool.PAGE_SIZE_PROPERTY, 20
        ));

      SonarQubeMcpTestClient.assertResultEquals(result, """
          Search Results: 2 total metrics
          Page: 1 | Page Size: 100
          
          Metrics:
            - Team size (team_size)
              ID: 23
              Description: Number of people in the team
              Domain: Management
              Type: INT
              Direction: 0 (no direction)
              Qualitative: false
              Hidden: false
              Custom: true
          
            - Uncovered lines (uncovered_lines)
              ID: 2
              Description: Uncovered lines
              Domain: Tests
              Type: INT
              Direction: 1 (higher values are better)
              Qualitative: true
              Hidden: false
              Custom: false""", false);
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

  }

  @Nested
  class WithSonarQubeServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_token_permission(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(SearchMetricsTool.TOOL_NAME);

      assertThat(result.isError()).isTrue();
      var content = result.content().getFirst().toString();
      assertThat(content).contains("An error occurred during the tool execution:");
      assertThat(content).contains("Please verify your token is valid and the requested resource exists.");
    }

    @SonarQubeMcpServerTest
    void it_should_succeed_when_no_metrics_found(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(MetricsApi.SEARCH_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes("""
            {
              "metrics": [],
              "total": 0,
              "p": 1,
              "ps": 100
            }
            """.getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(SearchMetricsTool.TOOL_NAME);

      SonarQubeMcpTestClient.assertResultEquals(result, """
        Search Results: 0 total metrics
        Page: 1 | Page Size: 100
        
        No metrics found.""", false);
    }

    @SonarQubeMcpServerTest
    void it_should_search_metrics_with_default_parameters(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(MetricsApi.SEARCH_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateSearchMetricsResponse().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(SearchMetricsTool.TOOL_NAME);

      SonarQubeMcpTestClient.assertResultEquals(result, """
          Search Results: 2 total metrics
          Page: 1 | Page Size: 100
          
          Metrics:
            - Team size (team_size)
              ID: 23
              Description: Number of people in the team
              Domain: Management
              Type: INT
              Direction: 0 (no direction)
              Qualitative: false
              Hidden: false
              Custom: true
          
            - Uncovered lines (uncovered_lines)
              ID: 2
              Description: Uncovered lines
              Domain: Tests
              Type: INT
              Direction: 1 (higher values are better)
              Qualitative: true
              Hidden: false
              Custom: false""", false);
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_search_metrics_with_page_parameters(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(MetricsApi.SEARCH_PATH + "?p=2&ps=20")
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateSearchMetricsResponse().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        SearchMetricsTool.TOOL_NAME,
        Map.of(
          SearchMetricsTool.PAGE_PROPERTY, 2,
          SearchMetricsTool.PAGE_SIZE_PROPERTY, 20
        ));

      SonarQubeMcpTestClient.assertResultEquals(result, """
          Search Results: 2 total metrics
          Page: 1 | Page Size: 100
          
          Metrics:
            - Team size (team_size)
              ID: 23
              Description: Number of people in the team
              Domain: Management
              Type: INT
              Direction: 0 (no direction)
              Qualitative: false
              Hidden: false
              Custom: true
          
            - Uncovered lines (uncovered_lines)
              ID: 2
              Description: Uncovered lines
              Domain: Tests
              Type: INT
              Direction: 1 (higher values are better)
              Qualitative: true
              Hidden: false
              Custom: false""", false);
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }
  }

  private static String generateSearchMetricsResponse() {
    return """
        {
          "metrics": [
            {
              "id": "23",
              "key": "team_size",
              "name": "Team size",
              "description": "Number of people in the team",
              "domain": "Management",
              "type": "INT",
              "direction": 0,
              "qualitative": false,
              "hidden": false,
              "custom": true
            },
            {
              "id": "2",
              "key": "uncovered_lines",
              "name": "Uncovered lines",
              "description": "Uncovered lines",
              "domain": "Tests",
              "type": "INT",
              "direction": 1,
              "qualitative": true,
              "hidden": false,
              "custom": false
            }
          ],
          "total": 2,
          "p": 1,
          "ps": 100
        }
        """;
  }

} 
