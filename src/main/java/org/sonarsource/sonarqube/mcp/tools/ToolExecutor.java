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
package org.sonarsource.sonarqube.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.serverapi.exception.NotFoundException;
import org.sonarsource.sonarqube.mcp.slcore.BackendService;

public class ToolExecutor {
  private static final McpLogger LOG = McpLogger.getInstance();
  private final BackendService backendService;

  public ToolExecutor(BackendService backendService) {
    this.backendService = backendService;
  }

  public McpSchema.CallToolResult execute(Tool tool, McpSchema.CallToolRequest toolRequest) {
    var toolName = tool.definition().name();
    LOG.info("Tool called: " + toolName);
    
    var startTime = System.currentTimeMillis();
    Tool.Result result;
    try {
      result = tool.execute(new Tool.Arguments(toolRequest.arguments()));
      var executionTime = System.currentTimeMillis() - startTime;
      LOG.info("Tool completed: " + toolName + " (execution time: " + executionTime + "ms)");
    } catch (Exception e) {
      var executionTime = System.currentTimeMillis() - startTime;
      String message;
      if (e instanceof NotFoundException) {
        message = "Make sure your token is valid.";
      } else {
        message = e instanceof ResponseErrorException responseErrorException ? responseErrorException.getResponseError().getMessage() : e.getMessage();
      }
      result = Tool.Result.failure("An error occurred during the tool execution: " + message);
      LOG.error("Tool failed: " + toolName + " (execution time: " + executionTime + "ms)", e);
    }
    backendService.notifyToolCalled("mcp_" + tool.definition().name(), !result.isError());
    return result.toCallToolResult();
  }
}
