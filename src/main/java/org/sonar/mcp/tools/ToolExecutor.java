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
    McpSchema.CallToolResult result;
    try {
      result = tool.execute(arguments);
    } catch (Exception e) {
      result = new McpSchema.CallToolResult("An error occurred during the tool execution: " + e.getMessage(), true);
      logger.error("An error occurred during the tool execution", e);
    }
    backendService.notifyToolCalled("mcp." + tool.definition().name(), !result.isError());
    return result;
  }
}
