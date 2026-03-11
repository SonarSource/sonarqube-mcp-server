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
import java.util.Set;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.a3s.A3sConfigApi;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.OrganizationsApi;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;
import org.sonarsource.sonarqube.mcp.tools.analysis.RunAdvancedCodeAnalysisTool;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PerRequestToolFilteringHandlerTest {

  private static final String REQUEST_ID = "1";

  @Test
  void tools_list_always_returns_filtered_list() {
    var issuesTool = mockTool("search_issues", ToolCategory.ISSUES, true);
    var handler = new PerRequestToolFilteringHandler(mock(McpStatelessServerHandler.class), List.of(issuesTool), null);
    var context = contextWithToken();

    var response = handler.handleRequest(context, toolsListRequest()).block();

    assertThat(response).isNotNull();
    assertThat(((McpSchema.ListToolsResult) response.result()).tools()).hasSize(1);
  }

  @Test
  void tools_list_with_toolsets_header_returns_only_matching_tools() {
    var issuesTool = mockTool("search_issues", ToolCategory.ISSUES, true);
    var hotspotsTool = mockTool("search_hotspots", ToolCategory.SECURITY_HOTSPOTS, true);
    var projectsTool = mockTool("search_projects", ToolCategory.PROJECTS, true);

    var handler = new PerRequestToolFilteringHandler(mock(McpStatelessServerHandler.class), List.of(issuesTool, hotspotsTool, projectsTool), null);
    var context = contextWith(Map.of(
      HttpServerTransportProvider.CONTEXT_TOKEN_KEY, "token",
      HttpServerTransportProvider.CONTEXT_TOOLSETS_KEY, Set.of(ToolCategory.ISSUES)
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

    var handler = new PerRequestToolFilteringHandler(mock(McpStatelessServerHandler.class), List.of(readOnlyTool, writeTool), null);
    var context = contextWith(Map.of(
      HttpServerTransportProvider.CONTEXT_TOKEN_KEY, "token",
      HttpServerTransportProvider.CONTEXT_READ_ONLY_KEY, true
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

    var handler = new PerRequestToolFilteringHandler(mock(McpStatelessServerHandler.class),
      List.of(issuesReadOnly, issuesWrite, hotspotsTool), null);
    var context = contextWith(Map.of(
      HttpServerTransportProvider.CONTEXT_TOKEN_KEY, "token",
      HttpServerTransportProvider.CONTEXT_TOOLSETS_KEY, Set.of(ToolCategory.ISSUES),
      HttpServerTransportProvider.CONTEXT_READ_ONLY_KEY, true
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

    var handler = new PerRequestToolFilteringHandler(mock(McpStatelessServerHandler.class), List.of(projectsTool, issuesTool), null);
    var context = contextWith(Map.of(
      HttpServerTransportProvider.CONTEXT_TOKEN_KEY, "token",
      HttpServerTransportProvider.CONTEXT_TOOLSETS_KEY, Set.of(ToolCategory.MEASURES) // neither issues nor projects
    ));

    var result = (McpSchema.ListToolsResult) handler.handleRequest(context, toolsListRequest()).block().result();

    assertThat(result.tools())
      .extracting(McpSchema.Tool::name)
      .containsExactly("search_projects"); // only PROJECTS survives
  }

  @Test
  void tools_call_allowed_tool_delegates_to_sdk_handler() {
    var delegate = mock(McpStatelessServerHandler.class);
    when(delegate.handleRequest(any(), any())).thenReturn(Mono.just(
      new McpSchema.JSONRPCResponse(McpSchema.JSONRPC_VERSION, REQUEST_ID, Map.of(), null)));

    var issuesTool = mockTool("search_issues", ToolCategory.ISSUES, true);
    var handler = new PerRequestToolFilteringHandler(delegate, List.of(issuesTool), null);
    var context = contextWith(Map.of(
      HttpServerTransportProvider.CONTEXT_TOKEN_KEY, "token",
      HttpServerTransportProvider.CONTEXT_TOOLSETS_KEY, Set.of(ToolCategory.ISSUES)
    ));
    var callRequest = toolsCallRequest("search_issues");

    handler.handleRequest(context, callRequest).block();

    verify(delegate).handleRequest(context, callRequest);
  }

  @Test
  void tools_call_disallowed_tool_returns_method_not_found_error() {
    var issuesTool = mockTool("search_issues", ToolCategory.ISSUES, true);
    var hotspotsTool = mockTool("search_hotspots", ToolCategory.SECURITY_HOTSPOTS, true);

    var handler = new PerRequestToolFilteringHandler(mock(McpStatelessServerHandler.class), List.of(issuesTool, hotspotsTool), null);
    var context = contextWith(Map.of(
      HttpServerTransportProvider.CONTEXT_TOKEN_KEY, "token",
      HttpServerTransportProvider.CONTEXT_TOOLSETS_KEY, Set.of(ToolCategory.ISSUES)
    ));

    var response = handler.handleRequest(context, toolsCallRequest("search_hotspots")).block();

    assertThat(response).isNotNull();
    assertThat(response.error()).isNotNull();
    assertThat(response.error().code()).isEqualTo(McpSchema.ErrorCodes.METHOD_NOT_FOUND);
    assertThat(response.error().message()).contains("search_hotspots");
    assertThat(response.result()).isNull();
  }

  @Test
  void tools_call_disallowed_write_tool_in_read_only_mode_returns_method_not_found_error() {
    var writeTool = mockTool("change_status", ToolCategory.ISSUES, false);

    var handler = new PerRequestToolFilteringHandler(mock(McpStatelessServerHandler.class), List.of(writeTool), null);
    var context = contextWith(Map.of(
      HttpServerTransportProvider.CONTEXT_TOKEN_KEY, "token",
      HttpServerTransportProvider.CONTEXT_READ_ONLY_KEY, true
    ));

    var response = handler.handleRequest(context, toolsCallRequest("change_status")).block();

    assertThat(response).isNotNull();
    assertThat(response.error()).isNotNull();
    assertThat(response.error().code()).isEqualTo(McpSchema.ErrorCodes.METHOD_NOT_FOUND);
    assertThat(response.error().message()).contains("change_status");
  }

  @Test
  void tools_call_without_filter_headers_delegates_to_sdk_handler() {
    var delegate = mock(McpStatelessServerHandler.class);
    when(delegate.handleRequest(any(), any())).thenReturn(Mono.just(
      new McpSchema.JSONRPCResponse(McpSchema.JSONRPC_VERSION, REQUEST_ID, Map.of(), null)));

    var hotspotsTool = mockTool("search_hotspots", ToolCategory.SECURITY_HOTSPOTS, true);
    var handler = new PerRequestToolFilteringHandler(delegate, List.of(hotspotsTool), null);
    var context = contextWithToken();
    var callRequest = toolsCallRequest("search_hotspots");

    handler.handleRequest(context, callRequest).block();

    verify(delegate).handleRequest(context, callRequest);
  }

  @Test
  void non_tools_list_requests_always_delegate_to_sdk_handler() {
    var delegate = mock(McpStatelessServerHandler.class);
    when(delegate.handleRequest(any(), any())).thenReturn(Mono.just(
      new McpSchema.JSONRPCResponse(McpSchema.JSONRPC_VERSION, REQUEST_ID, Map.of(), null)));

    var handler = new PerRequestToolFilteringHandler(delegate, List.of(), null);
    var context = contextWith(Map.of(
      HttpServerTransportProvider.CONTEXT_TOKEN_KEY, "token",
      HttpServerTransportProvider.CONTEXT_TOOLSETS_KEY, Set.of(ToolCategory.ISSUES)
    ));
    var initRequest = new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION, McpSchema.METHOD_INITIALIZE, REQUEST_ID, Map.of());

    handler.handleRequest(context, initRequest).block();

    verify(delegate).handleRequest(context, initRequest);
  }

  @Test
  void notifications_always_delegate_to_sdk_handler() {
    var delegate = mock(McpStatelessServerHandler.class);
    when(delegate.handleNotification(any(), any())).thenReturn(Mono.empty());

    var handler = new PerRequestToolFilteringHandler(delegate, List.of(), null);
    var context = contextWithToken();
    var notification = new McpSchema.JSONRPCNotification(McpSchema.JSONRPC_VERSION, "notifications/initialized", null);

    handler.handleNotification(context, notification).block();

    verify(delegate).handleNotification(context, notification);
  }

  @Test
  void advanced_analysis_tool_hidden_when_token_or_org_missing() {
    var a3sTool = mockAdvancedAnalysisTool();
    BiFunction<String, String, ServerApi> factory = (token, org) -> mock(ServerApi.class);
    var handler = new PerRequestToolFilteringHandler(mock(McpStatelessServerHandler.class), List.of(a3sTool), factory);
    var context = contextWith(Map.of(HttpServerTransportProvider.CONTEXT_TOKEN_KEY, "token"));

    var result = (McpSchema.ListToolsResult) handler.handleRequest(context, toolsListRequest()).block().result();

    assertThat(result.tools()).isEmpty();
  }

  @Test
  void advanced_analysis_tool_visible_when_factory_returns_enabled() {
    var a3sTool = mockAdvancedAnalysisTool();
    var serverApi = mock(ServerApi.class);
    var orgApi = mock(OrganizationsApi.class);
    var a3sConfigApi = mock(A3sConfigApi.class);
    when(serverApi.organizationsApi()).thenReturn(orgApi);
    when(serverApi.a3sConfigApi()).thenReturn(a3sConfigApi);
    when(orgApi.getOrganizationUuidV4("my-org")).thenReturn("uuid-v4");
    when(a3sConfigApi.getOrgConfig("uuid-v4")).thenReturn(new A3sConfigApi.OrgConfigResponse("uuid-v4", true, true));

    BiFunction<String, String, ServerApi> factory = (token, org) -> serverApi;
    var handler = new PerRequestToolFilteringHandler(mock(McpStatelessServerHandler.class), List.of(a3sTool), factory);
    var context = contextWith(Map.of(
      HttpServerTransportProvider.CONTEXT_TOKEN_KEY, "token",
      HttpServerTransportProvider.CONTEXT_ORG_KEY, "my-org"
    ));

    var result = (McpSchema.ListToolsResult) handler.handleRequest(context, toolsListRequest()).block().result();

    assertThat(result.tools()).extracting(McpSchema.Tool::name).containsExactly(RunAdvancedCodeAnalysisTool.TOOL_NAME);
  }

  @Test
  void advanced_analysis_tool_hidden_when_org_uuid_is_null() {
    var a3sTool = mockAdvancedAnalysisTool();
    var serverApi = mock(ServerApi.class);
    var orgApi = mock(OrganizationsApi.class);
    when(serverApi.organizationsApi()).thenReturn(orgApi);
    when(orgApi.getOrganizationUuidV4("my-org")).thenReturn(null);

    BiFunction<String, String, ServerApi> factory = (token, org) -> serverApi;
    var handler = new PerRequestToolFilteringHandler(mock(McpStatelessServerHandler.class), List.of(a3sTool), factory);
    var context = contextWith(Map.of(
      HttpServerTransportProvider.CONTEXT_TOKEN_KEY, "token",
      HttpServerTransportProvider.CONTEXT_ORG_KEY, "my-org"
    ));

    var result = (McpSchema.ListToolsResult) handler.handleRequest(context, toolsListRequest()).block().result();

    assertThat(result.tools()).isEmpty();
  }

  @Test
  void tools_call_advanced_analysis_tool_blocked_when_not_enabled() {
    var a3sTool = mockAdvancedAnalysisTool();
    var handler = new PerRequestToolFilteringHandler(mock(McpStatelessServerHandler.class), List.of(a3sTool), null);
    var context = contextWith(Map.of(
      HttpServerTransportProvider.CONTEXT_TOKEN_KEY, "token",
      HttpServerTransportProvider.CONTEXT_ORG_KEY, "my-org"
    ));

    var response = handler.handleRequest(context, toolsCallRequest(RunAdvancedCodeAnalysisTool.TOOL_NAME)).block();

    assertThat(response).isNotNull();
    assertThat(response.error()).isNotNull();
    assertThat(response.error().code()).isEqualTo(McpSchema.ErrorCodes.METHOD_NOT_FOUND);
  }

  @Test
  void tools_call_with_non_map_params_delegates_to_sdk_handler() {
    var delegate = mock(McpStatelessServerHandler.class);
    when(delegate.handleRequest(any(), any())).thenReturn(Mono.just(
      new McpSchema.JSONRPCResponse(McpSchema.JSONRPC_VERSION, REQUEST_ID, Map.of(), null)));

    var handler = new PerRequestToolFilteringHandler(delegate, List.of(), null);
    var request = new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION, McpSchema.METHOD_TOOLS_CALL, REQUEST_ID, "not-a-map");

    handler.handleRequest(contextWithToken(), request).block();

    verify(delegate).handleRequest(contextWithToken(), request);
  }

  private static RunAdvancedCodeAnalysisTool mockAdvancedAnalysisTool() {
    var annotations = new McpSchema.ToolAnnotations(null, true, null, null, null, null);
    var toolDef = new McpSchema.Tool(RunAdvancedCodeAnalysisTool.TOOL_NAME, null, "Advanced analysis", null, null, annotations, null);
    var tool = mock(RunAdvancedCodeAnalysisTool.class);
    when(tool.definition()).thenReturn(toolDef);
    when(tool.getCategory()).thenReturn(ToolCategory.ANALYSIS);
    return tool;
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

  private static McpSchema.JSONRPCRequest toolsCallRequest(String toolName) {
    return new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION, McpSchema.METHOD_TOOLS_CALL, REQUEST_ID, Map.of("name", toolName));
  }

  private static McpTransportContext contextWithToken() {
    return contextWith(Map.of(HttpServerTransportProvider.CONTEXT_TOKEN_KEY, "token"));
  }

  private static McpTransportContext contextWith(Map<String, Object> entries) {
    return McpTransportContext.create(entries);
  }
}
