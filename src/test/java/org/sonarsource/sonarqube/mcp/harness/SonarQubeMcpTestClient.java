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
package org.sonarsource.sonarqube.mcp.harness;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class SonarQubeMcpTestClient {
  private final McpSyncClient mcpSyncClient;
  private static final Gson gson = new GsonBuilder().disableHtmlEscaping().create();

  public SonarQubeMcpTestClient(McpSyncClient mcpSyncClient) {
    this.mcpSyncClient = mcpSyncClient;
  }

  public static void assertResultEquals(McpSchema.CallToolResult actual, String expected) {
    assertThat(JsonParser.parseString(gson.toJson(actual.structuredContent()))).isEqualTo(JsonParser.parseString(expected));
  }

  public static void assertSchemaEquals(Map<String, Object> actual, String expected) {
    assertThat(JsonParser.parseString(gson.toJson(actual))).isEqualTo(JsonParser.parseString(expected));
  }

  public static void assertMissingRequiredArgument(McpSchema.CallToolResult result, String argumentName) {
    assertThat(result.isError()).isTrue();
    var text = ((McpSchema.TextContent) result.content().getFirst()).text();
    assertThat(text).contains(argumentName);
    assertThat(text).satisfiesAnyOf(
      message -> assertThat(message).contains("Missing required argument"),
      message -> assertThat(message).contains("input validation failed"),
      message -> assertThat(message).contains("required property '" + argumentName + "' not found")
    );
  }

  public static void assertToolExecutionError(McpSchema.CallToolResult result, String expectedFragment) {
    assertThat(result.isError()).isTrue();
    var text = ((McpSchema.TextContent) result.content().getFirst()).text();
    assertThat(text).satisfiesAnyOf(
      message -> assertThat(message).contains(expectedFragment),
      message -> assertThat(message).contains("input validation failed")
    );
  }

  public McpSchema.CallToolResult callTool(String toolName) {
    return callTool(toolName, Map.of());
  }

  public McpSchema.CallToolResult callTool(String toolName, Map<String, Object> arguments) {
    return mcpSyncClient.callTool(McpSchema.CallToolRequest.builder(toolName).arguments(arguments).build());
  }

  public List<McpSchema.Tool> listTools() {
    return mcpSyncClient.listTools().tools();
  }

}
