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
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  private record InvocationMetrics(String invocationId, long durationMs, boolean successful, @Nullable String errorType, long responseSizeBytes,
    long invocationTimestamp, List<String> matchingToolsets) {}
  private final BackendService backendService;
  @Nullable
  private final AnalyticsService analyticsService;

  /**
   * Pre-resolved connection context for stdio mode. Null when running in HTTP mode.
   */
  @Nullable
  private final ConnectionContext stdioContext;

  /**
   * Supplies the invocation {@link ServerApi} before asynchronous analytics dispatch in either transport.
   */
  @Nullable
  private final Supplier<ServerApi> invocationApiSupplier;

  /**
   * MCP server ID to include in the _meta field for proxied servers to correlate telemetry.
   */
  @Nullable
  private final String mcpServerId;

  /**
   * Supplies the toolsets enabled for the current request.
   */
  private final Supplier<Set<ToolCategory>> enabledToolsetsSupplier;

  public ToolExecutor(BackendService backendService, @Nullable AnalyticsService analyticsService,
    @Nullable ConnectionContext stdioContext, @Nullable Supplier<ServerApi> invocationApiSupplier, @Nullable String mcpServerId,
    Supplier<Set<ToolCategory>> enabledToolsetsSupplier) {
    this.backendService = backendService;
    this.analyticsService = analyticsService;
    this.stdioContext = stdioContext;
    this.invocationApiSupplier = invocationApiSupplier;
    this.mcpServerId = mcpServerId;
    this.enabledToolsetsSupplier = enabledToolsetsSupplier;
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

    var arguments = new Tool.Arguments(toolRequest.arguments() != null ? toolRequest.arguments() : Map.of(), meta);
    try {
      result = tool.execute(arguments);
      logSuccess(toolName, invocationTimestamp);
    } catch (Exception e) {
      errorType = resolveErrorType(e);
      result = handleExecutionError(e, toolName, invocationTimestamp);
    }

    var durationMs = System.currentTimeMillis() - invocationTimestamp;
    var successful = !result.isError();
    var callToolResult = result.toCallToolResult();
    var responseSizeBytes = computeResponseSizeBytes(callToolResult);
    var enabledToolsets = enabledToolsetsSupplier.get();
    var matchingToolsets = tool.getCategories().stream()
      .filter(enabledToolsets::contains)
      .map(ToolCategory::getKey)
      .sorted()
      .toList();
    backendService.notifyToolCalled("mcp_" + tool.definition().name(), successful);
    notifyAnalytics(toolName, new InvocationMetrics(invocationId, durationMs, successful, errorType, responseSizeBytes, invocationTimestamp, matchingToolsets),
      arguments);
    return callToolResult;
  }

  private void notifyAnalytics(String toolName, InvocationMetrics metrics, Tool.Arguments arguments) {
    var service = analyticsService;
    if (service == null) {
      return;
    }
    try {
      submitAnalytics(service, toolName, metrics, arguments);
    } catch (RuntimeException e) {
      LOG.debug("Unable to schedule tool analytics (" + e.getClass().getSimpleName() + ")");
    }
  }

  private void submitAnalytics(AnalyticsService service, String toolName, InvocationMetrics metrics, Tool.Arguments arguments) {
    var organization = arguments.getOptionalString(ToolParameters.ORGANIZATION);
    var context = stdioContext;
    var captured = context != null ? context.snapshot() : null;
    var apiSupplier = invocationApiSupplier;
    if (apiSupplier != null) {
      ServerApi serverApi;
      try {
        var api = apiSupplier.get();
        serverApi = organization == null ? api : api.withOrganization(organization);
      } catch (Exception e) {
        LOG.debug("Unable to enrich tool analytics (" + e.getClass().getSimpleName() + ")");
        service.submit(() -> dispatchAnalyticsEvent(service, toolName, () -> fallbackSnapshot(captured), metrics));
        return;
      }
      // Capture the invocation API and calling agent before leaving the request thread; keep lookups asynchronous.
      service.submit(() -> dispatchAnalyticsEvent(service, toolName,
        () -> context != null ? resolveStdioSnapshot(context, serverApi, captured) : resolveInvocationSnapshot(serverApi), metrics));
    } else if (captured != null) {
      service.submit(() -> dispatchAnalyticsEvent(service, toolName, () -> captured, metrics));
    }
  }

  private static ConnectionContext.Snapshot fallbackSnapshot(@Nullable ConnectionContext.Snapshot captured) {
    return captured == null ? ConnectionContext.empty().snapshot()
      : new ConnectionContext.Snapshot(null, captured.sqsInstallationId(), captured.userUuid(), captured.callingAgentName(), captured.callingAgentVersion());
  }

  private static ConnectionContext.Snapshot resolveStdioSnapshot(ConnectionContext context, ServerApi serverApi, ConnectionContext.Snapshot captured) {
    try {
      return context.snapshotFor(serverApi, captured);
    } catch (RuntimeException e) {
      LOG.debug("Unable to enrich tool analytics (" + e.getClass().getSimpleName() + ")");
      return fallbackSnapshot(captured);
    }
  }

  private static ConnectionContext.Snapshot resolveInvocationSnapshot(@Nullable ServerApi serverApi) {
    var ctx = ConnectionContext.empty();
    if (serverApi != null) {
      try {
        ctx.resolveFrom(serverApi);
      } catch (RuntimeException e) {
        LOG.debug("Unable to enrich tool analytics (" + e.getClass().getSimpleName() + ")");
      }
    }
    return ctx.snapshot();
  }

  private static void dispatchAnalyticsEvent(AnalyticsService service, String toolName, Supplier<ConnectionContext.Snapshot> ctxSupplier, InvocationMetrics metrics) {
    try {
      ConnectionContext.Snapshot ctx;
      try {
        ctx = ctxSupplier.get();
      } catch (RuntimeException e) {
        ctx = ConnectionContext.empty().snapshot();
        LOG.debug("Unable to enrich tool analytics (" + e.getClass().getSimpleName() + ")");
      }
      service.notifyToolInvoked(new ToolInvocationResult(
        metrics.invocationId(), toolName,
        ctx.organizationUuidV4(), ctx.sqsInstallationId(), ctx.userUuid(),
        ctx.callingAgentName(), ctx.callingAgentVersion(),
        metrics.durationMs(), metrics.successful(), metrics.errorType(),
        metrics.responseSizeBytes(), metrics.invocationTimestamp(), metrics.matchingToolsets()
      ));
    } catch (Exception e) {
      LOG.debug("Unable to send tool analytics (" + e.getClass().getSimpleName() + ")");
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
