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
package org.sonarsource.sonarqube.mcp.tools.analysis;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class AnalysisToolTests {

  @Nested
  class Failure {
    @SonarQubeMcpServerTest
    void it_should_return_an_error_if_codeSnippet_is_missing(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        AnalysisTool.TOOL_NAME,
        Map.of(
          AnalysisTool.LANGUAGE_PROPERTY, ""
        )));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("An error occurred during the tool execution: Missing required argument: codeSnippet", true));
    }
  }

  @Nested
  class Success {
    @SonarQubeMcpServerTest
    void it_should_find_no_issues_in_an_empty_file(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        AnalysisTool.TOOL_NAME,
        Map.of(
          AnalysisTool.SNIPPET_PROPERTY, "",
          AnalysisTool.LANGUAGE_PROPERTY, ""
        )));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("No Sonar issues found in the code snippet.", false));
    }

    @SonarQubeMcpServerTest
    void it_should_find_an_issues_in_a_php_file(SonarQubeMcpServerTestHarness harness) {
      var mcpClient = harness.newClient();

      var result = mcpClient.callTool(new McpSchema.CallToolRequest(
        AnalysisTool.TOOL_NAME,
        Map.of(
          AnalysisTool.SNIPPET_PROPERTY, """
            // TODO just do it
            """,
          AnalysisTool.LANGUAGE_PROPERTY, "php"
        )));

      assertThat(result)
        .isEqualTo(new McpSchema.CallToolResult("""
          Found 1 Sonar issues in the code snippet
          Complete the task associated to this "TODO" comment.
          Rule key: php:S1135
          Severity: INFO
          Clean Code attribute: CONVENTIONAL
          Impacts: {MAINTAINABILITY=INFO}
          Description: Complete the task associated to this "TODO" comment.
          Quick fixes available: No
          Starting on line: 1""", false));
    }
  }

}
