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

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarqube.mcp.tools.exception.MissingRequiredArgumentException;

public abstract class Tool {
  private final McpSchema.Tool definition;
  private final ToolCategory category;

  protected Tool(McpSchema.Tool definition, ToolCategory category) {
    this.definition = definition;
    this.category = category;
  }

  public McpSchema.Tool definition() {
    return definition;
  }

  public ToolCategory getCategory() {
    return category;
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
      var arg = argumentsMap.get(argumentName);
      return switch (arg) {
        case String string -> string;
        case null -> throw new MissingRequiredArgumentException(argumentName);
        default -> String.valueOf(arg);
      };
    }

    public Boolean getBooleanOrThrow(String argumentName) {
      if (!argumentsMap.containsKey(argumentName)) {
        throw new MissingRequiredArgumentException(argumentName);
      }
      var arg = argumentsMap.get(argumentName);
      return switch (arg) {
        case Boolean bool -> bool;
        case String string -> Boolean.parseBoolean(string);
        case null, default -> throw new MissingRequiredArgumentException(argumentName);
      };
    }

    @CheckForNull
    public Integer getOptionalInteger(String argumentName) {
      var arg = argumentsMap.get(argumentName);
      return switch (arg) {
        case Integer integer -> integer;
        case String string -> Integer.parseInt(string);
        case null, default -> null;
      };
    }

    @CheckForNull
    public Boolean getOptionalBoolean(String argumentName) {
      var arg = argumentsMap.get(argumentName);
      return switch (arg) {
        case Boolean bool -> bool;
        case String string -> Boolean.parseBoolean(string);
        case null, default -> null;
      };
    }

    @CheckForNull
    public String getOptionalString(String argumentName) {
      var arg = argumentsMap.get(argumentName);
      if (arg instanceof String string) {
        return string;
      } else {
        return null;
      }
    }

    public int getIntOrDefault(String argumentName, int defaultValue) {
      var intArgument = getOptionalInteger(argumentName);
      if (intArgument == null) {
        return defaultValue;
      }
      return intArgument;
    }

    @SuppressWarnings("unchecked")
    public List<String> getStringListOrThrow(String argumentName) {
      if (!argumentsMap.containsKey(argumentName)) {
        throw new MissingRequiredArgumentException(argumentName);
      }
      return (List<String>) argumentsMap.get(argumentName);
    }

    @CheckForNull
    @SuppressWarnings("unchecked")
    public List<String> getOptionalStringList(String argumentName) {
      return (List<String>) argumentsMap.get(argumentName);
    }
  }

  public static class Result {
    /**
     * Create a successful result from a response object.
     * The response object will be serialized to both JSON text content and structured content.
     * This follows the MCP spec recommendation that structured content should also be available as text.
     */
    public static Result success(Record responseObject) {
      return new Result(McpSchema.CallToolResult.builder()
        .isError(false)
        .addTextContent(SchemaUtils.toJsonString(responseObject))
        .structuredContent(SchemaUtils.toStructuredContent(responseObject))
        .build());
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
