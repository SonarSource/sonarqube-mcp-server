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

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

import static org.assertj.core.api.Assertions.assertThat;

class RunAdvancedCodeAnalysisToolTests {

  @Test
  void should_have_correct_tool_name() {
    var tool = new RunAdvancedCodeAnalysisTool();

    assertThat(tool.definition().name()).isEqualTo("run_advanced_code_analysis");
  }

  @Test
  void should_be_in_analysis_category() {
    var tool = new RunAdvancedCodeAnalysisTool();

    assertThat(tool.getCategory()).isEqualTo(ToolCategory.ANALYSIS);
  }

  @Test
  void should_be_marked_read_only() {
    var tool = new RunAdvancedCodeAnalysisTool();

    assertThat(tool.definition().annotations().readOnlyHint()).isTrue();
  }

  @Test
  void should_return_hello_world() {
    var tool = new RunAdvancedCodeAnalysisTool();

    var result = tool.execute(new Tool.Arguments(Map.of()));

    assertThat(result.isError()).isFalse();
    var content = result.toCallToolResult().content().get(0).toString();
    assertThat(content).contains("Hello, World");
  }

}
