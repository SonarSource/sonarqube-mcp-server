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
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.sonarsource.sonarqube.mcp.analytics.ConnectionContext;
import org.sonarsource.sonarqube.mcp.analytics.AnalyticsService;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ForbiddenException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.NotFoundException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.UnauthorizedException;
import org.sonarsource.sonarqube.mcp.slcore.BackendService;

public class ToolExecutor {
  private static final McpLogger LOG = McpLogger.getInstance();
  private final BackendService backendService;
  @Nullable
  private final AnalyticsService analyticsService;
  private final Supplier<ConnectionContext> connectionContextSupplier;

  public ToolExecutor(BackendService backendService) {
    this(backendService, null, ConnectionContext::empty);
  }

  public ToolExecutor(BackendService backendService, @Nullable AnalyticsService analyticsService,
    Supplier<ConnectionContext> connectionContextSupplier) {
    this.backendService = backendService;
    this.analyticsService = analyticsService;
    this.connectionContextSupplier = connectionContextSupplier;
  }

  public McpSchema.CallToolResult execute(Tool tool, McpSchema.CallToolRequest toolRequest) {
    var toolName = tool.definition().name();
    LOG.info("Tool called: " + toolName);

    var startTime = System.currentTimeMillis();
    Tool.Result result;

    try {
      result = tool.execute(new Tool.Arguments(toolRequest.arguments()));
      logSuccess(toolName, startTime);
    } catch (Exception e) {
      result = handleExecutionError(e, toolName, startTime);
    }

    var durationMs = System.currentTimeMillis() - startTime;
    var successful = !result.isError();
    backendService.notifyToolCalled("mcp_" + tool.definition().name(), successful);
    notifyAnalytics(toolName, durationMs, successful);
    return result.toCallToolResult();
  }

  private void notifyAnalytics(String toolName, long durationMs, boolean successful) {
    var service = analyticsService;
    if (service == null) {
      return;
    }
    CompletableFuture.runAsync(() -> {
      try {
        var ctx = connectionContextSupplier.get();
        service.notifyToolInvoked(toolName, ctx.getOrganizationUuidV4(), ctx.getSqsInstallationId(), ctx.getUserUuid(),
          ctx.getCallingAgentName(), ctx.getCallingAgentVersion(), durationMs, successful);
      } catch (Exception e) {
        LOG.debug("Failed to send analytics event for tool " + toolName + ": " + e.getMessage());
      }
    });
  }

  private static void logSuccess(String toolName, long startTime) {
    var executionTime = System.currentTimeMillis() - startTime;
    LOG.info("Tool completed: " + toolName + " (execution time: " + executionTime + "ms)");
  }

  private static Tool.Result handleExecutionError(Exception e, String toolName, long startTime) {
    var executionTime = System.currentTimeMillis() - startTime;
    var message = formatErrorMessage(e);
    LOG.error("Tool failed: " + toolName + " (execution time: " + executionTime + "ms)", e);
    return Tool.Result.failure("An error occurred during the tool execution: " + message);
  }

  private static String formatErrorMessage(Exception e) {
    return switch (e) {
      case UnauthorizedException ex -> ex.getMessage() + ". Please verify your token is valid and has the correct permissions.";
      case ForbiddenException ex -> ex.getMessage() + ". Please verify your token has the required permissions for this operation.";
      case NotFoundException ex -> ex.getMessage() + ". Please verify your token is valid and the requested resource exists.";
      case ResponseErrorException responseErrorException -> responseErrorException.getResponseError().getMessage();
      default -> e.getMessage();
    };
  }

}
