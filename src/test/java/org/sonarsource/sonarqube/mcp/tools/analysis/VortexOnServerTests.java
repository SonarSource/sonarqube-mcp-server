/*
 * SonarQube MCP Server
 * Copyright (C) SonarSource
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
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;

import static org.assertj.core.api.Assertions.assertThat;

class VortexOnServerTests {

  @SonarQubeMcpServerTest
  void it_should_not_register_vortex_analysis_on_server_when_both_hubs_are_entitled(SonarQubeMcpServerTestHarness harness) {
    harness.stubServerCagEntitlement(true);
    harness.stubServerA3sEntitlement(true);
    var mcpClient = harness.newClient();

    var toolNames = mcpClient.listTools().stream().map(McpSchema.Tool::name).toList();

    assertThat(toolNames)
      .contains(AnalyzeCodeSnippetTool.TOOL_NAME)
      .doesNotContain(RunAdvancedCodeAnalysisTool.TOOL_NAME);
  }

  @SonarQubeMcpServerTest
  void it_should_not_register_vortex_analysis_on_server_without_the_cag_hub(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient();

    var toolNames = mcpClient.listTools().stream().map(McpSchema.Tool::name).toList();

    assertThat(toolNames)
      .contains(AnalyzeCodeSnippetTool.TOOL_NAME)
      .doesNotContain(RunAdvancedCodeAnalysisTool.TOOL_NAME);
  }

  @SonarQubeMcpServerTest
  void it_should_not_register_vortex_analysis_on_an_unlicensed_server(SonarQubeMcpServerTestHarness harness) {
    harness.stubServerCagEntitlement(false);
    harness.stubServerA3sEntitlement(true);
    var mcpClient = harness.newClient();

    var toolNames = mcpClient.listTools().stream().map(McpSchema.Tool::name).toList();

    assertThat(toolNames)
      .contains(AnalyzeCodeSnippetTool.TOOL_NAME)
      .doesNotContain(RunAdvancedCodeAnalysisTool.TOOL_NAME);
  }
}
