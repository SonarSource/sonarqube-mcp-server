/*
 * Sonar MCP Server
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
package org.sonar.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.sonar.mcp.log.McpLogger;
import org.sonar.mcp.slcore.BackendService;

public class ToolExecutor {
  private final McpLogger logger = McpLogger.getInstance();
  private final BackendService backendService;

  public ToolExecutor(BackendService backendService) {
    this.backendService = backendService;
  }

  public McpSchema.CallToolResult execute(Tool tool, Map<String, Object> arguments) {
    Tool.Result result;
    try {
      result = tool.execute(arguments);
    } catch (Exception e) {
      result = Tool.Result.failure("An error occurred during the tool execution",  e);
      logger.error("An error occurred during the tool execution", e);
    }
    backendService.notifyToolCalled("mcp." + tool.definition().name(), !result.isError());
    return result.toCallToolResult();
  }
}
