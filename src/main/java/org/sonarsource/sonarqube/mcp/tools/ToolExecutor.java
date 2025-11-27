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
package org.sonarsource.sonarqube.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ForbiddenException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.NotFoundException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.UnauthorizedException;
import org.sonarsource.sonarqube.mcp.slcore.BackendService;

public class ToolExecutor {
  private static final McpLogger LOG = McpLogger.getInstance();
  private static final int INITIALIZATION_TIMEOUT_SECONDS = 30;
  private final BackendService backendService;
  private final CompletableFuture<Void> initializationFuture;

  public ToolExecutor(BackendService backendService, CompletableFuture<Void> initializationFuture) {
    this.backendService = backendService;
    this.initializationFuture = initializationFuture;
  }

  public McpSchema.CallToolResult execute(Tool tool, McpSchema.CallToolRequest toolRequest) {
    var toolName = tool.definition().name();
    LOG.info("Tool called: " + toolName);
    
    var startTime = System.currentTimeMillis();
    Tool.Result result;
    
    try {
      waitForInitialization(toolName);
      result = tool.execute(new Tool.Arguments(toolRequest.arguments()));
      logSuccess(toolName, startTime);
    } catch (TimeoutException | ExecutionException e) {
      result = handleInitializationError(e, toolName, startTime);
    } catch (Exception e) {
      result = handleExecutionError(e, toolName, startTime);
    }
    
    backendService.notifyToolCalled("mcp_" + tool.definition().name(), !result.isError());
    return result.toCallToolResult();
  }

  private void waitForInitialization(String toolName) throws ExecutionException, InterruptedException, TimeoutException {
    if (!initializationFuture.isDone()) {
      LOG.info("Waiting for server initialization to complete before executing tool: " + toolName);
    }
    initializationFuture.get(INITIALIZATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
  }

  private static void logSuccess(String toolName, long startTime) {
    var executionTime = System.currentTimeMillis() - startTime;
    LOG.info("Tool completed: " + toolName + " (execution time: " + executionTime + "ms)");
  }

  private static Tool.Result handleInitializationError(Exception e, String toolName, long startTime) {
    var executionTime = System.currentTimeMillis() - startTime;
    String errorMessage;
    
    if (e instanceof TimeoutException) {
      errorMessage = "Server initialization is taking longer than expected. Please try again in a moment.";
      LOG.error("Tool failed due to initialization timeout: " + toolName + " (execution time: " + executionTime + "ms)", e);
    } else {
      errorMessage = "Server initialization failed: " + e.getCause().getMessage() +
        ". Please check the server logs for more details.";
      LOG.error("Tool failed due to initialization error: " + toolName + " (execution time: " + executionTime + "ms)", e);
    }
    
    return Tool.Result.failure(errorMessage);
  }

  private static Tool.Result handleExecutionError(Exception e, String toolName, long startTime) {
    var executionTime = System.currentTimeMillis() - startTime;
    var message = formatErrorMessage(e);
    LOG.error("Tool failed: " + toolName + " (execution time: " + executionTime + "ms)", e);
    return Tool.Result.failure("An error occurred during the tool execution: " + message);
  }

  private static String formatErrorMessage(Exception e) {
    return switch (e) {
      case UnauthorizedException ex -> 
        ex.getMessage() + ". Please verify your token is valid and has the correct permissions.";
      case ForbiddenException ex -> 
        ex.getMessage() + ". Please verify your token has the required permissions for this operation.";
      case NotFoundException ex -> 
        ex.getMessage() + ". Please verify your token is valid and the requested resource exists.";
      case ResponseErrorException responseErrorException -> 
        responseErrorException.getResponseError().getMessage();
      default -> e.getMessage();
    };
  }

}
