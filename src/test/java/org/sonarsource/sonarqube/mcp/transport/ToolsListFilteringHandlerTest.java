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
package org.sonarsource.sonarqube.mcp.transport;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolsListFilteringHandlerTest {

  private static final String REQUEST_ID = "1";

  @Test
  void tools_list_without_filter_headers_delegates_to_sdk_handler() {
    var delegate = mockDelegateReturningEmptyList();
    var handler = new ToolsListFilteringHandler(delegate, List.of());
    var context = contextWithToken();

    handler.handleRequest(context, toolsListRequest()).block();

    verify(delegate).handleRequest(context, toolsListRequest());
  }

  @Test
  void tools_list_with_toolsets_header_returns_only_matching_tools() {
    var issuesTool = mockTool("search_issues", ToolCategory.ISSUES, true);
    var hotspotsTool = mockTool("search_hotspots", ToolCategory.SECURITY_HOTSPOTS, true);
    var projectsTool = mockTool("search_projects", ToolCategory.PROJECTS, true);

    var handler = new ToolsListFilteringHandler(mock(McpStatelessServerHandler.class), List.of(issuesTool, hotspotsTool, projectsTool));
    var context = contextWith(Map.of(
      HttpServerTransportProvider.CONTEXT_TOKEN_KEY, "token",
      HttpServerTransportProvider.CONTEXT_TOOLSETS_KEY, ToolCategory.ISSUES.getKey()
    ));

    var result = (McpSchema.ListToolsResult) handler.handleRequest(context, toolsListRequest()).block().result();

    assertThat(result.tools())
      .extracting(McpSchema.Tool::name)
      .containsExactlyInAnyOrder("search_issues", "search_projects"); // PROJECTS always included
  }

  @Test
  void tools_list_with_read_only_header_returns_only_read_only_tools() {
    var readOnlyTool = mockTool("search_issues", ToolCategory.ISSUES, true);
    var writeTool = mockTool("change_status", ToolCategory.ISSUES, false);

    var handler = new ToolsListFilteringHandler(mock(McpStatelessServerHandler.class), List.of(readOnlyTool, writeTool));
    var context = contextWith(Map.of(
      HttpServerTransportProvider.CONTEXT_TOKEN_KEY, "token",
      HttpServerTransportProvider.CONTEXT_READ_ONLY_KEY, "true"
    ));

    var result = (McpSchema.ListToolsResult) handler.handleRequest(context, toolsListRequest()).block().result();

    assertThat(result.tools())
      .extracting(McpSchema.Tool::name)
      .containsExactly("search_issues");
  }

  @Test
  void tools_list_with_toolsets_and_read_only_applies_both_filters() {
    var issuesReadOnly = mockTool("search_issues", ToolCategory.ISSUES, true);
    var issuesWrite = mockTool("change_status", ToolCategory.ISSUES, false);
    var hotspotsTool = mockTool("search_hotspots", ToolCategory.SECURITY_HOTSPOTS, true);

    var handler = new ToolsListFilteringHandler(mock(McpStatelessServerHandler.class),
      List.of(issuesReadOnly, issuesWrite, hotspotsTool));
    var context = contextWith(Map.of(
      HttpServerTransportProvider.CONTEXT_TOKEN_KEY, "token",
      HttpServerTransportProvider.CONTEXT_TOOLSETS_KEY, ToolCategory.ISSUES.getKey(),
      HttpServerTransportProvider.CONTEXT_READ_ONLY_KEY, "true"
    ));

    var result = (McpSchema.ListToolsResult) handler.handleRequest(context, toolsListRequest()).block().result();

    assertThat(result.tools())
      .extracting(McpSchema.Tool::name)
      .containsExactly("search_issues");
  }

  @Test
  void tools_list_projects_category_always_included_regardless_of_toolsets() {
    var projectsTool = mockTool("search_projects", ToolCategory.PROJECTS, true);
    var issuesTool = mockTool("search_issues", ToolCategory.ISSUES, true);

    var handler = new ToolsListFilteringHandler(mock(McpStatelessServerHandler.class), List.of(projectsTool, issuesTool));
    var context = contextWith(Map.of(
      HttpServerTransportProvider.CONTEXT_TOKEN_KEY, "token",
      HttpServerTransportProvider.CONTEXT_TOOLSETS_KEY, "measures" // neither issues nor projects
    ));

    var result = (McpSchema.ListToolsResult) handler.handleRequest(context, toolsListRequest()).block().result();

    assertThat(result.tools())
      .extracting(McpSchema.Tool::name)
      .containsExactly("search_projects"); // only PROJECTS survives
  }

  @Test
  void non_tools_list_requests_always_delegate_to_sdk_handler() {
    var delegate = mock(McpStatelessServerHandler.class);
    when(delegate.handleRequest(any(), any())).thenReturn(Mono.just(
      new McpSchema.JSONRPCResponse(McpSchema.JSONRPC_VERSION, REQUEST_ID, Map.of(), null)));

    var handler = new ToolsListFilteringHandler(delegate, List.of());
    var context = contextWith(Map.of(
      HttpServerTransportProvider.CONTEXT_TOKEN_KEY, "token",
      HttpServerTransportProvider.CONTEXT_TOOLSETS_KEY, ToolCategory.ISSUES.getKey()
    ));
    var initRequest = new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION, McpSchema.METHOD_INITIALIZE, REQUEST_ID, Map.of());

    handler.handleRequest(context, initRequest).block();

    verify(delegate).handleRequest(context, initRequest);
  }

  @Test
  void notifications_always_delegate_to_sdk_handler() {
    var delegate = mock(McpStatelessServerHandler.class);
    when(delegate.handleNotification(any(), any())).thenReturn(Mono.empty());

    var handler = new ToolsListFilteringHandler(delegate, List.of());
    var context = contextWithToken();
    var notification = new McpSchema.JSONRPCNotification(McpSchema.JSONRPC_VERSION, "notifications/initialized", null);

    handler.handleNotification(context, notification).block();

    verify(delegate).handleNotification(context, notification);
  }

  private static McpStatelessServerHandler mockDelegateReturningEmptyList() {
    var delegate = mock(McpStatelessServerHandler.class);
    when(delegate.handleRequest(any(), any())).thenReturn(Mono.just(
      new McpSchema.JSONRPCResponse(McpSchema.JSONRPC_VERSION, REQUEST_ID,
        new McpSchema.ListToolsResult(List.of(), null), null)));
    return delegate;
  }

  private static Tool mockTool(String name, ToolCategory category, boolean readOnly) {
    var annotations = new McpSchema.ToolAnnotations(null, readOnly, null, null, null, null);
    var toolDef = new McpSchema.Tool(name, null, name + " description", null, null, annotations, null);
    var tool = mock(Tool.class);
    when(tool.definition()).thenReturn(toolDef);
    when(tool.getCategory()).thenReturn(category);
    return tool;
  }

  private static McpSchema.JSONRPCRequest toolsListRequest() {
    return new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION, McpSchema.METHOD_TOOLS_LIST, REQUEST_ID, null);
  }

  private static McpTransportContext contextWithToken() {
    return contextWith(Map.of(HttpServerTransportProvider.CONTEXT_TOKEN_KEY, "token"));
  }

  private static McpTransportContext contextWith(Map<String, Object> entries) {
    return McpTransportContext.create(entries);
  }
}
