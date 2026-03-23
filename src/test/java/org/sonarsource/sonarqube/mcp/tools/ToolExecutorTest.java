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

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
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
import org.sonarsource.sonarqube.mcp.slcore.BackendService;
import org.sonarsource.sonarqube.mcp.tools.exception.MissingRequiredArgumentException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolExecutorTest {

  private BackendService mockBackendService;
  private ToolExecutor toolExecutor;

  @BeforeEach
  void prepare() {
    mockBackendService = mock(BackendService.class);
    toolExecutor = new ToolExecutor(mockBackendService);
  }

  @Test
  void it_should_register_telemetry_after_the_tool_call_succeeds() {
    record TestResponse(@JsonPropertyDescription("Success message") String message) {}
    
    toolExecutor.execute(new Tool(new McpSchema.Tool("tool_name", "test description", "", new McpSchema.JsonSchema("object", Map.of(), List.of(), false, Map.of(), Map.of()), Map.of(), null, Map.of()), ToolCategory.ANALYSIS) {
      @Override
      public Result execute(Arguments arguments) {
        return Result.success(new TestResponse("Success!"));
      }
    }, new McpSchema.CallToolRequest("", Map.of()));

    verify(mockBackendService).notifyToolCalled("mcp_tool_name", true);
  }

  @Test
  void it_should_register_telemetry_after_the_tool_call_fails() {
    toolExecutor.execute(new Tool(new McpSchema.Tool("tool_name", "test description", "", new McpSchema.JsonSchema("object", Map.of(), List.of(), false, Map.of(), Map.of()), Map.of(), null, Map.of()), ToolCategory.ANALYSIS) {
      @Override
      public Result execute(Arguments arguments) {
        return Result.failure("Failure!");
      }
    }, new McpSchema.CallToolRequest("", Map.of()));

    verify(mockBackendService).notifyToolCalled("mcp_tool_name", false);
  }

  @Test
  void it_should_handle_unauthorized_exception() {
    var callToolResult = toolExecutor.execute(new Tool(new McpSchema.Tool("tool_name", "test description", "", new McpSchema.JsonSchema("object", Map.of(), List.of(), false, Map.of(), Map.of()), Map.of(), null, Map.of()), ToolCategory.ANALYSIS) {
      @Override
      public Result execute(Arguments arguments) {
        throw new UnauthorizedException("Not authorized");
      }
    }, new McpSchema.CallToolRequest("", Map.of()));

    assertThat(callToolResult.isError()).isTrue();
    assertThat(callToolResult.content().toString()).contains("An error occurred during the tool execution: " +
      "SonarQube answered with Not authorized. Please verify your token is valid and has the correct permissions.");
    verify(mockBackendService).notifyToolCalled("mcp_tool_name", false);
  }

  @Test
  void it_should_handle_forbidden_exception() {
    var callToolResult = toolExecutor.execute(new Tool(new McpSchema.Tool("tool_name", "test description", "", new McpSchema.JsonSchema("object", Map.of(), List.of(), false, Map.of(), Map.of()), Map.of(), null, Map.of()), ToolCategory.ANALYSIS) {
      @Override
      public Result execute(Arguments arguments) {
        throw new ForbiddenException("Forbidden");
      }
    }, new McpSchema.CallToolRequest("", Map.of()));

    assertThat(callToolResult.isError()).isTrue();
    assertThat(callToolResult.content().toString()).contains("An error occurred during the tool execution: " +
      "SonarQube answered with Forbidden. Please verify your token has the required permissions for this operation.");
    verify(mockBackendService).notifyToolCalled("mcp_tool_name", false);
  }

  @Test
  void it_should_handle_not_found_exception() {
    var callToolResult = toolExecutor.execute(new Tool(new McpSchema.Tool("tool_name", "test description", "", new McpSchema.JsonSchema("object", Map.of(), List.of(), false, Map.of(), Map.of()), Map.of(), null, Map.of()), ToolCategory.ANALYSIS) {
      @Override
      public Result execute(Arguments arguments) {
        throw new NotFoundException("Resource not found");
      }
    }, new McpSchema.CallToolRequest("", Map.of()));

    assertThat(callToolResult.isError()).isTrue();
    assertThat(callToolResult.content().toString()).contains("An error occurred during the tool execution: " +
      "SonarQube answered with Resource not found. Please verify your token is valid and the requested resource exists.");
    verify(mockBackendService).notifyToolCalled("mcp_tool_name", false);
  }

  @Test
  void it_should_handle_generic_exception() {
    var callToolResult = toolExecutor.execute(new Tool(new McpSchema.Tool("tool_name", "test description", "", new McpSchema.JsonSchema("object", Map.of(), List.of(), false, Map.of(), Map.of()), Map.of(), null, Map.of()), ToolCategory.ANALYSIS) {
      @Override
      public Result execute(Arguments arguments) {
        throw new RuntimeException("Unexpected error");
      }
    }, new McpSchema.CallToolRequest("", Map.of()));

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
    var executor = new ToolExecutor(mockBackendService, analyticsService, ConnectionContext.empty(), null);
    var resultCaptor = ArgumentCaptor.forClass(ToolInvocationResult.class);

    executor.execute(new Tool(new McpSchema.Tool("tool_name", "test description", "", new McpSchema.JsonSchema("object", Map.of(), List.of(), false, Map.of(), Map.of()), Map.of(), null, Map.of()), ToolCategory.ANALYSIS) {
      @Override
      public Result execute(Arguments arguments) {
        throw exception;
      }
    }, new McpSchema.CallToolRequest("", Map.of()));

    verify(analyticsService, timeout(2000)).notifyToolInvoked(resultCaptor.capture());
    assertThat(resultCaptor.getValue().errorType()).isEqualTo(expectedErrorType);
  }

  @Test
  void it_should_skip_analytics_when_service_is_null() {
    var executor = new ToolExecutor(mockBackendService, null, ConnectionContext.empty(), null);

    var result = executeDummyTool(executor);

    assertThat(result.isError()).isFalse();
  }

  @Test
  void it_should_dispatch_event_in_stdio_mode_with_pre_resolved_context() {
    var analyticsService = syncAnalyticsService();
    var ctx = ConnectionContext.empty();
    ctx.captureCallingAgent("cursor", "1.0.0");
    var executor = new ToolExecutor(mockBackendService, analyticsService, ctx, null);

    executeDummyTool(executor);

    verify(analyticsService).notifyToolInvoked(any(ToolInvocationResult.class));
  }

  @Test
  void it_should_skip_analytics_when_both_context_and_supplier_are_null() {
    var analyticsService = syncAnalyticsService();
    var executor = new ToolExecutor(mockBackendService, analyticsService, null, null);

    executeDummyTool(executor);

    verify(analyticsService, never()).notifyToolInvoked(any(ToolInvocationResult.class));
  }

  @Test
  void it_should_dispatch_event_in_http_mode_and_resolve_context_from_server_api() {
    var analyticsService = syncAnalyticsService();
    var mockServerApi = mock(ServerApi.class, RETURNS_DEEP_STUBS);
    var executor = new ToolExecutor(mockBackendService, analyticsService, null, () -> mockServerApi);

    executeDummyTool(executor);

    verify(mockServerApi).isSonarQubeCloud();
    verify(analyticsService).notifyToolInvoked(any(ToolInvocationResult.class));
  }

  @Test
  void it_should_skip_analytics_in_http_mode_when_supplier_throws() {
    var analyticsService = syncAnalyticsService();
    var executor = new ToolExecutor(mockBackendService, analyticsService, null,
      () -> { throw new RuntimeException("no transport context"); });

    executeDummyTool(executor);

    verify(analyticsService, never()).notifyToolInvoked(any(ToolInvocationResult.class));
  }

  @Test
  void it_should_skip_analytics_in_http_mode_when_resolve_from_throws() {
    var analyticsService = syncAnalyticsService();
    var mockServerApi = mock(ServerApi.class, RETURNS_DEEP_STUBS);
    doThrow(new RuntimeException("API unavailable")).when(mockServerApi).isSonarQubeCloud();
    var executor = new ToolExecutor(mockBackendService, analyticsService, null, () -> mockServerApi);

    executeDummyTool(executor);

    verify(analyticsService, never()).notifyToolInvoked(any(ToolInvocationResult.class));
  }

  @Test
  void it_should_inject_invocation_id_into_tool_arguments_and_analytics() {
    var analyticsService = syncAnalyticsService();
    var executor = new ToolExecutor(mockBackendService, analyticsService, ConnectionContext.empty(), null);
    var tool = mock(Tool.class);
    var toolDefinition = new McpSchema.Tool("test_tool", "desc", "", new McpSchema.JsonSchema("object", Map.of(), List.of(), false, Map.of(), Map.of()), Map.of(), null, Map.of());
    record DummyResponse(String message) {}
    when(tool.definition()).thenReturn(toolDefinition);
    when(tool.execute(any())).thenReturn(Tool.Result.success(new DummyResponse("ok")));

    var toolRequest = new McpSchema.CallToolRequest("test_tool", Map.of("arg", "value"), Map.of("client_meta", "client_value"));
    executor.execute(tool, toolRequest);

    var argumentsCaptor = ArgumentCaptor.forClass(Tool.Arguments.class);
    verify(tool).execute(argumentsCaptor.capture());
    var capturedMeta = argumentsCaptor.getValue().getMeta();
    assertThat(capturedMeta)
      .isNotNull()
      .containsEntry("client_meta", "client_value")
      .containsKey("invocation_id");
    var invocationId = capturedMeta.get("invocation_id").toString();
    assertThat(invocationId).isNotBlank();
    var resultCaptor = ArgumentCaptor.forClass(ToolInvocationResult.class);
    verify(analyticsService).notifyToolInvoked(resultCaptor.capture());
    assertThat(resultCaptor.getValue().invocationId()).isEqualTo(invocationId);
    assertThat(resultCaptor.getValue().toolName()).isEqualTo("test_tool");
    assertThat(resultCaptor.getValue().isSuccessful()).isTrue();
  }

  /** Stubs submit() to run the Runnable synchronously so assertions need no async wait. */
  private static AnalyticsService syncAnalyticsService() {
    var service = mock(AnalyticsService.class);
    doAnswer(invocation -> { ((Runnable) invocation.getArgument(0)).run(); return null; })
      .when(service).submit(any(Runnable.class));
    return service;
  }

  private McpSchema.CallToolResult executeDummyTool(ToolExecutor executor) {
    record DummyResponse(String message) {}
    return executor.execute(
      new Tool(new McpSchema.Tool("tool_name", "desc", "", new McpSchema.JsonSchema("object", Map.of(), List.of(), false, Map.of(), Map.of()), Map.of(), null, Map.of()),
        ToolCategory.ANALYSIS) {
        @Override
        public Result execute(Arguments arguments) {
          return Result.success(new DummyResponse("ok"));
        }
      },
      new McpSchema.CallToolRequest("", Map.of()));
  }

}
