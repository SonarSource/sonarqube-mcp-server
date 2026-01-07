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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ForbiddenException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.NotFoundException;
import org.sonarsource.sonarqube.mcp.serverapi.exception.UnauthorizedException;
import org.sonarsource.sonarqube.mcp.slcore.BackendService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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

}
