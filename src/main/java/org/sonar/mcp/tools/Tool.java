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
import java.util.Map;

public abstract class Tool {
  private final McpSchema.Tool definition;

  protected Tool(McpSchema.Tool definition) {
    this.definition = definition;
  }

  public McpSchema.Tool definition() {
    return definition;
  }

  public abstract Result execute(Map<String, Object> arguments);

  public static class Result {
    public static Result success(String content) {
      return new Result(McpSchema.CallToolResult.builder().isError(false).addTextContent(content).build());
    }

    public static Result failure(String errorMessage) {
      return new Result(McpSchema.CallToolResult.builder().isError(true).addTextContent(errorMessage).build());
    }

    public static Result failure(String errorMessage, Throwable throwable) {
      return new Result(McpSchema.CallToolResult.builder().isError(true).addTextContent(errorMessage + ": " + throwable.getMessage()).build());
    }

    private final McpSchema.CallToolResult callToolResult;

    public Result(McpSchema.CallToolResult callToolResult) {
      this.callToolResult = callToolResult;
    }

    public McpSchema.CallToolResult toCallToolResult() {
      return callToolResult;
    }

    public boolean isError() {
      return callToolResult.isError();
    }
  }
}
