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
package org.sonarsource.sonarqube.mcp.client;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;
import org.sonarsource.sonarqube.mcp.tools.external.ExternalMcpTool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExternalMcpToolTest {

  private McpClientManager clientManager;
  private McpSchema.Tool originalTool;

  @BeforeEach
  void setUp() {
    clientManager = mock(McpClientManager.class);
    
    // Create a simple tool definition
    originalTool = new McpSchema.Tool(
      "get_weather",
      "Get Weather",
      "Get the current weather for a location",
      new McpSchema.JsonSchema("object", Map.of(), List.of(), false, Map.of(), Map.of()),
      null,
      null,
      null
    );
  }

  @Test
  void constructor_should_create_tool_with_correct_definition() {
    var tool = new ExternalMcpTool(
      "weather_get_weather",
      "weather",
      "get_weather",
      originalTool,
      clientManager
    );

    var definition = tool.definition();
    assertThat(definition.name()).isEqualTo("weather_get_weather");
    assertThat(definition.title()).isEqualTo("Get Weather");
    assertThat(definition.description()).isEqualTo("Get the current weather for a location");
  }

  @Test
  void constructor_should_use_name_as_title_if_title_is_null() {
    var toolWithoutTitle = new McpSchema.Tool(
      "simple_tool",
      null,
      "A simple tool",
      new McpSchema.JsonSchema("object", Map.of(), List.of(), false, Map.of(), Map.of()),
      null,
      null,
      null
    );

    var tool = new ExternalMcpTool("ns_simple_tool", "ns", "simple_tool", toolWithoutTitle, clientManager);

    assertThat(tool.definition().title()).isEqualTo("simple_tool");
  }

  @Test
  void constructor_should_default_description_if_null() {
    var toolWithoutDescription = new McpSchema.Tool(
      "simple_tool",
      null,
      null,
      new McpSchema.JsonSchema("object", Map.of(), List.of(), false, Map.of(), Map.of()),
      null,
      null,
      null
    );

    var tool = new ExternalMcpTool("ns_simple_tool", "ns", "simple_tool", toolWithoutDescription, clientManager);

    assertThat(tool.definition().description()).isEqualTo("External tool");
  }

  @Test
  void getCategory_should_return_external() {
    var tool = new ExternalMcpTool("weather_get_weather", "weather", "get_weather", originalTool, clientManager);

    assertThat(tool.getCategory()).isEqualTo(ToolCategory.EXTERNAL);
  }

  @Test
  void getServerId_should_return_server_id() {
    var tool = new ExternalMcpTool("weather_get_weather", "weather-server", "get_weather", originalTool, clientManager);

    assertThat(tool.getServerId()).isEqualTo("weather-server");
  }

  @Test
  void getOriginalToolName_should_return_original_name() {
    var tool = new ExternalMcpTool("weather_get_weather", "weather", "get_weather", originalTool, clientManager);

    assertThat(tool.getOriginalToolName()).isEqualTo("get_weather");
  }

  @Test
  void execute_should_forward_request_to_client_manager() throws Exception {
    var tool = new ExternalMcpTool("weather_get_weather", "weather", "get_weather", originalTool, clientManager);

    var expectedResult = McpSchema.CallToolResult.builder()
      .isError(false)
      .addTextContent("Sunny, 72°F")
      .build();

    when(clientManager.executeTool(eq("weather"), eq("get_weather"), anyMap()))
      .thenReturn(expectedResult);

    var arguments = new Tool.Arguments(Map.of("location", "New York"));
    var result = tool.execute(arguments);

    assertThat(result.isError()).isFalse();
  }

  @Test
  void execute_should_return_failure_when_provider_unavailable() throws Exception {
    var tool = new ExternalMcpTool("weather_get_weather", "weather", "get_weather", originalTool, clientManager);

    when(clientManager.executeTool(eq("weather"), eq("get_weather"), anyMap()))
      .thenThrow(new McpClientManager.ProviderUnavailableException("Service not connected"));

    var arguments = new Tool.Arguments(Map.of("location", "New York"));
    var result = tool.execute(arguments);

    assertThat(result.isError()).isTrue();
    assertThat(result.toCallToolResult().content().get(0))
      .isInstanceOf(McpSchema.TextContent.class);
    var textContent = (McpSchema.TextContent) result.toCallToolResult().content().get(0);
    assertThat(textContent.text()).isEqualTo("This feature is temporarily unavailable. Please try again later.");
  }

  @Test
  void execute_should_return_failure_on_unexpected_exception() throws Exception {
    var tool = new ExternalMcpTool("weather_get_weather", "weather", "get_weather", originalTool, clientManager);

    when(clientManager.executeTool(eq("weather"), eq("get_weather"), anyMap()))
      .thenThrow(new RuntimeException("Unexpected error"));

    var arguments = new Tool.Arguments(Map.of("location", "New York"));
    var result = tool.execute(arguments);

    assertThat(result.isError()).isTrue();
  }

  @Test
  void execute_should_handle_error_result_from_external_server() throws Exception {
    var tool = new ExternalMcpTool("weather_get_weather", "weather", "get_weather", originalTool, clientManager);

    var errorResult = McpSchema.CallToolResult.builder()
      .isError(true)
      .addTextContent("Location not found")
      .build();

    when(clientManager.executeTool(eq("weather"), eq("get_weather"), anyMap()))
      .thenReturn(errorResult);

    var arguments = new Tool.Arguments(Map.of("location", "Invalid"));
    var result = tool.execute(arguments);

    assertThat(result.isError()).isTrue();
    var textContent = (McpSchema.TextContent) result.toCallToolResult().content().get(0);
    assertThat(textContent.text()).isEqualTo("Location not found");
  }
}
