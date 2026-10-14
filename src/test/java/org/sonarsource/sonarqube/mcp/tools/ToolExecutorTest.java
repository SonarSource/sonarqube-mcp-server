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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarqube.mcp.analytics.AnalyticsService;
import org.sonarsource.sonarqube.mcp.analytics.ConnectionContext;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ForbiddenException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.NotFoundException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ServerApiException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ServerInternalErrorException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.UnauthorizedException;
import org.sonarsource.sonarqube.mcp.analytics.ToolInvocationResult;
import org.sonarsource.sonarqube.mcp.client.McpClientManager;
import org.sonarsource.sonarqube.mcp.slcore.BackendService;
import org.sonarsource.sonarqube.mcp.tools.exception.MissingRequiredArgumentException;
import org.sonarsource.sonarqube.mcp.tools.organizations.ListOrganizationsTool;
import org.sonarsource.sonarqube.mcp.tools.proxied.ProxiedMcpTool;
import org.sonarsource.sonarqube.mcp.transport.McpJsonMappers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ToolExecutorTest {

  private static final Map<String, Object> EMPTY_INPUT_SCHEMA = Map.of(
    "type", "object",
    "properties", Map.of(),
    "required", List.of(),
    "additionalProperties", false);

  private BackendService mockBackendService;
  private ToolExecutor toolExecutor;

  @BeforeEach
  void prepare() {
    mockBackendService = mock(BackendService.class);
    toolExecutor = new ToolExecutor(mockBackendService, null, null, null, null, ToolCategory::all);
  }

  @Test
  void it_should_execute_argument_free_tools_when_arguments_are_omitted_and_analytics_is_disabled() {
    var api = mock(ServerApi.class, RETURNS_DEEP_STUBS);
    when(api.organizationsApi().listOrganizations()).thenReturn(List.of());
    var request = McpSchema.CallToolRequest.builder(ListOrganizationsTool.TOOL_NAME).build();
    assertThat(request.arguments()).isNull();

    var result = toolExecutor.execute(new ListOrganizationsTool(() -> api), request);

    assertThat(result.isError()).isFalse();
    assertThat(result.content().toString()).contains("organizations");
    verify(mockBackendService).notifyToolCalled("mcp_list_organizations", true);
  }

  @Test
  void it_should_register_telemetry_after_the_tool_call_succeeds() {
    record TestResponse(String message) {}
    
    toolExecutor.execute(new Tool(McpSchema.Tool.builder("tool_name", EMPTY_INPUT_SCHEMA).title("test description").description("").build(), ToolCategory.ANALYSIS) {
      @Override
      public Result execute(Arguments arguments) {
        return Result.success(new TestResponse("Success!"));
      }
    }, McpSchema.CallToolRequest.builder("tool_name").arguments(Map.of()).build());

    verify(mockBackendService).notifyToolCalled("mcp_tool_name", true);
  }

  @Test
  void it_should_register_telemetry_after_the_tool_call_fails() {
    toolExecutor.execute(new Tool(McpSchema.Tool.builder("tool_name", EMPTY_INPUT_SCHEMA).title("test description").description("").build(), ToolCategory.ANALYSIS) {
      @Override
      public Result execute(Arguments arguments) {
        return Result.failure("Failure!");
      }
    }, McpSchema.CallToolRequest.builder("tool_name").arguments(Map.of()).build());

    verify(mockBackendService).notifyToolCalled("mcp_tool_name", false);
  }

  static Stream<Arguments> proxied_tool_results() {
    var content = "\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]";
    return Stream.of(
      Arguments.of("{" + content + "}", null, false),
      Arguments.of("{" + content + ",\"isError\":false}", false, false),
      Arguments.of("{" + content + ",\"isError\":true}", true, true));
  }

  @ParameterizedTest
  @MethodSource("proxied_tool_results")
  void it_should_execute_deserialized_proxied_result_without_changing_content_or_classification(
    String responseJson, Boolean responseIsError, boolean expectedError) throws Exception {
    var proxiedResult = McpJsonMappers.DEFAULT.readValue(responseJson, McpSchema.CallToolResult.class);
    assertThat(proxiedResult.isError()).isEqualTo(responseIsError);
    var clientManager = mock(McpClientManager.class);
    when(clientManager.executeTool(any(), any(), any(), any())).thenReturn(proxiedResult);
    var toolDefinition = McpSchema.Tool.builder("proxied_tool", EMPTY_INPUT_SCHEMA).build();
    var tool = new ProxiedMcpTool("server", "proxied_tool", toolDefinition, clientManager);
    var analyticsService = syncAnalyticsService();
    var executor = new ToolExecutor(mockBackendService, analyticsService, ConnectionContext.empty(), null, null, ToolCategory::all);

    var result = executor.execute(tool, McpSchema.CallToolRequest.builder("proxied_tool").arguments(Map.of()).build());

    assertThat(result.isError()).isEqualTo(responseIsError == null ? null : expectedError);
    assertThat(((McpSchema.TextContent) result.content().getFirst()).text()).isEqualTo("ok");
    verify(mockBackendService).notifyToolCalled("mcp_proxied_tool", !expectedError);
    var resultCaptor = ArgumentCaptor.forClass(ToolInvocationResult.class);
    verify(analyticsService).notifyToolInvoked(resultCaptor.capture());
    assertThat(resultCaptor.getValue().isSuccessful()).isEqualTo(!expectedError);
  }

  @Test
  void it_should_handle_unauthorized_exception() {
    var callToolResult = toolExecutor.execute(new Tool(McpSchema.Tool.builder("tool_name", EMPTY_INPUT_SCHEMA).title("test description").description("").build(), ToolCategory.ANALYSIS) {
      @Override
      public Result execute(Arguments arguments) {
        throw new UnauthorizedException("Not authorized");
      }
    }, McpSchema.CallToolRequest.builder("tool_name").arguments(Map.of()).build());

    assertThat(callToolResult.isError()).isTrue();
    assertThat(callToolResult.content().toString()).contains("An error occurred during the tool execution: " +
      "SonarQube answered with Not authorized. Please verify your token is valid and has the correct permissions.");
    verify(mockBackendService).notifyToolCalled("mcp_tool_name", false);
  }

  @Test
  void it_should_handle_forbidden_exception() {
    var callToolResult = toolExecutor.execute(new Tool(McpSchema.Tool.builder("tool_name", EMPTY_INPUT_SCHEMA).title("test description").description("").build(), ToolCategory.ANALYSIS) {
      @Override
      public Result execute(Arguments arguments) {
        throw new ForbiddenException("Forbidden");
      }
    }, McpSchema.CallToolRequest.builder("tool_name").arguments(Map.of()).build());

    assertThat(callToolResult.isError()).isTrue();
    assertThat(callToolResult.content().toString()).contains("An error occurred during the tool execution: " +
      "SonarQube answered with Forbidden. Please verify your token has the required permissions for this operation.");
    verify(mockBackendService).notifyToolCalled("mcp_tool_name", false);
  }

  @Test
  void it_should_handle_not_found_exception() {
    var callToolResult = toolExecutor.execute(new Tool(McpSchema.Tool.builder("tool_name", EMPTY_INPUT_SCHEMA).title("test description").description("").build(), ToolCategory.ANALYSIS) {
      @Override
      public Result execute(Arguments arguments) {
        throw new NotFoundException("Resource not found");
      }
    }, McpSchema.CallToolRequest.builder("tool_name").arguments(Map.of()).build());

    assertThat(callToolResult.isError()).isTrue();
    assertThat(callToolResult.content().toString()).contains("An error occurred during the tool execution: " +
      "SonarQube answered with Resource not found. Please verify your token is valid and the requested resource exists.");
    verify(mockBackendService).notifyToolCalled("mcp_tool_name", false);
  }

  @Test
  void it_should_handle_generic_exception() {
    var callToolResult = toolExecutor.execute(new Tool(McpSchema.Tool.builder("tool_name", EMPTY_INPUT_SCHEMA).title("test description").description("").build(), ToolCategory.ANALYSIS) {
      @Override
      public Result execute(Arguments arguments) {
        throw new RuntimeException("Unexpected error");
      }
    }, McpSchema.CallToolRequest.builder("tool_name").arguments(Map.of()).build());

    assertThat(callToolResult.isError()).isTrue();
    assertThat(callToolResult.content().toString()).contains("An error occurred during the tool execution: Unexpected error");
    verify(mockBackendService).notifyToolCalled("mcp_tool_name", false);
  }

  static Stream<Arguments> error_type_mappings() {
    return Stream.of(
      Arguments.of(new UnauthorizedException("msg"), "unauthorized"),
      Arguments.of(new ForbiddenException("msg"), "forbidden"),
      Arguments.of(new NotFoundException("msg"), "not_found"),
      Arguments.of(new ServerInternalErrorException("msg"), "server_error"),
      Arguments.of(new ServerApiException("msg"), "server_api_error"),
      Arguments.of(new MissingRequiredArgumentException("param"), "missing_argument"),
      Arguments.of(new IllegalArgumentException("bad arg"), "invalid_argument"),
      Arguments.of(new ResponseErrorException(new ResponseError(ResponseErrorCode.InternalError, "rpc error", null)), "protocol_error"),
      Arguments.of(new RuntimeException("boom"), "unknown")
    );
  }

  @ParameterizedTest
  @MethodSource("error_type_mappings")
  void it_should_map_exception_to_error_type(RuntimeException exception, String expectedErrorType) {
    var analyticsService = mock(AnalyticsService.class);
    doAnswer(invocation -> { ((Runnable) invocation.getArgument(0)).run(); return null; })
      .when(analyticsService).submit(any(Runnable.class));
    var executor = new ToolExecutor(mockBackendService, analyticsService, ConnectionContext.empty(), null, null, ToolCategory::all);
    var resultCaptor = ArgumentCaptor.forClass(ToolInvocationResult.class);

    executor.execute(new Tool(McpSchema.Tool.builder("tool_name", EMPTY_INPUT_SCHEMA).title("test description").description("").build(), ToolCategory.ANALYSIS) {
      @Override
      public Result execute(Arguments arguments) {
        throw exception;
      }
    }, McpSchema.CallToolRequest.builder("tool_name").arguments(Map.of()).build());

    verify(analyticsService, timeout(2000)).notifyToolInvoked(resultCaptor.capture());
    assertThat(resultCaptor.getValue().errorType()).isEqualTo(expectedErrorType);
  }

  @Test
  void it_should_skip_analytics_when_service_is_null() {
    var executor = new ToolExecutor(mockBackendService, null, ConnectionContext.empty(), null, null, ToolCategory::all);

    var result = executeDummyTool(executor);

    assertThat(result.isError()).isFalse();
  }

  @Test
  void it_should_not_resolve_identity_when_analytics_is_disabled() {
    var api = mock(ServerApi.class);
    var executor = new ToolExecutor(mockBackendService, null, ConnectionContext.empty(), () -> api, null, ToolCategory::all);

    assertThat(executeDummyTool(executor, Map.of("organization", "selected-org")).isError()).isFalse();

    verifyNoInteractions(api);
  }

  @Test
  void it_should_dispatch_event_in_stdio_mode_with_pre_resolved_context() {
    var analyticsService = syncAnalyticsService();
    var ctx = ConnectionContext.empty();
    ctx.captureCallingAgent("cursor", "1.0.0");
    var executor = new ToolExecutor(mockBackendService, analyticsService, ctx, null, null, ToolCategory::all);

    executeDummyTool(executor);

    verify(analyticsService).notifyToolInvoked(any(ToolInvocationResult.class));
  }

  @Test
  void it_should_report_the_intersection_of_enabled_toolsets_and_tool_categories() {
    var analyticsService = syncAnalyticsService();
    var executor = new ToolExecutor(mockBackendService, analyticsService, ConnectionContext.empty(), null, null,
      () -> Set.of(ToolCategory.VORTEX, ToolCategory.ISSUES, ToolCategory.ANALYSIS));
    record TestResponse(String value) {}
    var tool = new Tool(McpSchema.Tool.builder("tool_name", EMPTY_INPUT_SCHEMA).title("test description").description("").build(),
      ToolCategory.ANALYSIS, ToolCategory.VORTEX) {
      @Override
      public Result execute(Arguments arguments) {
        return Result.success(new TestResponse("ok"));
      }
    };

    executor.execute(tool, McpSchema.CallToolRequest.builder("tool_name").arguments(Map.of()).build());

    var resultCaptor = ArgumentCaptor.forClass(ToolInvocationResult.class);
    verify(analyticsService).notifyToolInvoked(resultCaptor.capture());
    assertThat(resultCaptor.getValue().matchingToolsets()).containsExactly("analysis", "vortex");
  }

  @Test
  void it_should_skip_analytics_when_both_context_and_supplier_are_null() {
    var analyticsService = syncAnalyticsService();
    var executor = new ToolExecutor(mockBackendService, analyticsService, null, null, null, ToolCategory::all);

    executeDummyTool(executor);

    verify(analyticsService, never()).notifyToolInvoked(any(ToolInvocationResult.class));
  }

  @Test
  void it_should_dispatch_event_in_http_mode_and_resolve_context_from_server_api() {
    var analyticsService = syncAnalyticsService();
    var mockServerApi = mock(ServerApi.class, RETURNS_DEEP_STUBS);
    var executor = new ToolExecutor(mockBackendService, analyticsService, null, () -> mockServerApi, null, ToolCategory::all);

    executeDummyTool(executor);

    verify(mockServerApi).isSonarQubeCloud();
    verify(analyticsService).notifyToolInvoked(any(ToolInvocationResult.class));
  }

  @Test
  void it_should_capture_selected_organization_before_async_analytics_in_both_transports() {
    for (var stdio : List.of(true, false)) {
      var analyticsService = mock(AnalyticsService.class);
      var defaultApi = mock(ServerApi.class, RETURNS_DEEP_STUBS);
      when(defaultApi.isSonarQubeCloud()).thenReturn(true);
      when(defaultApi.getOrganization()).thenReturn("default-org");
      when(defaultApi.organizationsApi().getOrganizationUuidV4("default-org")).thenReturn("default-uuid");
      when(defaultApi.usersApi().getCurrentUserId()).thenReturn("cached-user");
      var selectedApi = mock(ServerApi.class, RETURNS_DEEP_STUBS);
      when(defaultApi.withOrganization("selected-org")).thenReturn(selectedApi);
      when(selectedApi.isSonarQubeCloud()).thenReturn(true);
      when(selectedApi.getOrganization()).thenReturn("selected-org");
      when(selectedApi.organizationsApi().getOrganizationUuidV4("selected-org")).thenReturn("selected-uuid");
      when(selectedApi.usersApi().getCurrentUserId()).thenReturn("selected-user");
      var context = ConnectionContext.empty();
      context.captureCallingAgent("agent", "1.0");
      if (stdio) {
        context.resolveFrom(defaultApi);
      }
      var currentApi = new java.util.concurrent.atomic.AtomicReference<>(defaultApi);
      var executor = new ToolExecutor(mockBackendService, analyticsService, stdio ? context : null, currentApi::get, null, ToolCategory::all);
      record TestResponse(String message) {}
      var tool = new Tool(McpSchema.Tool.builder("tool_name", EMPTY_INPUT_SCHEMA).description("").build(), ToolCategory.PROJECTS) {
        @Override
        public Result execute(Arguments arguments) {
          return Result.success(new TestResponse("ok"));
        }
      };

      executor.execute(tool, McpSchema.CallToolRequest.builder("tool_name").arguments(Map.of("organization", "selected-org")).build());
      var taskCaptor = ArgumentCaptor.forClass(Runnable.class);
      verify(analyticsService).submit(taskCaptor.capture());
      verify(selectedApi.organizationsApi(), never()).getOrganizationUuidV4(any());
      verify(selectedApi.usersApi(), never()).getCurrentUserId();
      currentApi.set(null);
      taskCaptor.getValue().run();

      var resultCaptor = ArgumentCaptor.forClass(ToolInvocationResult.class);
      verify(analyticsService).notifyToolInvoked(resultCaptor.capture());
      assertThat(resultCaptor.getValue().organizationUuidV4()).isEqualTo("selected-uuid");
      assertThat(resultCaptor.getValue().userUuid()).isEqualTo(stdio ? "cached-user" : "selected-user");
      if (stdio) {
        assertThat(resultCaptor.getValue().callingAgentName()).isEqualTo("agent");
        assertThat(context.getOrganizationUuidV4()).isEqualTo("default-uuid");
        verify(selectedApi.usersApi(), never()).getCurrentUserId();
      }
    }
  }

  @Test
  void it_should_reuse_stdio_startup_identity_and_cache_organization_selections() {
    var defaultApi = mock(ServerApi.class, RETURNS_DEEP_STUBS);
    when(defaultApi.isSonarQubeCloud()).thenReturn(true);
    when(defaultApi.getOrganization()).thenReturn("default-org");
    when(defaultApi.organizationsApi().getOrganizationUuidV4("default-org")).thenReturn("default-uuid");
    when(defaultApi.usersApi().getCurrentUserId()).thenReturn("cached-user");
    var selectedApi = mock(ServerApi.class, RETURNS_DEEP_STUBS);
    when(defaultApi.withOrganization("selected-org")).thenReturn(selectedApi);
    when(selectedApi.isSonarQubeCloud()).thenReturn(true);
    when(selectedApi.getOrganization()).thenReturn("selected-org");
    when(selectedApi.organizationsApi().getOrganizationUuidV4("selected-org")).thenReturn("selected-uuid");
    var context = ConnectionContext.empty();
    context.resolveFrom(defaultApi);
    context.captureCallingAgent("agent", "1.0");
    clearInvocations(defaultApi.usersApi(), defaultApi.organizationsApi());
    var analyticsService = syncAnalyticsService();
    var executor = new ToolExecutor(mockBackendService, analyticsService, context, () -> defaultApi, null, ToolCategory::all);

    executeDummyTool(executor);
    executeDummyTool(executor);
    executeDummyTool(executor, Map.of("organization", "selected-org"));
    executeDummyTool(executor, Map.of("organization", "selected-org"));
    executeDummyTool(executor);

    var events = ArgumentCaptor.forClass(ToolInvocationResult.class);
    verify(analyticsService, times(5)).notifyToolInvoked(events.capture());
    assertThat(events.getAllValues()).extracting(ToolInvocationResult::organizationUuidV4)
      .containsExactly("default-uuid", "default-uuid", "selected-uuid", "selected-uuid", "default-uuid");
    assertThat(events.getAllValues()).allSatisfy(event -> {
      assertThat(event.userUuid()).isEqualTo("cached-user");
      assertThat(event.callingAgentName()).isEqualTo("agent");
    });
    assertThat(context.getOrganizationUuidV4()).isEqualTo("default-uuid");
    verify(defaultApi.usersApi(), never()).getCurrentUserId();
    verify(defaultApi.organizationsApi(), never()).getOrganizationUuidV4(any());
    verify(selectedApi.usersApi(), never()).getCurrentUserId();
    verify(selectedApi.organizationsApi()).getOrganizationUuidV4("selected-org");
  }

  @Test
  void it_should_retry_failed_stdio_uuid_lookup_without_affecting_tool_results() {
    var api = mock(ServerApi.class, RETURNS_DEEP_STUBS);
    when(api.isSonarQubeCloud()).thenReturn(true);
    when(api.getOrganization()).thenReturn("org");
    when(api.organizationsApi().getOrganizationUuidV4("org"))
      .thenThrow(new IllegalStateException("unavailable")).thenReturn("org-uuid");
    var analyticsService = syncAnalyticsService();
    var executor = new ToolExecutor(mockBackendService, analyticsService, ConnectionContext.empty(), () -> api, null, ToolCategory::all);

    assertThat(executeDummyTool(executor).isError()).isFalse();
    assertThat(executeDummyTool(executor).isError()).isFalse();
    assertThat(executeDummyTool(executor).isError()).isFalse();

    verify(api.organizationsApi(), times(2)).getOrganizationUuidV4("org");
    verify(api.usersApi(), never()).getCurrentUserId();
    var events = ArgumentCaptor.forClass(ToolInvocationResult.class);
    verify(analyticsService, times(2)).notifyToolInvoked(events.capture());
    assertThat(events.getAllValues()).extracting(ToolInvocationResult::organizationUuidV4).containsOnly("org-uuid");
  }

  @Test
  void it_should_reuse_stdio_server_installation_and_user_identity() {
    var api = mock(ServerApi.class, RETURNS_DEEP_STUBS);
    when(api.systemApi().getStatus().id()).thenReturn("server-id");
    when(api.usersApi().getCurrentUserId()).thenReturn("cached-user");
    var context = ConnectionContext.empty();
    context.resolveFrom(api);
    clearInvocations(api.systemApi(), api.usersApi());
    var analyticsService = syncAnalyticsService();
    var executor = new ToolExecutor(mockBackendService, analyticsService, context, () -> api, null, ToolCategory::all);

    executeDummyTool(executor);
    executeDummyTool(executor);

    var events = ArgumentCaptor.forClass(ToolInvocationResult.class);
    verify(analyticsService, times(2)).notifyToolInvoked(events.capture());
    assertThat(events.getAllValues()).allSatisfy(event -> {
      assertThat(event.sqsInstallationId()).isEqualTo("server-id");
      assertThat(event.userUuid()).isEqualTo("cached-user");
      assertThat(event.organizationUuidV4()).isNull();
    });
    verify(api.systemApi(), never()).getStatus();
    verify(api.usersApi(), never()).getCurrentUserId();
  }

  @Test
  void it_should_capture_calling_agent_before_async_dispatch() {
    var analyticsService = mock(AnalyticsService.class);
    var api = mock(ServerApi.class);
    var context = ConnectionContext.empty();
    var executor = new ToolExecutor(mockBackendService, analyticsService, context, () -> api, null, ToolCategory::all);
    executeDummyTool(executor);
    var task = ArgumentCaptor.forClass(Runnable.class);
    verify(analyticsService).submit(task.capture());

    context.captureCallingAgent("later-agent", "2.0");
    task.getValue().run();

    var event = ArgumentCaptor.forClass(ToolInvocationResult.class);
    verify(analyticsService).notifyToolInvoked(event.capture());
    assertThat(event.getValue().callingAgentName()).isNull();
    assertThat(event.getValue().callingAgentVersion()).isNull();
  }

  @Test
  void it_should_isolate_http_identity_and_repeat_lookups_after_request_context_is_cleared() {
    var apiA = mock(ServerApi.class, RETURNS_DEEP_STUBS);
    var apiB = mock(ServerApi.class, RETURNS_DEEP_STUBS);
    for (var api : List.of(apiA, apiB)) {
      when(api.isSonarQubeCloud()).thenReturn(true);
      when(api.getOrganization()).thenReturn("shared-org");
    }
    when(apiA.organizationsApi().getOrganizationUuidV4("shared-org")).thenReturn("uuid-a");
    when(apiA.usersApi().getCurrentUserId()).thenReturn("user-a");
    when(apiB.organizationsApi().getOrganizationUuidV4("shared-org")).thenReturn("uuid-b");
    when(apiB.usersApi().getCurrentUserId()).thenReturn("user-b");
    var requestApi = new ThreadLocal<ServerApi>();
    var analyticsService = mock(AnalyticsService.class);
    var executor = new ToolExecutor(mockBackendService, analyticsService, null, requestApi::get, null, ToolCategory::all);

    for (var api : List.of(apiA, apiB, apiA)) {
      requestApi.set(api);
      try {
        executeDummyTool(executor);
      } finally {
        requestApi.remove();
      }
    }
    verify(apiA.usersApi(), never()).getCurrentUserId();
    verify(apiB.usersApi(), never()).getCurrentUserId();
    verify(apiA.organizationsApi(), never()).getOrganizationUuidV4(any());
    verify(apiB.organizationsApi(), never()).getOrganizationUuidV4(any());
    var tasks = ArgumentCaptor.forClass(Runnable.class);
    verify(analyticsService, times(3)).submit(tasks.capture());
    tasks.getAllValues().forEach(Runnable::run);

    var events = ArgumentCaptor.forClass(ToolInvocationResult.class);
    verify(analyticsService, times(3)).notifyToolInvoked(events.capture());
    assertThat(events.getAllValues()).extracting(ToolInvocationResult::userUuid).containsExactly("user-a", "user-b", "user-a");
    assertThat(events.getAllValues()).extracting(ToolInvocationResult::organizationUuidV4).containsExactly("uuid-a", "uuid-b", "uuid-a");
    verify(apiA.usersApi(), times(2)).getCurrentUserId();
    verify(apiA.organizationsApi(), times(2)).getOrganizationUuidV4("shared-org");
    verify(apiB.usersApi()).getCurrentUserId();
    verify(apiB.organizationsApi()).getOrganizationUuidV4("shared-org");
  }

  @Test
  void it_should_skip_analytics_in_http_mode_when_supplier_throws() {
    var analyticsService = syncAnalyticsService();
    var executor = new ToolExecutor(mockBackendService, analyticsService, null,
      () -> { throw new RuntimeException("no transport context"); }, null, ToolCategory::all);

    executeDummyTool(executor);

    verify(analyticsService, never()).notifyToolInvoked(any(ToolInvocationResult.class));
  }

  @Test
  void it_should_skip_analytics_in_http_mode_when_resolve_from_throws() {
    var analyticsService = syncAnalyticsService();
    var mockServerApi = mock(ServerApi.class, RETURNS_DEEP_STUBS);
    doThrow(new RuntimeException("API unavailable")).when(mockServerApi).isSonarQubeCloud();
    var executor = new ToolExecutor(mockBackendService, analyticsService, null, () -> mockServerApi, null, ToolCategory::all);

    executeDummyTool(executor);

    verify(analyticsService, never()).notifyToolInvoked(any(ToolInvocationResult.class));
  }

  @Test
  void it_should_inject_invocation_id_into_tool_arguments_and_analytics() {
    var analyticsService = syncAnalyticsService();
    var executor = new ToolExecutor(mockBackendService, analyticsService, ConnectionContext.empty(), null, null, ToolCategory::all);
    var tool = mock(Tool.class);
    var toolDefinition = McpSchema.Tool.builder("test_tool", EMPTY_INPUT_SCHEMA).title("desc").description("").build();
    record DummyResponse(String message) {}
    when(tool.definition()).thenReturn(toolDefinition);
    when(tool.getCategories()).thenReturn(Set.of(ToolCategory.ANALYSIS));
    when(tool.execute(any())).thenReturn(Tool.Result.success(new DummyResponse("ok")));

    var toolRequest = McpSchema.CallToolRequest.builder("test_tool")
      .arguments(Map.of("arg", "value"))
      .meta(Map.of("client_meta", "client_value"))
      .build();
    executor.execute(tool, toolRequest);

    var argumentsCaptor = ArgumentCaptor.forClass(Tool.Arguments.class);
    verify(tool).execute(argumentsCaptor.capture());
    var capturedMeta = argumentsCaptor.getValue().getMeta();
    assertThat(capturedMeta)
      .isNotNull()
      .containsEntry("client_meta", "client_value")
      .containsKey("invocation_id")
      .doesNotContainKey("mcp_server_id");
    var invocationId = capturedMeta.get("invocation_id").toString();
    assertThat(invocationId).isNotBlank();
    var resultCaptor = ArgumentCaptor.forClass(ToolInvocationResult.class);
    verify(analyticsService).notifyToolInvoked(resultCaptor.capture());
    assertThat(resultCaptor.getValue().invocationId()).isEqualTo(invocationId);
    assertThat(resultCaptor.getValue().toolName()).isEqualTo("test_tool");
    assertThat(resultCaptor.getValue().isSuccessful()).isTrue();
  }

  @Test
  void it_should_inject_mcp_server_id_into_tool_arguments() {
    var analyticsService = syncAnalyticsService();
    var mcpServerId = "test-server-id-12345";
    var executor = new ToolExecutor(mockBackendService, analyticsService, ConnectionContext.empty(), null, mcpServerId, ToolCategory::all);
    var tool = mock(Tool.class);
    var toolDefinition = McpSchema.Tool.builder("test_tool", EMPTY_INPUT_SCHEMA).title("desc").description("").build();
    record DummyResponse(String message) {}
    when(tool.definition()).thenReturn(toolDefinition);
    when(tool.getCategories()).thenReturn(Set.of(ToolCategory.ANALYSIS));
    when(tool.execute(any())).thenReturn(Tool.Result.success(new DummyResponse("ok")));

    var toolRequest = McpSchema.CallToolRequest.builder("test_tool")
      .arguments(Map.of("arg", "value"))
      .meta(Map.of("client_meta", "client_value"))
      .build();
    executor.execute(tool, toolRequest);

    var argumentsCaptor = ArgumentCaptor.forClass(Tool.Arguments.class);
    verify(tool).execute(argumentsCaptor.capture());
    var capturedMeta = argumentsCaptor.getValue().getMeta();
    assertThat(capturedMeta)
      .isNotNull()
      .containsEntry("client_meta", "client_value")
      .containsEntry("invocation_id", capturedMeta.get("invocation_id"))
      .containsEntry("mcp_server_id", mcpServerId);
  }

  /** Stubs submit() to run the Runnable synchronously so assertions need no async wait. */
  private static AnalyticsService syncAnalyticsService() {
    var service = mock(AnalyticsService.class);
    doAnswer(invocation -> { ((Runnable) invocation.getArgument(0)).run(); return null; })
      .when(service).submit(any(Runnable.class));
    return service;
  }

  private McpSchema.CallToolResult executeDummyTool(ToolExecutor executor) {
    return executeDummyTool(executor, Map.of());
  }

  private McpSchema.CallToolResult executeDummyTool(ToolExecutor executor, Map<String, Object> arguments) {
    record DummyResponse(String message) {}
    return executor.execute(
      new Tool(McpSchema.Tool.builder("tool_name", EMPTY_INPUT_SCHEMA).title("desc").description("").build(),
        ToolCategory.ANALYSIS) {
        @Override
        public Result execute(Arguments arguments) {
          return Result.success(new DummyResponse("ok"));
        }
      },
      McpSchema.CallToolRequest.builder("tool_name").arguments(arguments).build());
  }

}
