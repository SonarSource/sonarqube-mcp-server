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

import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class RunAdvancedCodeAnalysisTool extends Tool {

  public static final String TOOL_NAME = "run_advanced_code_analysis";

  public record RunAdvancedCodeAnalysisToolResponse(String message) {
  }

  public RunAdvancedCodeAnalysisTool() {
    super(SchemaToolBuilder.forOutput(RunAdvancedCodeAnalysisToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Advanced Code Analysis")
      .setDescription("Run advanced code analysis remotely using SonarQube Cloud advanced analysis.")
      .setReadOnlyHint()
      .build(),
      ToolCategory.ANALYSIS);
  }

  @Override
  public Result execute(Arguments arguments) {
    return Result.success(new RunAdvancedCodeAnalysisToolResponse("Hello, World"));
  }

}
