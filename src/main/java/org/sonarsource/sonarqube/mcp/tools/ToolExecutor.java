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
package org.sonarsource.sonarqube.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.UUID;
import java.util.function.Supplier;
import jakarta.annotation.Nullable;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.sonarsource.sonarqube.mcp.analytics.AnalyticsService;
import org.sonarsource.sonarqube.mcp.analytics.ConnectionContext;
import org.sonarsource.sonarqube.mcp.analytics.ToolInvocationResult;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ForbiddenException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.NotFoundException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ServerApiException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ServerInternalErrorException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.UnauthorizedException;
import org.sonarsource.sonarqube.mcp.slcore.BackendService;
import org.sonarsource.sonarqube.mcp.tools.exception.MissingRequiredArgumentException;

public class ToolExecutor {
  private static final McpLogger LOG = McpLogger.getInstance();
  private record InvocationMetrics(String invocationId, long durationMs, boolean successful, @Nullable String errorType, long responseSizeBytes, long invocationTimestamp) {}
  private final BackendService backendService;
  @Nullable
  private final AnalyticsService analyticsService;

  /**
   * Pre-resolved connection context for stdio mode. Null when running in HTTP mode.
   */
  @Nullable
  private final ConnectionContext stdioContext;

  /**
   * Supplies the request-scoped {@link ServerApi} for HTTP mode. Null when running in stdio mode.
   */
  @Nullable
  private final Supplier<ServerApi> httpServerApiSupplier;

  /**
   * MCP server ID to include in the _meta field for proxied servers to correlate telemetry.
   */
  @Nullable
  private final String mcpServerId;

  public ToolExecutor(BackendService backendService) {
    this(backendService, null, null, null, null);
  }

  public ToolExecutor(BackendService backendService, @Nullable AnalyticsService analyticsService,
    @Nullable ConnectionContext stdioContext, @Nullable Supplier<ServerApi> httpServerApiSupplier, @Nullable String mcpServerId) {
    this.backendService = backendService;
    this.analyticsService = analyticsService;
    this.stdioContext = stdioContext;
    this.httpServerApiSupplier = httpServerApiSupplier;
    this.mcpServerId = mcpServerId;
  }

  public McpSchema.CallToolResult execute(Tool tool, McpSchema.CallToolRequest toolRequest) {
    var toolName = tool.definition().name();
    LOG.info("Tool called: " + toolName);

    var invocationTimestamp = System.currentTimeMillis();
    Tool.Result result;
    String errorType = null;
    var invocationId = UUID.randomUUID().toString();
    var meta = new HashMap<String, Object>();
    if (toolRequest.meta() != null) {
      meta.putAll(toolRequest.meta());
    }
    meta.put("invocation_id", invocationId);
    if (mcpServerId != null) {
      meta.put("mcp_server_id", mcpServerId);
    }

    try {
      result = tool.execute(new Tool.Arguments(toolRequest.arguments(), meta));
      logSuccess(toolName, invocationTimestamp);
    } catch (Exception e) {
      errorType = resolveErrorType(e);
      result = handleExecutionError(e, toolName, invocationTimestamp);
    }

    var durationMs = System.currentTimeMillis() - invocationTimestamp;
    var successful = !result.isError();
    var callToolResult = result.toCallToolResult();
    var responseSizeBytes = computeResponseSizeBytes(callToolResult);
    backendService.notifyToolCalled("mcp_" + tool.definition().name(), successful);
    notifyAnalytics(toolName, new InvocationMetrics(invocationId, durationMs, successful, errorType, responseSizeBytes, invocationTimestamp));
    return callToolResult;
  }

  private void notifyAnalytics(String toolName, InvocationMetrics metrics) {
    var service = analyticsService;
    if (service == null) {
      return;
    }
    var httpSupplier = httpServerApiSupplier;
    if (httpSupplier != null) {
      ServerApi serverApi;
      try {
        serverApi = httpSupplier.get();
      } catch (Exception e) {
        LOG.debug("Failed to obtain ServerApi for analytics context, skipping event for tool " + toolName + ": " + e.getMessage());
        return;
      }
      // HTTP mode: resolve context asynchronously — API calls must not block the tool response.
      service.submit(() -> dispatchAnalyticsEvent(service, toolName, () -> resolveHttpContext(serverApi), metrics));
    } else if (stdioContext != null) {
      // stdio mode: context is pre-resolved at startup — read cached values, no I/O.
      var ctx = stdioContext;
      service.submit(() -> dispatchAnalyticsEvent(service, toolName, () -> ctx, metrics));
    }
  }

  private static ConnectionContext resolveHttpContext(@Nullable ServerApi serverApi) {
    var ctx = ConnectionContext.empty();
    if (serverApi != null) {
      ctx.resolveFrom(serverApi);
    }
    return ctx;
  }

  private static void dispatchAnalyticsEvent(AnalyticsService service, String toolName, Supplier<ConnectionContext> ctxSupplier, InvocationMetrics metrics) {
    try {
      var ctx = ctxSupplier.get();
      service.notifyToolInvoked(new ToolInvocationResult(
        metrics.invocationId(), toolName,
        ctx.getOrganizationUuidV4(), ctx.getSqsInstallationId(), ctx.getUserUuid(),
        ctx.getCallingAgentName(), ctx.getCallingAgentVersion(),
        metrics.durationMs(), metrics.successful(), metrics.errorType(),
        metrics.responseSizeBytes(), metrics.invocationTimestamp()
      ));
    } catch (Exception e) {
      LOG.debug("Failed to send analytics event for tool " + toolName + ": " + e.getMessage());
    }
  }

  private static long computeResponseSizeBytes(McpSchema.CallToolResult callToolResult) {
    return callToolResult.content().stream()
      .filter(c -> c instanceof McpSchema.TextContent)
      .map(c -> ((McpSchema.TextContent) c).text())
      .mapToLong(text -> text.getBytes(StandardCharsets.UTF_8).length)
      .sum();
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

  private static String resolveErrorType(Exception e) {
    return switch (e) {
      case UnauthorizedException ignored -> "unauthorized";
      case ForbiddenException ignored -> "forbidden";
      case NotFoundException ignored -> "not_found";
      case ServerInternalErrorException ignored -> "server_error";
      case ServerApiException ignored -> "server_api_error";
      case MissingRequiredArgumentException ignored -> "missing_argument";
      case IllegalArgumentException ignored -> "invalid_argument";
      case ResponseErrorException ignored -> "protocol_error";
      default -> "unknown";
    };
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
