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
package org.sonarsource.sonarqube.mcp.tools.analysis;

import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.qualityprofiles.QualityProfilesApi;
import org.sonarsource.sonarqube.mcp.serverapi.rules.RulesApi;
import org.sonarsource.sonarqube.mcp.slcore.BackendService;
import org.sonarsource.sonarqube.mcp.tools.Tool;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertSchemaEquals;
import static org.sonarsource.sonarqube.mcp.tools.analysis.AnalyzeCodeSnippetTool.TOOL_NAME;

class AnalyzeCodeSnippetToolTests {

  @SonarQubeMcpServerTest
  void it_should_validate_output_schema_and_annotations(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient();

    var tool = mcpClient.listTools().stream().filter(t -> t.name().equals(TOOL_NAME)).findFirst().orElseThrow();

    assertThat(tool.annotations()).isNotNull();
    assertThat(tool.annotations().readOnlyHint()).isTrue();
    assertThat(tool.annotations().openWorldHint()).isTrue();
    assertThat(tool.annotations().idempotentHint()).isFalse();
    assertThat(tool.annotations().destructiveHint()).isFalse();

    assertSchemaEquals(tool.outputSchema(), """
      {
        "type": "object",
        "properties": {
          "issueCount": {
            "type": "integer",
            "description": "Total number of issues"
          },
          "issues": {
            "type": "array",
            "description": "List of issues found in the code snippet",
            "items": {
              "type": "object",
              "properties": {
                "cleanCodeAttribute": {
                  "type": "string",
                  "description": "Clean code attribute"
                },
                "hasQuickFixes": {
                  "type": "boolean",
                  "description": "Whether quick fixes are available"
                },
                "impacts": {
                  "type": "string",
                  "description": "Software quality impacts"
                },
                "primaryMessage": {
                  "type": "string",
                  "description": "Primary issue message"
                },
                "ruleKey": {
                  "type": "string",
                  "description": "Rule key that triggered the issue"
                },
                "severity": {
                  "type": "string",
                  "description": "Issue severity level"
                },
                "textRange": {
                  "type": "object",
                  "properties": {
                    "endLine": {
                      "type": "integer",
                      "description": "Ending line number"
                    },
                    "startLine": {
                      "type": "integer",
                      "description": "Starting line number"
                    }
                  },
                  "required": ["endLine", "startLine"],
                  "description": "Location in the code"
                }
              },
              "required": [
                "cleanCodeAttribute",
                "hasQuickFixes",
                "impacts",
                "primaryMessage",
                "ruleKey",
                "severity"
              ]
            }
          }
        },
        "required": ["issueCount", "issues"]
      }
      """);
  }

  @Test
  void it_should_handle_initialization_failure() {
    // Create a future that completes exceptionally
    var failedFuture = new CompletableFuture<Void>();
    failedFuture.completeExceptionally(new RuntimeException("Initialization failed"));
    var tool = new AnalyzeCodeSnippetTool(mock(BackendService.class), mock(ServerApiProvider.class), failedFuture);

    var callToolResult = tool.execute(new Tool.Arguments(Map.of()));

    assertThat(callToolResult.isError()).isTrue();
    assertThat(callToolResult.toCallToolResult().toString()).contains("Server initialization failed: Initialization failed. " +
      "Please check the server logs for more details.");
  }

  @Nested
  class MissingPrerequisite {
    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_fileContent_is_missing(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(
        TOOL_NAME,
        Map.of(AnalyzeCodeSnippetTool.LANGUAGE_PROPERTY, ""));

      assertThat(result)
        .isEqualTo(McpSchema.CallToolResult.builder().isError(true).addTextContent("An error occurred during the tool execution: Missing required argument: fileContent").build());
    }
  }

  @Nested
  class Connected {

    @SonarQubeMcpServerTest
    void it_should_find_no_issues_in_empty_content(SonarQubeMcpServerTestHarness harness) {
      mockServerRules(harness, null, List.of("php:S1135"));
      var mcpClient = harness.withPlugins().newClient();

      var result = mcpClient.callTool(
        TOOL_NAME,
        Map.of(AnalyzeCodeSnippetTool.FILE_CONTENT_PROPERTY, ""));

      assertResultEquals(result, "{\"issues\":[],\"issueCount\":0}");
    }

