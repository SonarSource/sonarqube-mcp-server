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
package org.sonarsource.sonarqube.mcp.tools.metrics;

import com.github.tomakehurst.wiremock.http.Body;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.sonarsource.sonarqube.mcp.harness.ReceivedRequest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.metrics.MetricsApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertSchemaEquals;

class SearchMetricsToolTests {

  @SonarQubeMcpServerTest
  void it_should_validate_output_schema(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient();

    var tool = mcpClient.listTools().stream().filter(t -> t.name().equals(SearchMetricsTool.TOOL_NAME)).findFirst().orElseThrow();

    assertSchemaEquals(tool.outputSchema(), """
      {
         "type":"object",
         "properties":{
            "metrics":{
               "description":"List of metrics matching the search",
               "type":"array",
               "items":{
                  "type":"object",
                  "properties":{
                     "custom":{
                        "type":"boolean",
                        "description":"Whether this is a custom metric"
                     },
                     "description":{
                        "type":"string",
                        "description":"Metric description"
                     },
                     "direction":{
                        "type":"integer",
                        "description":"Direction for metric improvement"
                     },
                     "domain":{
                        "type":"string",
                        "description":"Metric domain/category"
                     },
                     "hidden":{
                        "type":"boolean",
                        "description":"Whether the metric is hidden"
                     },
                     "id":{
                        "type":"string",
                        "description":"Metric unique identifier"
                     },
                     "key":{
                        "type":"string",
                        "description":"Metric key"
                     },
                     "name":{
                        "type":"string",
                        "description":"Metric display name"
                     },
                     "qualitative":{
                        "type":"boolean",
                        "description":"Whether this is a qualitative metric"
                     },
                     "type":{
                        "type":"string",
                        "description":"Metric value type"
                     }
                  },
                  "required":[
                     "custom",
                     "description",
                     "direction",
                     "domain",
                     "hidden",
                     "id",
                     "key",
                     "name",
                     "qualitative",
                     "type"
                  ]
               }
            },
            "page":{
               "type":"integer",
               "description":"Current page number"
            },
            "pageSize":{
               "type":"integer",
               "description":"Number of items per page"
            },
            "total":{
               "type":"integer",
               "description":"Total number of metrics"
            }
         },
         "required":[
            "metrics",
            "page",
            "pageSize",
            "total"
         ]
      }
      """);
  }

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

      assertResultEquals(result, """
        {
            "metrics":[
        
            ],
            "total":0,
            "page":1,
            "pageSize":100
         }""");
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

      assertResultEquals(result, """
        {
            "metrics":[
               {
                  "id":"23",
                  "key":"team_size",
                  "name":"Team size",
                  "description":"Number of people in the team",
                  "domain":"Management",
                  "type":"INT",
                  "direction":0,
                  "qualitative":false,
                  "hidden":false,
                  "custom":true
               },
               {
                  "id":"2",
                  "key":"uncovered_lines",
                  "name":"Uncovered lines",
                  "description":"Uncovered lines",
                  "domain":"Tests",
                  "type":"INT",
                  "direction":1,
                  "qualitative":true,
                  "hidden":false,
                  "custom":false
               }
            ],
            "total":2,
            "page":1,
            "pageSize":100
         }""");
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

      assertResultEquals(result, """
        {
            "metrics":[
               {
                  "id":"23",
                  "key":"team_size",
                  "name":"Team size",
                  "description":"Number of people in the team",
                  "domain":"Management",
                  "type":"INT",
                  "direction":0,
                  "qualitative":false,
                  "hidden":false,
                  "custom":true
               },
               {
                  "id":"2",
                  "key":"uncovered_lines",
                  "name":"Uncovered lines",
                  "description":"Uncovered lines",
                  "domain":"Tests",
                  "type":"INT",
                  "direction":1,
                  "qualitative":true,
                  "hidden":false,
                  "custom":false
               }
            ],
            "total":2,
            "page":1,
            "pageSize":100
         }""");
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

      assertResultEquals(result, """
        {
            "metrics":[
        
            ],
            "total":0,
            "page":1,
            "pageSize":100
         }""");
    }

    @SonarQubeMcpServerTest
    void it_should_search_metrics_with_default_parameters(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(MetricsApi.SEARCH_PATH)
        .willReturn(aResponse().withResponseBody(
          Body.fromJsonBytes(generateSearchMetricsResponse().getBytes(StandardCharsets.UTF_8))
        )));
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(SearchMetricsTool.TOOL_NAME);

      assertResultEquals(result, """
        {
            "metrics":[
               {
                  "id":"23",
                  "key":"team_size",
                  "name":"Team size",
                  "description":"Number of people in the team",
                  "domain":"Management",
                  "type":"INT",
                  "direction":0,
                  "qualitative":false,
                  "hidden":false,
                  "custom":true
               },
               {
                  "id":"2",
                  "key":"uncovered_lines",
                  "name":"Uncovered lines",
                  "description":"Uncovered lines",
                  "domain":"Tests",
                  "type":"INT",
                  "direction":1,
                  "qualitative":true,
                  "hidden":false,
                  "custom":false
               }
            ],
            "total":2,
            "page":1,
            "pageSize":100
         }""");
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

      assertResultEquals(result, """
        {
            "metrics":[
               {
                  "id":"23",
                  "key":"team_size",
                  "name":"Team size",
                  "description":"Number of people in the team",
                  "domain":"Management",
                  "type":"INT",
                  "direction":0,
                  "qualitative":false,
                  "hidden":false,
                  "custom":true
               },
               {
                  "id":"2",
                  "key":"uncovered_lines",
                  "name":"Uncovered lines",
                  "description":"Uncovered lines",
                  "domain":"Tests",
                  "type":"INT",
                  "direction":1,
                  "qualitative":true,
                  "hidden":false,
                  "custom":false
               }
            ],
            "total":2,
            "page":1,
            "pageSize":100
         }""");
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
