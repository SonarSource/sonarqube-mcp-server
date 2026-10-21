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
package org.sonarsource.sonarqube.mcp.transport;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;
import org.sonarsource.sonarqube.mcp.tools.ToolDefinitionBuilder;
import reactor.core.publisher.Mono;

/**
 * Wraps the SDK-provided {@link McpStatelessServerHandler} to intercept {@code tools/list}
 * and {@code tools/call} requests and apply per-request narrowing based on the
 * {@code SONARQUBE_TOOLSETS}, {@code SONARQUBE_READ_ONLY}, and verified OAuth scopes.
 *
 * <p>The handler receives {@code allTools} — the set already filtered by the server-level
 * {@code SONARQUBE_TOOLSETS} and {@code SONARQUBE_READ_ONLY} environment variables at startup.
 * Per-request headers can only <em>narrow</em> this set further, never expand it:
 * <ul>
 *   <li>If the server started with {@code SONARQUBE_READ_ONLY=true}, write tools are absent
 *       from {@code allTools} and cannot be re-enabled per-request.</li>
 *   <li>If the server started with a restricted {@code SONARQUBE_TOOLSETS}, per-request headers
 *       can select a subset of those toolsets, but cannot add toolsets beyond what the server
 *       was launched with.</li>
 * </ul>
 *
 * <p>The SDK's built-in {@code toolsListRequestHandler} ignores the {@link McpTransportContext},
 * so it always returns all registered tools. When a per-request filter is active, this handler
 * short-circuits the delegate and directly returns only the subset the client is allowed to see,
 * without consulting the SDK handler at all.
 *
 * <p>For {@code tools/call}, if a per-request filter is active and the requested tool is not in
 * the allowed set, a {@code METHOD_NOT_FOUND} error is returned immediately without delegating
 * to the SDK handler. An otherwise eligible tool with insufficient OAuth scopes instead marks
 * the request for an HTTP 403 challenge before any entitlement check or dispatch.
 *
 * <p>Each tool can further gate its own visibility per-request by overriding
 * {@link Tool#isEnabledFor(McpTransportContext)}.
 *
 * <p>Additionally, this handler works around
 * <a href="https://github.com/modelcontextprotocol/java-sdk/issues/784">java-sdk#784</a>:
 * when the SDK receives a request for an unregistered method (e.g. {@code resources/list}),
 * it returns {@code Mono.error(McpError)} instead of a proper JSON-RPC {@code -32601} response,
 * causing HTTP 500. This handler intercepts such methods and returns the correct error response.
 */
public class PerRequestToolFilteringHandler implements McpStatelessServerHandler {

  private static final McpLogger LOG = McpLogger.getInstance();

  private static final Set<String> SUPPORTED_REQUEST_METHODS = Set.of(
    McpSchema.METHOD_INITIALIZE,
    McpSchema.METHOD_PING,
    McpSchema.METHOD_TOOLS_LIST,
    McpSchema.METHOD_TOOLS_CALL
  );

  private final McpStatelessServerHandler delegate;
  private final List<Tool> allTools;

  public PerRequestToolFilteringHandler(McpStatelessServerHandler delegate, List<Tool> allTools) {
    this.delegate = delegate;
    this.allTools = List.copyOf(allTools);
  }

  @Override
  public Mono<McpSchema.JSONRPCResponse> handleRequest(McpTransportContext transportContext, McpSchema.JSONRPCRequest request) {
    if (McpSchema.METHOD_TOOLS_LIST.equals(request.method())) {
      var filteredTools = filterTools(transportContext);
      var result = new McpSchema.ListToolsResult(filteredTools, null);
      return Mono.just(new McpSchema.JSONRPCResponse(McpSchema.JSONRPC_VERSION, request.id(), result, null));
    }
    if (McpSchema.METHOD_TOOLS_CALL.equals(request.method())) {
      return handleToolCall(transportContext, request);
    }
    // https://github.com/modelcontextprotocol/java-sdk/issues/784
    if (!SUPPORTED_REQUEST_METHODS.contains(request.method())) {
      LOG.debug("Rejecting unsupported method: " + request.method());
      var error = new McpSchema.JSONRPCResponse.JSONRPCError(McpSchema.ErrorCodes.METHOD_NOT_FOUND, "Method not found: " + request.method(), null);
      return Mono.just(new McpSchema.JSONRPCResponse(McpSchema.JSONRPC_VERSION, request.id(), null, error));
    }
    return delegate.handleRequest(transportContext, request);
  }

