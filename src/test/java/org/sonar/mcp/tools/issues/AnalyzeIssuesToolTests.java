/*
 * Sonar MCP Server
 * Copyright (C) 2025 SonarSource
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.mcp.tools.issues;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.sonar.mcp.harness.SonarMcpServerTest;
import org.sonar.mcp.harness.SonarMcpServerTestHarness;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("This tool is not supported for now, will be re-introduced later")
class AnalyzeIssuesToolTests {

  @Nested
  class Failure {
    @SonarMcpServerTest
    void it_should_return_an_error_if_codeSnippet_is_missing(SonarMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        AnalyzeIssuesTool.TOOL_NAME,
        Map.of(
          AnalyzeIssuesTool.LANGUAGE_PROPERTY, ""
        )));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Missing required argument: codeSnippet", true));
    }

    @SonarMcpServerTest
    void it_should_fail_to_analyze_incorrect_file(SonarMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        AnalyzeIssuesTool.TOOL_NAME,
        Map.of(
          AnalyzeIssuesTool.SNIPPET_PROPERTY, "<? wrong\ncode",
          AnalyzeIssuesTool.LANGUAGE_PROPERTY, "java"
        )));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("Failed to analyze the code snippet.", false));
    }
  }

  @Nested
  class Success {
    @SonarMcpServerTest
    void it_should_find_no_issues_in_an_empty_file(SonarMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        AnalyzeIssuesTool.TOOL_NAME,
        Map.of(
          AnalyzeIssuesTool.SNIPPET_PROPERTY, "",
          AnalyzeIssuesTool.LANGUAGE_PROPERTY, ""
        )));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("No Sonar issues found in the code snippet.", false));
    }

    @SonarMcpServerTest
    void it_should_find_an_issues_in_a_php_file(SonarMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        AnalyzeIssuesTool.TOOL_NAME,
        Map.of(
          AnalyzeIssuesTool.SNIPPET_PROPERTY, """
            // TODO just do it
            """,
          AnalyzeIssuesTool.LANGUAGE_PROPERTY, "php"
        )));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          Found 1 Sonar issues in the code snippet
          Complete the task associated to this "TODO" comment.
          Rule key: php:S1135
          Severity: INFO
          Clean Code attribute: COMPLETE
          Impacts: {MAINTAINABILITY=INFO}
          Description: Complete the task associated to this "TODO" comment.
          Quick fixes available: No
          Starting on line: 1""", false));
    }
  }

}
