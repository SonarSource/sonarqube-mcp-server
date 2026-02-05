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
package org.sonarsource.sonarqube.mcp.tools.sources;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.harness.ReceivedRequest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.sources.SourcesApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarlint.core.serverapi.UrlUtils.urlEncode;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertSchemaEquals;

class GetFileCoverageDetailsToolTests {

  @Test
  void it_should_strip_html_tags() {
    assertThat(GetFileCoverageDetailsTool.stripHtmlTags("<span class=\"k\">public</span> <span class=\"k\">void</span> test()"))
      .isEqualTo("public void test()");
    assertThat(GetFileCoverageDetailsTool.stripHtmlTags("  return <span class=\"sym\">value</span>;"))
      .isEqualTo("  return value;");
    assertThat(GetFileCoverageDetailsTool.stripHtmlTags("no tags here"))
      .isEqualTo("no tags here");
  }

  @SonarQubeMcpServerTest
  void it_should_validate_output_schema_and_annotations(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient();

    var tool = mcpClient.listTools().stream()
      .filter(t -> t.name().equals(GetFileCoverageDetailsTool.TOOL_NAME))
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
          "fileKey": {
            "type": "string",
            "description": "File component key"
          },
          "filePath": {
            "type": "string",
            "description": "File path"
          },
          "partiallyConditionalLines": {
            "description": "List of lines with partially covered branches/conditions",
            "type": "array",
            "items": {
              "type": "object",
              "properties": {
                "code": {
                  "type": "string",
                  "description": "Source code on this line"
                },
                "coveredConditions": {
                  "type": "integer",
                  "description": "Number of conditions covered by tests"
                },
                "lineNumber": {
                  "type": "integer",
                  "description": "Line number (1-based)"
                },
                "totalConditions": {
                  "type": "integer",
                  "description": "Total number of conditions (branches) on this line"
                },
                "uncoveredConditions": {
                  "type": "integer",
                  "description": "Number of conditions not covered by tests"
                }
              },
              "required": ["code", "coveredConditions", "lineNumber", "totalConditions", "uncoveredConditions"]
            }
          },
          "summary": {
            "type": "object",
            "properties": {
              "branchCoveragePercent": {
                "type": "number",
                "description": "Branch coverage percentage"
              },
              "coverableLines": {
                "type": "integer",
                "description": "Number of coverable lines (executable code)"
              },
              "coveredConditions": {
                "type": "integer",
                "description": "Number of conditions covered by tests"
              },
              "coveredLines": {
                "type": "integer",
                "description": "Number of lines covered by tests"
              },
              "lineCoveragePercent": {
                "type": "number",
                "description": "Line coverage percentage"
              },
              "totalConditions": {
                "type": "integer",
                "description": "Total number of conditions (branches) to cover"
              },
              "totalLines": {
                "type": "integer",
                "description": "Total number of lines in the file"
              },
              "uncoveredConditions": {
                "type": "integer",
                "description": "Number of conditions not covered by tests"
              },
              "uncoveredLines": {
                "type": "integer",
                "description": "Number of lines not covered by tests"
              }
            },
            "required": ["branchCoveragePercent", "coverableLines", "coveredConditions", "coveredLines", "lineCoveragePercent", "totalConditions", "totalLines", "uncoveredConditions", "uncoveredLines"],
            "description": "Coverage summary for this file"
          },
          "uncoveredLines": {
            "description": "List of uncovered lines (lines that have never been executed by tests)",
            "type": "array",
            "items": {
              "type": "object",
              "properties": {
                "code": {
                  "type": "string",
                  "description": "Source code on this line"
                },
                "lineNumber": {
                  "type": "integer",
                  "description": "Line number (1-based)"
                }
              },
              "required": ["code", "lineNumber"]
            }
          }
        },
        "required": ["fileKey", "filePath", "partiallyConditionalLines", "summary", "uncoveredLines"]
      }
      """);
  }

  @Nested
  class MissingPrerequisite {

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_the_key_parameter_is_missing(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient(Map.of("SONARQUBE_TOKEN", "token", "SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(GetFileCoverageDetailsTool.TOOL_NAME);

      assertThat(result).isEqualTo(McpSchema.CallToolResult.builder()
        .isError(true)
        .addTextContent("An error occurred during the tool execution: Missing required argument: key")
        .build());
    }
  }

  @Nested
  class WithSonarCloudServer {

    @SonarQubeMcpServerTest
    void it_should_return_coverage_details_with_uncovered_lines(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(SourcesApi.SOURCES_LINES_PATH + "?key=" + urlEncode("my_project:src/Foo.java"))
        .willReturn(aResponse().withBody("""
          {
            "sources": [
              {"line": 1, "code": "<span class=\\"k\\">package</span> org.example;", "scmAuthor": "dev@example.com", "scmDate": "2025-01-01T12:00:00+0000", "scmRevision": "abc123", "isNew": false},
              {"line": 2, "code": "", "isNew": false},
              {"line": 3, "code": "<span class=\\"k\\">public class</span> Foo {", "lineHits": 5, "isNew": false},
              {"line": 4, "code": "  <span class=\\"k\\">public void</span> covered() {", "lineHits": 3, "isNew": false},
              {"line": 5, "code": "    System.out.println(\\"covered\\");", "lineHits": 3, "isNew": false},
              {"line": 6, "code": "  }", "lineHits": 3, "isNew": false},
              {"line": 7, "code": "  <span class=\\"k\\">public void</span> uncovered() {", "lineHits": 0, "isNew": false},
              {"line": 8, "code": "    System.out.println(\\"uncovered\\");", "lineHits": 0, "isNew": false},
              {"line": 9, "code": "  }", "lineHits": 0, "isNew": false},
              {"line": 10, "code": "}", "lineHits": 5, "isNew": false}
            ]
          }
          """)));

      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        GetFileCoverageDetailsTool.TOOL_NAME,
        Map.of("key", "my_project:src/Foo.java"));

      assertThat(result.isError()).isFalse();
      var json = new com.google.gson.Gson().toJson(result.structuredContent());
      assertThat(json).contains("\"fileKey\":\"my_project:src/Foo.java\"");
      assertThat(json).contains("\"filePath\":\"src/Foo.java\"");
      assertThat(json).contains("\"coverableLines\":7");
      assertThat(json).contains("\"uncoveredLines\":3");
      assertThat(json).contains("\"lineNumber\":7");
      assertThat(json).contains("\"lineNumber\":8");
      assertThat(json).contains("\"lineNumber\":9");
      assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
        .contains(new ReceivedRequest("Bearer token", ""));
    }

    @SonarQubeMcpServerTest
    void it_should_return_coverage_details_with_partially_covered_branches(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(SourcesApi.SOURCES_LINES_PATH + "?key=" + urlEncode("my_project:src/Bar.java"))
        .willReturn(aResponse().withBody("""
          {
            "sources": [
              {"line": 1, "code": "public class Bar {", "lineHits": 5, "isNew": false},
              {"line": 2, "code": "  public boolean check(int x, int y) {", "lineHits": 10, "isNew": false},
              {"line": 3, "code": "    if (x > 0 && y > 0) {", "lineHits": 10, "conditions": 2, "coveredConditions": 1, "isNew": false},
              {"line": 4, "code": "      return true;", "lineHits": 3, "isNew": false},
              {"line": 5, "code": "    }", "lineHits": 10, "isNew": false},
              {"line": 6, "code": "    return false;", "lineHits": 7, "isNew": false},
              {"line": 7, "code": "  }", "lineHits": 10, "isNew": false},
              {"line": 8, "code": "}", "lineHits": 5, "isNew": false}
            ]
          }
          """)));

      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        GetFileCoverageDetailsTool.TOOL_NAME,
        Map.of("key", "my_project:src/Bar.java"));

      assertThat(result.isError()).isFalse();
      var json = new com.google.gson.Gson().toJson(result.structuredContent());
      assertThat(json).contains("\"lineCoveragePercent\":100.0");
      assertThat(json).contains("\"branchCoveragePercent\":50.0");
      assertThat(json).contains("\"totalConditions\":2");
      assertThat(json).contains("\"coveredConditions\":1");
      assertThat(json).contains("\"lineNumber\":3");
      assertThat(json).contains("\"uncoveredConditions\":1");
      assertThat(json).contains("if (x \\u003e 0 \\u0026\\u0026 y \\u003e 0) {");
    }

    @SonarQubeMcpServerTest
    void it_should_handle_branch_parameter(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(SourcesApi.SOURCES_LINES_PATH + "?key=" + urlEncode("my_project:src/Foo.java") +
        "&branch=" + urlEncode("feature/my_branch"))
        .willReturn(aResponse().withBody("""
          {
            "sources": [
              {"line": 1, "code": "public class Foo {}", "lineHits": 5, "isNew": false}
            ]
          }
          """)));

      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        GetFileCoverageDetailsTool.TOOL_NAME,
        Map.of("key", "my_project:src/Foo.java", "branch", "feature/my_branch"));

      assertThat(result.isError()).isFalse();
    }

    @SonarQubeMcpServerTest
    void it_should_handle_from_and_to_parameters(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(SourcesApi.SOURCES_LINES_PATH + "?key=" + urlEncode("my_project:src/Foo.java") +
        "&from=10&to=20")
        .willReturn(aResponse().withBody("""
          {
            "sources": [
              {"line": 10, "code": "line 10", "lineHits": 1, "isNew": false},
              {"line": 11, "code": "line 11", "lineHits": 0, "isNew": false}
            ]
          }
          """)));

      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        GetFileCoverageDetailsTool.TOOL_NAME,
        Map.of("key", "my_project:src/Foo.java", "from", 10, "to", 20));

      assertThat(result.isError()).isFalse();
      var json = new com.google.gson.Gson().toJson(result.structuredContent());
      assertThat(json).contains("\"totalLines\":2");
    }

    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_file_not_found(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(SourcesApi.SOURCES_LINES_PATH + "?key=" + urlEncode("my_project:src/NonExistent.java"))
        .willReturn(aResponse().withStatus(404)));

      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        GetFileCoverageDetailsTool.TOOL_NAME,
        Map.of("key", "my_project:src/NonExistent.java"));

      assertThat(result.isError()).isTrue();
      var json = new com.google.gson.Gson().toJson(result.content());
      assertThat(json).contains("Failed to retrieve coverage details");
    }

    @SonarQubeMcpServerTest
    void it_should_handle_file_with_no_coverage_data(SonarQubeMcpServerTestHarness harness) {
      harness.getMockSonarQubeServer().stubFor(get(SourcesApi.SOURCES_LINES_PATH + "?key=" + urlEncode("my_project:src/Empty.java"))
        .willReturn(aResponse().withBody("""
          {
            "sources": [
              {"line": 1, "code": "// comment", "isNew": false},
              {"line": 2, "code": "", "isNew": false}
            ]
          }
          """)));

      var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

      var result = mcpClient.callTool(
        GetFileCoverageDetailsTool.TOOL_NAME,
        Map.of("key", "my_project:src/Empty.java"));

      assertThat(result.isError()).isFalse();
      var json = new com.google.gson.Gson().toJson(result.structuredContent());
      assertThat(json).contains("\"coverableLines\":0");
      assertThat(json).contains("\"lineCoveragePercent\":100.0");
    }
  }
}
