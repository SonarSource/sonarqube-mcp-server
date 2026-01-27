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
package org.sonarsource.sonarqube.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.tools.analysis.AnalyzeCodeSnippetTool;
import org.sonarsource.sonarqube.mcp.tools.analysis.AnalyzeFileListTool;
import org.sonarsource.sonarqube.mcp.tools.analysis.RunAdvancedCodeAnalysisTool;
import org.sonarsource.sonarqube.mcp.tools.analysis.ToggleAutomaticAnalysisTool;

import static org.assertj.core.api.Assertions.assertThat;

class AdvancedAnalysisEnablementTest {

  @AfterEach
  void cleanup() {
    System.clearProperty("SONARQUBE_ADVANCED_ANALYSIS_ENABLED");
  }

  @SonarQubeMcpServerTest
  void should_register_only_advanced_analysis_tool_on_sonarcloud(SonarQubeMcpServerTestHarness harness) {
    System.setProperty("SONARQUBE_ADVANCED_ANALYSIS_ENABLED", "true");

    var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

    var toolNames = mcpClient.listTools().stream().map(McpSchema.Tool::name).toList();

    assertThat(toolNames).contains(RunAdvancedCodeAnalysisTool.TOOL_NAME);
    assertThat(toolNames).doesNotContain(AnalyzeCodeSnippetTool.TOOL_NAME);
    assertThat(toolNames).doesNotContain(AnalyzeFileListTool.TOOL_NAME);
    assertThat(toolNames).doesNotContain(ToggleAutomaticAnalysisTool.TOOL_NAME);
  }

  @SonarQubeMcpServerTest
  void should_log_warning_and_fallback_to_standard_analysis_on_sonarqube_server(SonarQubeMcpServerTestHarness harness) {
    var originalErr = System.err;
    var errBuffer = new ByteArrayOutputStream();
    System.setErr(new PrintStream(errBuffer, true, StandardCharsets.UTF_8));

    try {
      System.setProperty("SONARQUBE_ADVANCED_ANALYSIS_ENABLED", "true");

      var mcpClient = harness.newClient();

      var toolNames = mcpClient.listTools().stream().map(McpSchema.Tool::name).toList();

      assertThat(toolNames).doesNotContain(RunAdvancedCodeAnalysisTool.TOOL_NAME);
      assertThat(toolNames).contains(AnalyzeCodeSnippetTool.TOOL_NAME);

      var stderr = errBuffer.toString(StandardCharsets.UTF_8);
      assertThat(stderr).contains("SONARQUBE_ADVANCED_ANALYSIS_ENABLED is set but advanced analysis is only available on SonarCloud");
    } finally {
      System.setErr(originalErr);
    }
  }

}
