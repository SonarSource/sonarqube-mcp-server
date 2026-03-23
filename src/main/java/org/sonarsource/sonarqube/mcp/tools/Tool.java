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

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
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

  /**
   * Returns whether this tool should be visible and callable for the given request context.
   * The default implementation always returns {@code true}. Tools that require per-request
   * entitlement checks (e.g. feature-flag gating) can override this method.
   */
  public boolean isEnabledFor(McpTransportContext ctx) {
    return true;
  }

  public abstract Result execute(Arguments arguments);

  public static String resolveFileContent(@Nullable Path configuredWorkspacePath, Arguments arguments, String filePathProperty,
    String fileContentProperty) throws IOException {
    if (configuredWorkspacePath != null) {
      var workspaceRealPath = configuredWorkspacePath.toRealPath();
      var filePath = arguments.getStringOrThrow(filePathProperty);
      var candidate = workspaceRealPath.resolve(filePath).normalize();
      var realResolved = candidate.toRealPath();
      if (!realResolved.startsWith(workspaceRealPath)) {
        throw new IllegalArgumentException(
          "filePath '" + filePath + "' resolves outside the configured workspace '" + configuredWorkspacePath + "'");
      }
      return Files.readString(realResolved);
    }
    return arguments.getStringOrThrow(fileContentProperty);
  }

  public static class Arguments {
    private final Map<String, Object> argumentsMap;
    @Nullable
    private final Map<String, Object> metaMap;

    public Arguments(Map<String, Object> argumentsMap, @Nullable Map<String, Object> metaMap) {
      this.argumentsMap = argumentsMap;
      this.metaMap = metaMap;
    }

    @Nullable
    public Map<String, Object> getMeta() {
      return metaMap;
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

    /**
     * Resolves a project key argument with a fallback to a configured default.
     * If the argument is provided in the tool call, it takes precedence.
     * If not provided and a configured default exists, the default is used.
     * If neither is available, a {@link MissingRequiredArgumentException} is thrown.
     */
    public String getProjectKeyWithFallback(String argumentName, @Nullable String configuredDefault) {
      var fromArg = getOptionalString(argumentName);
      if (fromArg != null && !fromArg.isBlank()) {
        return fromArg;
      }
      if (configuredDefault != null && !configuredDefault.isBlank()) {
        return configuredDefault;
      }
      throw new MissingRequiredArgumentException(argumentName);
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

    @CheckForNull
    public String getOptionalEnumValue(String argumentName, String[] validValues) {
      var arg = argumentsMap.get(argumentName);
      if (arg == null) {
        return null;
      }
      
      var value = switch (arg) {
        case String string -> string;
        case List<?> list when !list.isEmpty() -> String.valueOf(list.getFirst());
        case List<?> ignored -> null;
        default -> String.valueOf(arg);
      };
      
      if (value != null && !isValidEnumValue(value, validValues)) {
        throw new IllegalArgumentException("Invalid " + argumentName + ": " + value + ". Possible values: " + String.join(", ", validValues));
      }
      
      return value;
    }

    public String getEnumOrThrow(String argumentName, String[] validValues) {
      var arg = argumentsMap.get(argumentName);
      if (arg == null) {
        throw new MissingRequiredArgumentException(argumentName);
      }
      
      var value = switch (arg) {
        case String string -> string;
        case List<?> list when !list.isEmpty() -> String.valueOf(list.getFirst());
        case List<?> ignored -> throw new MissingRequiredArgumentException(argumentName);
        default -> String.valueOf(arg);
      };
      
      if (!isValidEnumValue(value, validValues)) {
        throw new IllegalArgumentException("Invalid " + argumentName + ": " + value + ". Possible values: " + String.join(", ", validValues));
      }
      
      return value;
    }

    public String getEnumOrDefault(String argumentName, String[] validValues, String defaultValue) {
      var value = getOptionalEnumValue(argumentName, validValues);
      return value != null ? value : defaultValue;
    }

    @CheckForNull
    public List<String> getOptionalEnumList(String argumentName, String[] validValues) {
      var values = getOptionalStringList(argumentName);
      if (values != null) {
        for (var value : values) {
          if (!isValidEnumValue(value, validValues)) {
            throw new IllegalArgumentException("Invalid " + argumentName + ": " + value + ". Possible values: " + String.join(", ", validValues));
          }
        }
      }
      return values;
    }
    

    private static boolean isValidEnumValue(String value, String[] validValues) {
      for (var validValue : validValues) {
        if (validValue.equals(value)) {
          return true;
        }
      }
      return false;
    }
    
    /**
     * Get the underlying map of arguments.
     * Used for forwarding arguments to proxied MCP servers.
     */
    public Map<String, Object> toMap() {
      return Map.copyOf(argumentsMap);
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
