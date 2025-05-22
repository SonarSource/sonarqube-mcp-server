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
import javax.annotation.CheckForNull;
import org.sonar.mcp.tools.exception.MissingRequiredArgumentException;

public abstract class Tool {
  private final McpSchema.Tool definition;

  protected Tool(McpSchema.Tool definition) {
    this.definition = definition;
  }

  public McpSchema.Tool definition() {
    return definition;
  }

  public abstract Result execute(Arguments arguments);

  public static class Arguments {
    private final Map<String, Object> argumentsMap;

    public Arguments(Map<String, Object> argumentsMap) {
      this.argumentsMap = argumentsMap;
    }

    public String getStringOrThrow(String argumentName) {
      if (!argumentsMap.containsKey(argumentName)) {
        throw new MissingRequiredArgumentException(argumentName);
      }
      return (String) argumentsMap.get(argumentName);
    }

    @CheckForNull
    public String getOptionalString(String argumentName) {
      return (String) argumentsMap.get(argumentName);
    }

    public int getIntOrDefault(String argumentName, int defaultValue) {
      var stringArgument = getOptionalString(argumentName);
      if (stringArgument == null) {
        return defaultValue;
      }
      return Integer.parseInt(stringArgument);
    }

    public List<String> getStringListOrThrow(String argumentName) {
      if (!argumentsMap.containsKey(argumentName)) {
        throw new MissingRequiredArgumentException(argumentName);
      }
      return (List<String>) argumentsMap.get(argumentName);
    }

    @CheckForNull
    public List<String> getOptionalStringList(String argumentName) {
      return (List<String>) argumentsMap.get(argumentName);
    }
  }

  public static class Result {
    public static Result success(String content) {
      return new Result(McpSchema.CallToolResult.builder().isError(false).addTextContent(content).build());
    }

    public static Result failure(String errorMessage) {
      return new Result(McpSchema.CallToolResult.builder().isError(true).addTextContent(errorMessage).build());
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
