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
package org.sonarsource.sonarqube.mcp.tools.measures;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.sonarsource.sonarqube.mcp.harness.ReceivedRequest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.measures.MeasuresApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.serverapi.UrlUtils.urlEncode;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertSchemaEquals;

class SearchFilesByCoverageToolTests {

  @SonarQubeMcpServerTest
  void it_should_validate_output_schema_and_annotations(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient();

    var tool = mcpClient.listTools().stream()
      .filter(t -> t.name().equals(SearchFilesByCoverageTool.TOOL_NAME))
      .findFirst().orElseThrow();

    assertThat(tool.annotations()).isNotNull();
    assertThat(tool.annotations().readOnlyHint()).isTrue();
    assertThat(tool.annotations().openWorldHint()).isTrue();
    assertThat(tool.annotations().idempotentHint()).isFalse();
    assertThat(tool.annotations().destructiveHint()).isFalse();

    assertSchemaEquals(tool.outputSchema(), """
      {
        "type": "object",
        "properties": {
          "projectKey": {
            "type": "string",
            "description": "Project key"
          },
          "totalFiles": {
            "type": "integer",
            "description": "Total number of files in the project"
          },
          "filesReturned": {
            "type": "integer",
            "description": "Number of files returned in this response"
          },
          "pageIndex": {
            "type": "integer",
            "description": "Current page index"
          },
          "pageSize": {
            "type": "integer",
            "description": "Page size"
          },
          "projectSummary": {
            "type": "object",
            "properties": {
              "coverage": {
                "type": "number",
                "description": "Overall project coverage percentage"
              },
              "linesToCover": {
                "type": "integer",
                "description": "Total lines to cover in the project"
              },
              "uncoveredLines": {
                "type": "integer",
                "description": "Total uncovered lines in the project"
              }
            },
            "description": "Project-level coverage summary"
          },
          "files": {
            "type": "array",
            "items": {
              "type": "object",
              "properties": {
                "key": {
                  "type": "string",
                  "description": "File component key"
                },
                "path": {
                  "type": "string",
                  "description": "File path relative to project root"
                },
                "coverage": {
                  "type": "number",
                  "description": "Overall coverage percentage for this file"
                },
                "lineCoverage": {
                  "type": "number",
                  "description": "Line coverage percentage"
                },
                "branchCoverage": {
                  "type": "number",
                  "description": "Branch coverage percentage"
                },
                "linesToCover": {
                  "type": "integer",
                  "description": "Number of lines to cover"
                },
                "uncoveredLines": {
                  "type": "integer",
                  "description": "Number of uncovered lines"
                },
                "conditionsToCover": {
                  "type": "integer",
                  "description": "Number of conditions (branches) to cover"
                },
                "uncoveredConditions": {
                  "type": "integer",
                  "description": "Number of uncovered conditions"
                }
              },
              "required": ["key", "path"]
            },
            "description": "List of files with coverage information, sorted by coverage (ascending)"
          }
        },
        "required": ["projectKey", "totalFiles", "filesReturned", "pageIndex", "pageSize", "files"]
      }
      """);
  }

