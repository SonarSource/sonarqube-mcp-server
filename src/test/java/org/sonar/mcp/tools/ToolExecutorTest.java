/*
 * Sonar MCP Server
 * Copyright (C) 2025 SonarSource
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonar.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonar.mcp.slcore.BackendService;

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
    toolExecutor.execute(new Tool(new McpSchema.Tool("tool_name", null, new McpSchema.JsonSchema("object", Map.of(), List.of(), false))) {
      @Override
      public Result execute(Arguments arguments) {
        return Result.success("Success!");
      }
    }, Map.of());

    verify(mockBackendService).notifyToolCalled("mcp_tool_name", true);
  }

  @Test
  void it_should_register_telemetry_after_the_tool_call_fails() {
    toolExecutor.execute(new Tool(new McpSchema.Tool("tool_name", null, new McpSchema.JsonSchema("object", Map.of(), List.of(), false))) {
      @Override
      public Result execute(Arguments arguments) {
        return Result.failure("Failure!");
      }
    }, Map.of());

    verify(mockBackendService).notifyToolCalled("mcp_tool_name", false);
  }

}
