/*
 * SonarQube MCP Server
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
package org.sonarsource.sonarqube.mcp.harness;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;

public class SonarQubeMcpTestClient {
  private final McpSyncClient mcpSyncClient;

  public SonarQubeMcpTestClient(McpSyncClient mcpSyncClient) {
    this.mcpSyncClient = mcpSyncClient;
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

  /**
   * Assert that a CallToolResult has the expected text content and error state.
   * This ignores structured content for test comparisons.
   */
  public static void assertResultEquals(McpSchema.CallToolResult actual, String expectedText, boolean expectedIsError) {
    var actualText = actual.content().stream()
      .filter(content -> content instanceof McpSchema.TextContent)
      .map(content -> ((McpSchema.TextContent) content).text())
      .findFirst()
      .orElse("");
    
    if (!actualText.equals(expectedText)) {
      var message = String.format("Text content mismatch:%nExpected:%n%s%n%nActual:%n%s", expectedText, actualText);
      throw new AssertionError(message);
    }
    
    if (actual.isError() != expectedIsError) {
      throw new AssertionError("Expected isError: " + expectedIsError + " but was: " + actual.isError());
    }
  }

}