    @SonarQubeMcpServerTest
    void it_should_find_an_issues_in_a_php_file_when_rule_enabled_in_default_quality_profile(SonarQubeMcpServerTestHarness harness) {
      mockServerRules(harness, null, List.of("php:S1135"));
      var mcpClient = harness.withPlugins().newClient();

      var result = mcpClient.callTool(
        TOOL_NAME,
        Map.of(
          AnalyzeCodeSnippetTool.FILE_CONTENT_PROPERTY, """
            // TODO just do it
            """,
          AnalyzeCodeSnippetTool.LANGUAGE_PROPERTY, "php"));

      assertResultEquals(result, """
        {
          "issues" : [ {
            "ruleKey" : "php:S1135",
            "primaryMessage" : "Complete the task associated to this \\"TODO\\" comment.",
            "severity" : "INFO",
            "cleanCodeAttribute" : "COMPLETE",
            "impacts" : "{MAINTAINABILITY=INFO}",
            "hasQuickFixes" : false,
            "textRange" : {
              "startLine" : 1,
              "endLine" : 1
            }
          } ],
          "issueCount" : 1
        }""");
    }

    @SonarQubeMcpServerTest
    void it_should_find_an_issues_in_a_php_file_when_rule_enabled_in_project_quality_profile(SonarQubeMcpServerTestHarness harness) {
      mockServerRules(harness, "projectKey", List.of("php:S1135"));
      var mcpClient = harness.withPlugins().newClient();

      var result = mcpClient.callTool(
        TOOL_NAME,
        Map.of(
          AnalyzeCodeSnippetTool.PROJECT_KEY_PROPERTY, "projectKey",
          AnalyzeCodeSnippetTool.FILE_CONTENT_PROPERTY, """
            // TODO just do it
            """,
          AnalyzeCodeSnippetTool.LANGUAGE_PROPERTY, "php"));

      assertResultEquals(result, """
        {
           "issues":[
              {
                 "ruleKey":"php:S1135",
                 "primaryMessage":"Complete the task associated to this \\"TODO\\" comment.",
                 "severity":"INFO",
                 "cleanCodeAttribute":"COMPLETE",
                 "impacts":"{MAINTAINABILITY=INFO}",
                 "hasQuickFixes":false,
                 "textRange":{
                    "startLine":1,
                    "endLine":1
                 }
              }
           ],
           "issueCount":1
        }""");
    }

    @SonarQubeMcpServerTest
    void it_should_find_no_issues_if_rule_is_not_active(SonarQubeMcpServerTestHarness harness) {
      mockServerRules(harness, null, List.of());
      var mcpClient = harness.withPlugins().newClient();

      var result = mcpClient.callTool(
        TOOL_NAME,
        Map.of(
          AnalyzeCodeSnippetTool.FILE_CONTENT_PROPERTY, """
            // TODO just do it
            """,
          AnalyzeCodeSnippetTool.LANGUAGE_PROPERTY, "php"));

      assertResultEquals(result, "{\"issues\":[],\"issueCount\":0}");
    }

    @SonarQubeMcpServerTest
    void it_should_accept_scope_parameter_with_main_value(SonarQubeMcpServerTestHarness harness) {
      mockServerRules(harness, null, List.of("php:S1135"));
      var mcpClient = harness.withPlugins().newClient();

      var result = mcpClient.callTool(
        TOOL_NAME,
        Map.of(
          AnalyzeCodeSnippetTool.FILE_CONTENT_PROPERTY, """
            // TODO just do it
            """,
          AnalyzeCodeSnippetTool.LANGUAGE_PROPERTY, "php",
          AnalyzeCodeSnippetTool.SCOPE_PROPERTY, new String[] {"MAIN"}));

      assertResultEquals(result, """
        {
          "issues" : [ {
            "ruleKey" : "php:S1135",
            "primaryMessage" : "Complete the task associated to this \\"TODO\\" comment.",
            "severity" : "INFO",
            "cleanCodeAttribute" : "COMPLETE",
            "impacts" : "{MAINTAINABILITY=INFO}",
            "hasQuickFixes" : false,
            "textRange" : {
              "startLine" : 1,
              "endLine" : 1
            }
          } ],
          "issueCount" : 1
        }""");
    }