  @Nested
  class MissingPrerequisite {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_project_key_parameter_is_missing(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of("SONARQUBE_TOKEN", "token", "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(SearchFilesByCoverageTool.TOOL_NAME);

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder()
        .isError(true)
        .addTextContent("An error occurred during the tool execution: Missing required argument: projectKey")
        .build());
    }

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_min_coverage_is_invalid(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of("SONARQUBE_TOKEN", "token", "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        SearchFilesByCoverageTool.TOOL_NAME,
        Map.of("projectKey", "my_project", "minCoverage", -5));

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder()
        .isError(true)
        .addTextContent("minCoverage must be between 0 and 100")
        .build());
    }

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_max_coverage_is_invalid(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of("SONARQUBE_TOKEN", "token", "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        SearchFilesByCoverageTool.TOOL_NAME,
        Map.of("projectKey", "my_project", "maxCoverage", 150));

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder()
        .isError(true)
        .addTextContent("maxCoverage must be between 0 and 100")
        .build());
    }

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_min_coverage_is_greater_than_max_coverage(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of("SONARQUBE_TOKEN", "token", "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        SearchFilesByCoverageTool.TOOL_NAME,
        Map.of("projectKey", "my_project", "minCoverage", 80, "maxCoverage", 50));

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder()
        .isError(true)
        .addTextContent("minCoverage cannot be greater than maxCoverage")
        .build());
    }
  }

  @Nested
  class WithSonarCloudServer {

    @SonarQubeMcpServerTest
    void it_should_return_files_sorted_by_coverage(SonarQubeMcpServerTestHarness harness) {
      // Mock project-level metrics
      harness.getMockSonarQubeServer().stubFor(get(MeasuresApi.COMPONENT_PATH + "?component=my_project&metricKeys=coverage" +
        "%2Clines_to_cover%2Cuncovered_lines&additionalFields=metrics")
        .willReturn(aResponse().withBody("""
          {
            "component": {
              "key": "my_project",
              "name": "My Project",
              "qualifier": "TRK",
              "measures": [
                {"metric": "coverage", "value": "85.5"},
                {"metric": "lines_to_cover", "value": "1000"},
                {"metric": "uncovered_lines", "value": "145"}
              ]
            }
          }
          """)));

      // Mock component tree
      harness.getMockSonarQubeServer().stubFor(get(MeasuresApi.COMPONENT_TREE_PATH + "?component=my_project&metricKeys=coverage" +
        "%2Cline_coverage%2Cbranch_coverage%2Clines_to_cover%2Cuncovered_lines%2Cconditions_to_cover%2Cuncovered_conditions&qualifiers" +
        "=FIL&strategy=all&s=metric&metricSort=coverage&asc=true&p=1&ps=100")
        .willReturn(aResponse().withBody("""
          {
            "baseComponent": {
              "key": "my_project",
              "name": "My Project",
              "qualifier": "TRK"
            },
            "components": [
              {
                "key": "my_project:src/main/java/Foo.java",
                "name": "Foo.java",
                "qualifier": "FIL",
                "path": "src/main/java/Foo.java",
                "language": "java",
                "measures": [
                  {"metric": "coverage", "value": "45.5"},
                  {"metric": "line_coverage", "value": "50.0"},
                  {"metric": "branch_coverage", "value": "40.0"},
                  {"metric": "lines_to_cover", "value": "100"},
                  {"metric": "uncovered_lines", "value": "50"},
                  {"metric": "conditions_to_cover", "value": "20"},
                  {"metric": "uncovered_conditions", "value": "12"}
                ]
              },
              {
                "key": "my_project:src/main/java/Bar.java",
                "name": "Bar.java",
                "qualifier": "FIL",
                "path": "src/main/java/Bar.java",
                "language": "java",
                "measures": [
                  {"metric": "coverage", "value": "78.5"},
                  {"metric": "line_coverage", "value": "80.0"},
                  {"metric": "branch_coverage", "value": "75.0"},
                  {"metric": "lines_to_cover", "value": "50"},
                  {"metric": "uncovered_lines", "value": "10"},
                  {"metric": "conditions_to_cover", "value": "10"},
                  {"metric": "uncovered_conditions", "value": "2"}
                ]
              }
            ],
            "paging": {
              "pageIndex": 1,
              "pageSize": 100,
              "total": 2
            }
          }
          """)));

      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        SearchFilesByCoverageTool.TOOL_NAME,
        Map.of("projectKey", "my_project"));

      assertThat(result.isError()).isFalse();
      var json = new com.google.gson.Gson().toJson(result.structuredContent());
      assertThat(json).contains("\"projectKey\":\"my_project\"");
      assertThat(json).contains("\"totalFiles\":2");
      assertThat(json).contains("\"filesReturned\":2");
      assertThat(json).contains("\"coverage\":85.5");
      assertThat(json).contains("my_project:src/main/java/Foo.java");
      assertThat(json).contains("my_project:src/main/java/Bar.java");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_filter_files_by_min_coverage(SonarQubeMcpServerTestHarness harness) {
      // Mock project metrics
      harness.getMockSonarQubeServer().stubFor(get(MeasuresApi.COMPONENT_PATH + "?component=my_project&metricKeys=coverage" +
        "%2Clines_to_cover%2Cuncovered_lines&additionalFields=metrics")
        .willReturn(aResponse().withBody("""
          {
            "component": {
              "key": "my_project",
              "name": "My Project",
              "qualifier": "TRK",
              "measures": []
            }
          }
          """)));

      // Mock component tree
      harness.getMockSonarQubeServer().stubFor(get(MeasuresApi.COMPONENT_TREE_PATH + "?component=my_project&metricKeys=coverage" +
        "%2Cline_coverage%2Cbranch_coverage%2Clines_to_cover%2Cuncovered_lines%2Cconditions_to_cover%2Cuncovered_conditions&qualifiers" +
        "=FIL&strategy=all&s=metric&metricSort=coverage&asc=true&p=1&ps=100")
        .willReturn(aResponse().withBody("""
          {
            "components": [
              {
                "key": "my_project:src/Foo.java",
                "name": "Foo.java",
                "qualifier": "FIL",
                "path": "src/Foo.java",
                "measures": [{"metric": "coverage", "value": "45.5"}]
              },
              {
                "key": "my_project:src/Bar.java",
                "name": "Bar.java",
                "qualifier": "FIL",
                "path": "src/Bar.java",
                "measures": [{"metric": "coverage", "value": "78.5"}]
              }
            ],
            "paging": {"pageIndex": 1, "pageSize": 100, "total": 2}
          }
          """)));

      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        SearchFilesByCoverageTool.TOOL_NAME,
        Map.of("projectKey", "my_project", "minCoverage", 70));

      // Only Bar.java should be returned (coverage >= 70)
      assertThat(result.isError()).isFalse();
      var json = new com.google.gson.Gson().toJson(result.structuredContent());
      assertThat(json).contains("\"filesReturned\":1");
      assertThat(json).contains("my_project:src/Bar.java");
    }

    @SonarQubeMcpServerTest
    void it_should_handle_branch_parameter(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(MeasuresApi.COMPONENT_PATH + "?component=my_project&branch=" + urlEncode("feature" +
        "/my_branch") + "&metricKeys=coverage%2Clines_to_cover%2Cuncovered_lines&additionalFields=metrics")
        .willReturn(aResponse().withBody("""
          {
            "component": {"key": "my_project", "name": "My Project", "qualifier": "TRK", "measures": []}
          }
          """)));

      harness.getMockSonarQubeServer().stubFor(get(MeasuresApi.COMPONENT_TREE_PATH + "?component=my_project&branch=" + urlEncode("feature" +
        "/my_branch") + "&metricKeys=coverage%2Cline_coverage%2Cbranch_coverage%2Clines_to_cover%2Cuncovered_lines%2Cconditions_to_cover" +
        "%2Cuncovered_conditions&qualifiers=FIL&strategy=all&s=metric&metricSort=coverage&asc=true&p=1&ps=100")
        .willReturn(aResponse().withBody("""
          {
            "components": [],
            "paging": {"pageIndex": 1, "pageSize": 100, "total": 0}
          }
          """)));

      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        SearchFilesByCoverageTool.TOOL_NAME,
        Map.of("projectKey", "my_project", "branch", "feature/my_branch"));

      assertThat(result.isError()).isFalse();
    }
  }
}
