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
package org.sonarsource.sonarqube.mcp.its.sonarcloud.harness;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class SonarQubeMcpTestClient {
  private final McpSyncClient mcpSyncClient;
  private static final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

  public SonarQubeMcpTestClient(McpSyncClient mcpSyncClient) {
    this.mcpSyncClient = mcpSyncClient;
  }

  public static void assertResultEquals(McpSchema.CallToolResult actual, String expected) {
    assertThat(actual.isError()).isFalse();
    assertThat(actual.structuredContent()).isNotNull();
    assertThat(JsonParser.parseString(gson.toJson(actual.structuredContent()))).isEqualTo(JsonParser.parseString(expected));
  }

  /**
   * Asserts that {@code actual} structured content contains all fields from {@code expectedSubset}.
   * For arrays, each expected element must match at least one actual element (deep subset match).
   */
  public static void assertToolError(McpSchema.CallToolResult actual) {
    var hasErrorFlag = Boolean.TRUE.equals(actual.isError());
    var hasErrorText = actual.content().stream()
      .filter(McpSchema.TextContent.class::isInstance)
      .map(c -> ((McpSchema.TextContent) c).text())
      .anyMatch(text -> text.contains("An error occurred during the tool execution")
        || text.startsWith("Failed to "));
    assertThat(hasErrorFlag || hasErrorText)
      .as("Expected tool error but got isError=%s content=%s", actual.isError(), actual.content())
      .isTrue();
  }

  public static void assertStructuredContentContains(McpSchema.CallToolResult actual, String expectedSubset) {
    assertThat(actual.isError()).isFalse();
    assertThat(actual.structuredContent()).isNotNull();
    var actualJson = JsonParser.parseString(gson.toJson(actual.structuredContent()));
    var expectedJson = JsonParser.parseString(expectedSubset);
    assertThat(jsonContains(actualJson, expectedJson))
      .as("Structured content does not contain expected subset.\nActual: %s\nExpected subset: %s", actualJson, expectedJson)
      .isTrue();
  }

  public static void assertSchemaEquals(Map<String, Object> actual, String expected) {
    assertThat(JsonParser.parseString(gson.toJson(actual))).isEqualTo(JsonParser.parseString(expected));
  }

  private static boolean jsonContains(JsonElement actual, JsonElement expected) {
    if (expected.isJsonObject()) {
      if (!actual.isJsonObject()) {
        return false;
      }
      var actObj = actual.getAsJsonObject();
      var expObj = expected.getAsJsonObject();
      return expObj.entrySet().stream()
        .allMatch(entry -> actObj.has(entry.getKey()) && jsonContains(actObj.get(entry.getKey()), entry.getValue()));
    }
    if (expected.isJsonArray()) {
      if (!actual.isJsonArray()) {
        return false;
      }
      var actArr = actual.getAsJsonArray();
      for (var expEl : expected.getAsJsonArray()) {
        var found = false;
        for (var actEl : actArr) {
          if (jsonContains(actEl, expEl)) {
            found = true;
            break;
          }
        }
        if (!found) {
          return false;
        }
      }
      return true;
    }
    return actual.equals(expected);
  }

  public McpSchema.CallToolResult callTool(String toolName) {
    return callTool(toolName, Map.of());
  }

  public McpSchema.CallToolResult callTool(String toolName, Map<String, Object> arguments) {
    return mcpSyncClient.callTool(new McpSchema.CallToolRequest(toolName, arguments));
  }

  public List<McpSchema.Tool> listTools() {
    return mcpSyncClient.listTools().tools();
  }

}