    @SonarQubeMcpServerTest
    void it_should_accept_scope_parameter_with_test_value(SonarQubeMcpServerTestHarness harness) {
      mockServerRules(harness, null, List.of("php:S1135"));
      var mcpClient = harness.withPlugins().newClient();

      var result = mcpClient.callTool(
        TOOL_NAME,
        Map.of(
          AnalyzeCodeSnippetTool.FILE_CONTENT_PROPERTY, """
            // TODO just do it
            """,
          AnalyzeCodeSnippetTool.LANGUAGE_PROPERTY, "php",
          AnalyzeCodeSnippetTool.SCOPE_PROPERTY, new String[] {"TEST"}));

      assertResultEquals(result, "{\"issues\":[],\"issueCount\":0}");
    }

    @SonarQubeMcpServerTest
    void it_should_analyze_file_content(SonarQubeMcpServerTestHarness harness) {
      mockServerRules(harness, null, List.of("php:S1135"));
      var mcpClient = harness.withPlugins().newClient();

      var result = mcpClient.callTool(
        TOOL_NAME,
        Map.of(
          AnalyzeCodeSnippetTool.FILE_CONTENT_PROPERTY, "// TODO just do it\n",
          AnalyzeCodeSnippetTool.LANGUAGE_PROPERTY, "php"));

      assertResultEquals(result, """
        {
          "issues" : [ {
            "ruleKey" : "php:S1135",
            "primaryMessage" : "Complete the task associated to this \\"TODO\\" comment.",
            "severity" : "INFO",
            "cleanCodeAttribute" : "COMPLETE",
            "impacts" : "{MAINTAINABILITY=INFO}",
            "hasQuickFixes" : false,
            "textRange" : {
              "startLine" : 1,
              "endLine" : 1
            }
          } ],
          "issueCount" : 1
        }""");
    }

    @SonarQubeMcpServerTest
    void it_should_analyze_snippet_with_file_context_and_filter_issues(SonarQubeMcpServerTestHarness harness) {
      mockServerRules(harness, null, List.of("php:S1135"));
      var mcpClient = harness.withPlugins().newClient();

      var fileContent = """
        <?php
        // TODO existing issue line 2
        function foo() {
          // TODO new issue in snippet
          $x = 1;
          return true;
        }
        // TODO another issue line 8
        """;

      // Analyze only the snippet (tool will auto-detect it's at lines 4-5)
      var result = mcpClient.callTool(
        TOOL_NAME,
        Map.of(
          AnalyzeCodeSnippetTool.FILE_CONTENT_PROPERTY, fileContent,
          AnalyzeCodeSnippetTool.SNIPPET_PROPERTY, "  // TODO new issue in snippet\n  $x = 1;",
          AnalyzeCodeSnippetTool.LANGUAGE_PROPERTY, "php"));

      // Should only report the issue in the snippet (line 4), not the ones outside
      var resultStr = result.content().getFirst().toString();
      assertThat(resultStr).contains("\"issueCount\" : 1");
      assertThat(resultStr).contains("\"startLine\" : 4");
      assertThat(resultStr).contains("Complete the task associated to this");
      // Should not contain issues from line 2 or line 8
      assertThat(resultStr).doesNotContain("\"startLine\" : 2");
      assertThat(resultStr).doesNotContain("\"startLine\" : 8");
    }