  @Override
  public Mono<Void> handleNotification(McpTransportContext transportContext, McpSchema.JSONRPCNotification notification) {
    return delegate.handleNotification(transportContext, notification);
  }

  private Mono<McpSchema.JSONRPCResponse> handleToolCall(McpTransportContext transportContext, McpSchema.JSONRPCRequest request) {
    var toolName = extractToolName(request);
    if (toolName == null) {
      return delegate.handleRequest(transportContext, request);
    }
    var tool = allTools.stream().filter(candidate -> toolName.equals(candidate.definition().name())).findFirst().orElse(null);
    if (tool == null || !matchesRequestFilters(tool, transportContext)) {
      return toolNotFound(request, toolName);
    }
    var authorization = (OAuthScopeContext) transportContext.get(OAuthScopeContext.CONTEXT_KEY);
    if (authorization != null && !authorization.permits(tool.requiredAccessMode())) {
      authorization.deny(tool.requiredAccessMode());
      var error = new McpSchema.JSONRPCResponse.JSONRPCError(-32001, "Insufficient OAuth scope", null);
      return Mono.just(new McpSchema.JSONRPCResponse(McpSchema.JSONRPC_VERSION, request.id(), null, error));
    }
    if (!tool.isEnabledFor(transportContext)) {
      return toolNotFound(request, toolName);
    }
    return delegate.handleRequest(transportContext, request);
  }

  private List<McpSchema.Tool> filterTools(McpTransportContext ctx) {
    var authorization = (OAuthScopeContext) ctx.get(OAuthScopeContext.CONTEXT_KEY);
    var organization = ctx.get(HttpServerTransportProvider.CONTEXT_ORG_KEY);
    var organizationFixed = organization instanceof String key && !key.isBlank();

    return allTools.stream()
      .filter(tool -> matchesRequestFilters(tool, ctx))
      .filter(tool -> authorization == null || authorization.permits(tool.requiredAccessMode()))
      .filter(tool -> tool.isEnabledFor(ctx))
      .map(Tool::definition)
      .map(definition -> organizationFixed ? ToolDefinitionBuilder.withoutOrganization(definition) : definition)
      .toList();
  }

  private static boolean matchesRequestFilters(Tool tool, McpTransportContext ctx) {
    @SuppressWarnings("unchecked")
    var allowedCategories = (Set<ToolCategory>) ctx.get(HttpServerTransportProvider.CONTEXT_TOOLSETS_KEY);
    var readOnly = Boolean.TRUE.equals(ctx.get(HttpServerTransportProvider.CONTEXT_READ_ONLY_KEY));
    var annotations = tool.definition().annotations();
    return isCategoryAllowed(tool, allowedCategories) && (!readOnly || (annotations != null && Boolean.TRUE.equals(annotations.readOnlyHint())));
  }

  private static Mono<McpSchema.JSONRPCResponse> toolNotFound(McpSchema.JSONRPCRequest request, String name) {
    var error = new McpSchema.JSONRPCResponse.JSONRPCError(McpSchema.ErrorCodes.METHOD_NOT_FOUND, "Tool not found: " + name, null);
    return Mono.just(new McpSchema.JSONRPCResponse(McpSchema.JSONRPC_VERSION, request.id(), null, error));
  }

  @Nullable
  private static String extractToolName(McpSchema.JSONRPCRequest request) {
    if (request.params() instanceof Map<?, ?> params) {
      var name = params.get("name");
      return name instanceof String s ? s : null;
    }
    return null;
  }

  private static boolean isCategoryAllowed(Tool tool, @Nullable Set<ToolCategory> allowedCategories) {
    if (tool.getCategories().contains(ToolCategory.PROJECTS)) {
      return true;
    }
    if (allowedCategories == null) {
      return true;
    }
    return tool.getCategories().stream().anyMatch(allowedCategories::contains);
  }

}
