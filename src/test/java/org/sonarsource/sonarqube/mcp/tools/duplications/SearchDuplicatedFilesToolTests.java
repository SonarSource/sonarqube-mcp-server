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
package org.sonarsource.sonarqube.mcp.tools.duplications;

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
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertSchemaEquals;

class SearchDuplicatedFilesToolTests {

  @SonarQubeMcpServerTest
  void it_should_validate_output_schema_and_annotations(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient();

    var tool = mcpClient.listTools().stream().filter(t -> t.name().equals(SearchDuplicatedFilesTool.TOOL_NAME)).findFirst().orElseThrow();

    assertThat(tool.annotations()).isNotNull();
    assertThat(tool.annotations().readOnlyHint()).isTrue();
    assertThat(tool.annotations().openWorldHint()).isTrue();
    assertThat(tool.annotations().idempotentHint()).isFalse();
    assertThat(tool.annotations().destructiveHint()).isFalse();

    assertSchemaEquals(tool.outputSchema(), """
      {
        "type":"object",
        "properties":{
          "files":{
            "description":"List of files with duplications, sorted by most duplicated first",
            "type":"array",
            "items":{
              "type":"object",
              "properties":{
                "duplicatedBlocks":{
                  "type":"integer",
                  "description":"Number of duplicated blocks"
                },
                "duplicatedLines":{
                  "type":"integer",
                  "description":"Number of duplicated lines"
                },
                "duplicatedLinesDensity":{
                  "type":"string",
                  "description":"Duplication density percentage"
                },
                "key":{
                  "type":"string",
                  "description":"File key"
                },
                "name":{
                  "type":"string",
                  "description":"File name"
                },
                "path":{
                  "type":"string",
                  "description":"File path"
                }
              },
              "required":[
                "key",
                "name"
              ]
            }
          },
          "paging":{
            "description":"Pagination information",
            "type":"object",
            "properties":{
              "pageIndex":{
                "type":"integer",
                "description":"Current page number"
              },
              "pageSize":{
                "type":"integer",
                "description":"Number of results per page"
              },
              "total":{
                "type":"integer",
                "description":"Total number of duplicated files"
              }
            },
            "required":[
              "pageIndex",
              "pageSize",
              "total"
            ]
          },
          "summary":{
            "description":"Summary of duplication metrics",
            "type":"object",
            "properties":{
              "overallDuplicationDensity":{
                "type":"string",
                "description":"Overall duplication density percentage"
              },
              "totalDuplicatedBlocks":{
                "type":"integer",
                "description":"Total duplicated blocks in the project"
              },
              "totalDuplicatedLines":{
                "type":"integer",
                "description":"Total duplicated lines in the project"
              }
            }
          }
        },
        "required":[
          "files",
          "paging"
        ]
      }
      """);
  }

  @Nested
  class MissingPrerequisite {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_project_key_parameter_is_missing(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of("SONARQUBE_TOKEN", "token", "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(SearchDuplicatedFilesTool.TOOL_NAME);

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder().isError(true).addTextContent("An error occurred during the tool execution: Missing required argument: projectKey").build());
    }

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_page_size_is_invalid(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        SearchDuplicatedFilesTool.TOOL_NAME,
        Map.of("projectKey", "my_project", "pageSize", 0));

      assertThat(result.isError()).isTrue();
      assertThat(result.content().getFirst().toString()).contains("Page size must be between 1 and 500");
    }

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_page_index_is_invalid(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        SearchDuplicatedFilesTool.TOOL_NAME,
        Map.of("projectKey", "my_project", "pageIndex", 0));