    @SonarQubeMcpServerTest
    void it_should_auto_detect_snippet_location(SonarQubeMcpServerTestHarness harness) {
      mockServerRules(harness, null, List.of("php:S1135"));
      var mcpClient = harness.withPlugins().newClient();

      var fileContent = """
        <?php
        function foo() {
          // TODO implement this
          return null;
        }
        """;

      var result = mcpClient.callTool(
        TOOL_NAME,
        Map.of(
          AnalyzeCodeSnippetTool.FILE_CONTENT_PROPERTY, fileContent,
          AnalyzeCodeSnippetTool.SNIPPET_PROPERTY, "  // TODO implement this",
          AnalyzeCodeSnippetTool.LANGUAGE_PROPERTY, "php"));

      assertThat(result.isError()).isFalse();
      var resultStr = result.content().getFirst().toString();
      assertThat(resultStr).contains("\"issueCount\" : 1");
      assertThat(resultStr).contains("\"startLine\" : 3");
    }

    @SonarQubeMcpServerTest
    void it_should_error_when_snippet_not_found_in_file(SonarQubeMcpServerTestHarness harness) {
      mockServerRules(harness, null, List.of("php:S1135"));
      var mcpClient = harness.withPlugins().newClient();

      var result = mcpClient.callTool(
        TOOL_NAME,
        Map.of(
          AnalyzeCodeSnippetTool.FILE_CONTENT_PROPERTY, "<?php\necho 'hello';\n",
          AnalyzeCodeSnippetTool.SNIPPET_PROPERTY, "// This line does not exist in the file",
          AnalyzeCodeSnippetTool.LANGUAGE_PROPERTY, "php"));

      assertThat(result.isError()).isTrue();
      assertThat(result.toString()).contains("Could not find the provided code snippet");
    }

    @SonarQubeMcpServerTest
    void it_should_handle_windows_line_endings(SonarQubeMcpServerTestHarness harness) {
      mockServerRules(harness, null, List.of("php:S1135"));
      var mcpClient = harness.withPlugins().newClient();

      // File content with Windows line endings (\r\n)
      var fileContent = "<?php\r\n// TODO existing issue\r\nfunction foo() {\r\n  // TODO snippet issue\r\n  return true;\r\n}\r\n";
      
      // Snippet with Unix line endings (\n) - should still match
      var snippet = "  // TODO snippet issue\n  return true;";

      var result = mcpClient.callTool(
        TOOL_NAME,
        Map.of(
          AnalyzeCodeSnippetTool.FILE_CONTENT_PROPERTY, fileContent,
          AnalyzeCodeSnippetTool.SNIPPET_PROPERTY, snippet,
          AnalyzeCodeSnippetTool.LANGUAGE_PROPERTY, "php"));

      assertThat(result.isError()).isFalse();
      var resultStr = result.content().getFirst().toString();
      assertThat(resultStr).contains("\"issueCount\" : 1");
      assertThat(resultStr).contains("\"startLine\" : 4");
    }

  }

  private void mockServerRules(SonarQubeMcpServerTestHarness harness, @Nullable String projectKey, List<String> activeRuleKeys) {
    mockQualityProfiles(harness, projectKey, "qpKey");
    mockRules(harness, "qpKey", activeRuleKeys);
  }

  private static void mockQualityProfiles(SonarQubeMcpServerTestHarness harness, @Nullable String projectKey, String qualityProfileKey) {
    var query = projectKey == null ? "defaults=true" : ("project=" + projectKey);
    harness.getMockSonarQubeServer().stubFor(get(QualityProfilesApi.SEARCH_PATH + "?" + query).willReturn(okJson("""
      {
          "profiles": [
            {
              "key": "%s"
            }
          ]
        }
      """.formatted(qualityProfileKey))));
  }

  private static void mockRules(SonarQubeMcpServerTestHarness harness, String qualityProfileKey, List<String> activeRuleKeys) {
    var rulesPayload = activeRuleKeys.stream().map("""
      "%s": [
        {
          "params": []
        }
      ]
      """::formatted).collect(Collectors.joining(","));

    harness.getMockSonarQubeServer().stubFor(get(RulesApi.SEARCH_PATH + "?qprofile=" + qualityProfileKey + "&activation=true&f=templateKey%2Cactives&p=1").willReturn(okJson(
      """
        {
          "actives": {
            %s
          }
        }
        """.formatted(rulesPayload))));
  }

}
