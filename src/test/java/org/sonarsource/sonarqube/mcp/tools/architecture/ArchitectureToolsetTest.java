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
package org.sonarsource.sonarqube.mcp.tools.architecture;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureToolsetTest {

  private static final List<String> READ_TOOLS = List.of(ArchitectureGetTool.TOOL_NAME);

  private static final List<String> WRITE_TOOLS = List.of(
    ArchitectureSetTool.TOOL_NAME);

  @SonarQubeMcpServerTest
  void it_should_not_list_architecture_tools_with_the_default_toolsets(SonarQubeMcpServerTestHarness harness) {
    var toolNames = toolNames(harness, Map.of("SONARQUBE_TOOLSETS", ""));

    assertThat(toolNames).isNotEmpty().noneMatch(name -> name.startsWith("architecture_"));
  }

  @SonarQubeMcpServerTest
  void it_should_not_list_architecture_tools_when_other_toolsets_are_enabled(SonarQubeMcpServerTestHarness harness) {
    var toolNames = toolNames(harness, Map.of("SONARQUBE_TOOLSETS", "issues"));

    assertThat(toolNames).noneMatch(name -> name.startsWith("architecture_"));
  }

  @SonarQubeMcpServerTest
  void it_should_list_architecture_tools_when_the_toolset_is_enabled(SonarQubeMcpServerTestHarness harness) {
    var toolNames = toolNames(harness, Map.of("SONARQUBE_TOOLSETS", "architecture"));

    assertThat(toolNames).containsAll(READ_TOOLS).containsAll(WRITE_TOOLS)
      .doesNotContain("get_current_architecture", "get_intended_architecture", "architecture_get_current", "architecture_get_intended", "architecture_get_directives", "architecture_set_intended", "architecture_set_directives");
  }

  @SonarQubeMcpServerTest
  void it_should_hide_the_set_tools_in_read_only_mode(SonarQubeMcpServerTestHarness harness) {
    var toolNames = toolNames(harness, Map.of("SONARQUBE_TOOLSETS", "architecture", "SONARQUBE_READ_ONLY", "true"));

    assertThat(toolNames).containsAll(READ_TOOLS).doesNotContainAnyElementsOf(WRITE_TOOLS);
  }

  private static List<String> toolNames(SonarQubeMcpServerTestHarness harness, Map<String, String> env) {
    return harness.newClient(env).listTools().stream().map(McpSchema.Tool::name).toList();
  }

}