      assertThat(result.isError()).isTrue();
      assertThat(result.content().getFirst().toString()).contains("Page index must be greater than 0");
    }
  }

  @Nested
  class WithSonarCloudServer {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_request_fails_due_to_insufficient_permissions(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(MeasuresApi.COMPONENT_PATH + "?component=my_project&metricKeys=duplicated_lines,duplicated_blocks,duplicated_lines_density&additionalFields=metrics").willReturn(aResponse().withStatus(403)));
      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        SearchDuplicatedFilesTool.TOOL_NAME,
        Map.of("projectKey", "my_project"));

      assertThat(result.isError()).isTrue();
      assertThat(result.content().getFirst().toString()).contains("SonarQube answered with Forbidden");
    }

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_project_is_not_found(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(MeasuresApi.COMPONENT_PATH + "?component=non_existent_project&metricKeys=duplicated_lines,duplicated_blocks,duplicated_lines_density&additionalFields=metrics").willReturn(aResponse().withStatus(404)));
      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        SearchDuplicatedFilesTool.TOOL_NAME,
        Map.of("projectKey", "non_existent_project"));

      assertThat(result.isError()).isTrue();
      assertThat(result.content().getFirst().toString()).contains("Error 404");
    }

    @SonarQubeMcpServerTest
    void it_should_return_duplicated_files_sorted_by_most_duplicated(SonarQubeMcpServerTestHarness harness) {
      var projectMeasuresJson = """
        {
          "component": {
            "key": "my_project",
            "name": "My Project",
            "qualifier": "TRK",
            "measures": [
              {
                "metric": "duplicated_lines",
                "value": "250"
              },
              {
                "metric": "duplicated_blocks",
                "value": "15"
              },
              {
                "metric": "duplicated_lines_density",
                "value": "5.2"
              }
            ]
          }
        }
        """;

      var componentTreeJson = """
        {
          "paging": {
            "pageIndex": 1,
            "pageSize": 500,
            "total": 3
          },
          "baseComponent": {
            "key": "my_project",
            "name": "My Project",
            "qualifier": "TRK"
          },
          "components": [
            {
              "key": "my_project:src/main/java/Duplicated1.java",
              "name": "Duplicated1.java",
              "qualifier": "FIL",
              "path": "src/main/java/Duplicated1.java",
              "measures": [
                {
                  "metric": "duplicated_lines",
                  "value": "150"
                },
                {
                  "metric": "duplicated_blocks",
                  "value": "10"
                },
                {
                  "metric": "duplicated_lines_density",
                  "value": "15.5"
                }
              ]
            },
            {
              "key": "my_project:src/main/java/Duplicated2.java",
              "name": "Duplicated2.java",
              "qualifier": "FIL",
              "path": "src/main/java/Duplicated2.java",
              "measures": [
                {
                  "metric": "duplicated_lines",
                  "value": "100"
                },
                {
                  "metric": "duplicated_blocks",
                  "value": "5"
                },
                {
                  "metric": "duplicated_lines_density",
                  "value": "8.3"
                }
              ]
            },
            {
              "key": "my_project:src/main/java/NoDuplication.java",
              "name": "NoDuplication.java",
              "qualifier": "FIL",
              "path": "src/main/java/NoDuplication.java",
              "measures": [
                {
                  "metric": "duplicated_lines",
                  "value": "0"
                }
              ]
            }
          ]
        }
        """;

      harness.getMockSonarQubeServer().stubFor(get(MeasuresApi.COMPONENT_PATH + "?component=my_project&metricKeys=duplicated_lines,duplicated_blocks,duplicated_lines_density&additionalFields=metrics")
        .willReturn(aResponse().withBody(projectMeasuresJson).withHeader("Content-Type", "application/json")));

      harness.getMockSonarQubeServer().stubFor(get(MeasuresApi.COMPONENT_TREE_PATH + "?component=my_project&metricKeys=duplicated_lines,duplicated_blocks,duplicated_lines_density&qualifiers=FIL&ps=500&p=1&strategy=leaves&additionalFields=metrics")
        .willReturn(aResponse().withBody(componentTreeJson).withHeader("Content-Type", "application/json")));

      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        SearchDuplicatedFilesTool.TOOL_NAME,
        Map.of("projectKey", "my_project"));

      assertResultEquals(result, """
        {
          "files" : [ {
            "key" : "my_project:src/main/java/Duplicated1.java",
            "name" : "Duplicated1.java",
            "path" : "src/main/java/Duplicated1.java",
            "duplicatedLines" : 150,
            "duplicatedBlocks" : 10,
            "duplicatedLinesDensity" : "15.5"
          }, {
            "key" : "my_project:src/main/java/Duplicated2.java",
            "name" : "Duplicated2.java",
            "path" : "src/main/java/Duplicated2.java",
            "duplicatedLines" : 100,
            "duplicatedBlocks" : 5,
            "duplicatedLinesDensity" : "8.3"
          } ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 2,
            "total" : 2
          },
          "summary" : {
            "totalDuplicatedLines" : 250,
            "totalDuplicatedBlocks" : 15,
            "overallDuplicationDensity" : "5.2"
          }
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_empty_list_when_no_duplications_found(SonarQubeMcpServerTestHarness harness) {
      var projectMeasuresJson = """
        {
          "component": {
            "key": "my_project",
            "name": "My Project",
            "qualifier": "TRK",
            "measures": [
              {
                "metric": "duplicated_lines",
                "value": "0"
              }
            ]
          }
        }
        """;

      var componentTreeJson = """
        {
          "paging": {
            "pageIndex": 1,
            "pageSize": 500,
            "total": 0
          },
          "baseComponent": {
            "key": "my_project",
            "name": "My Project",
            "qualifier": "TRK"
          },
          "components": []
        }
        """;

      harness.getMockSonarQubeServer().stubFor(get(MeasuresApi.COMPONENT_PATH + "?component=my_project&metricKeys=duplicated_lines,duplicated_blocks,duplicated_lines_density&additionalFields=metrics")
        .willReturn(aResponse().withBody(projectMeasuresJson).withHeader("Content-Type", "application/json")));

      harness.getMockSonarQubeServer().stubFor(get(MeasuresApi.COMPONENT_TREE_PATH + "?component=my_project&metricKeys=duplicated_lines,duplicated_blocks,duplicated_lines_density&qualifiers=FIL&ps=500&p=1&strategy=leaves&additionalFields=metrics")
        .willReturn(aResponse().withBody(componentTreeJson).withHeader("Content-Type", "application/json")));

      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        SearchDuplicatedFilesTool.TOOL_NAME,
        Map.of("projectKey", "my_project"));

      assertResultEquals(result, """
        {
          "files" : [ ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 0,
            "total" : 0
          },
          "summary" : {
            "totalDuplicatedLines" : 0
          }
        }""");
    }

    @SonarQubeMcpServerTest
    void it_should_support_pagination(SonarQubeMcpServerTestHarness harness) {
      var projectMeasuresJson = """
        {
          "component": {
            "key": "my_project",
            "name": "My Project",
            "qualifier": "TRK",
            "measures": []
          }
        }
        """;

      var componentTreeJson = """
        {
          "paging": {
            "pageIndex": 2,
            "pageSize": 50,
            "total": 150
          },
          "baseComponent": {
            "key": "my_project",
            "name": "My Project",
            "qualifier": "TRK"
          },
          "components": [
            {
              "key": "my_project:src/File51.java",
              "name": "File51.java",
              "qualifier": "FIL",
              "path": "src/File51.java",
              "measures": [
                {
                  "metric": "duplicated_lines",
                  "value": "10"
                }
              ]
            }
          ]
        }
        """;

      harness.getMockSonarQubeServer().stubFor(get(MeasuresApi.COMPONENT_PATH + "?component=my_project&metricKeys=duplicated_lines,duplicated_blocks,duplicated_lines_density&additionalFields=metrics")
        .willReturn(aResponse().withBody(projectMeasuresJson).withHeader("Content-Type", "application/json")));

      harness.getMockSonarQubeServer().stubFor(get(MeasuresApi.COMPONENT_TREE_PATH + "?component=my_project&metricKeys=duplicated_lines,duplicated_blocks,duplicated_lines_density&qualifiers=FIL&ps=50&p=2&strategy=leaves&additionalFields=metrics")
        .willReturn(aResponse().withBody(componentTreeJson).withHeader("Content-Type", "application/json")));

      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        SearchDuplicatedFilesTool.TOOL_NAME,
        Map.of("projectKey", "my_project", "pageSize", 50, "pageIndex", 2));

      assertThat(result.isError()).isFalse();
      assertResultEquals(result, """
        {
          "files" : [ {
            "key" : "my_project:src/File51.java",
            "name" : "File51.java",
            "path" : "src/File51.java",
            "duplicatedLines" : 10
          } ],
          "paging" : {
            "pageIndex" : 2,
            "pageSize" : 50,
            "total" : 150
          }
        }""");
    }
  }

  @Nested
  class WithSonarQubeServer {

    @SonarQubeMcpServerTest
    void it_should_return_duplicated_files_on_sonarqube_server(SonarQubeMcpServerTestHarness harness) {
      var projectMeasuresJson = """
        {
          "component": {
            "key": "my_project",
            "name": "My Project",
            "qualifier": "TRK",
            "measures": [
              {
                "metric": "duplicated_lines",
                "value": "100"
              }
            ]
          }
        }
        """;

      var componentTreeJson = """
        {
          "paging": {
            "pageIndex": 1,
            "pageSize": 500,
            "total": 1
          },
          "baseComponent": {
            "key": "my_project",
            "name": "My Project",
            "qualifier": "TRK"
          },
          "components": [
            {
              "key": "my_project:src/main/java/Duplicated.java",
              "name": "Duplicated.java",
              "qualifier": "FIL",
              "path": "src/main/java/Duplicated.java",
              "measures": [
                {
                  "metric": "duplicated_lines",
                  "value": "100"
                },
                {
                  "metric": "duplicated_blocks",
                  "value": "5"
                }
              ]
            }
          ]
        }
        """;

      harness.getMockSonarQubeServer().stubFor(get(MeasuresApi.COMPONENT_PATH + "?component=my_project&metricKeys=duplicated_lines,duplicated_blocks,duplicated_lines_density&additionalFields=metrics")
        .willReturn(aResponse().withBody(projectMeasuresJson).withHeader("Content-Type", "application/json")));

      harness.getMockSonarQubeServer().stubFor(get(MeasuresApi.COMPONENT_TREE_PATH + "?component=my_project&metricKeys=duplicated_lines,duplicated_blocks,duplicated_lines_density&qualifiers=FIL&ps=500&p=1&strategy=leaves&additionalFields=metrics")
        .willReturn(aResponse().withBody(componentTreeJson).withHeader("Content-Type", "application/json")));

      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        SearchDuplicatedFilesTool.TOOL_NAME,
        Map.of("projectKey", "my_project"));

      assertResultEquals(result, """
        {
          "files" : [ {
            "key" : "my_project:src/main/java/Duplicated.java",
            "name" : "Duplicated.java",
            "path" : "src/main/java/Duplicated.java",
            "duplicatedLines" : 100,
            "duplicatedBlocks" : 5
          } ],
          "paging" : {
            "pageIndex" : 1,
            "pageSize" : 1,
            "total" : 1
          },
          "summary" : {
            "totalDuplicatedLines" : 100
          }
        }""");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }
  }

}
