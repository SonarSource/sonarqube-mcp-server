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
import org.sonarsource.sonarqube.mcp.tools.proxied.ProxiedMcpTool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProxiedMcpToolTest {

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
    var tool = new ProxiedMcpTool(
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
  void getCategory_should_return_external() {
    var tool = new ProxiedMcpTool("weather_get_weather", "weather", "get_weather", originalTool, clientManager);

    assertThat(tool.getCategory()).isEqualTo(ToolCategory.EXTERNAL);
  }

  @Test
  void getOriginalToolName_should_return_original_name() {
    var tool = new ProxiedMcpTool("weather_get_weather", "weather", "get_weather", originalTool, clientManager);

    assertThat(tool.getOriginalToolName()).isEqualTo("get_weather");
  }

  @Test
  void execute_should_handle_error_result_from_proxied_server() throws Exception {
    var tool = new ProxiedMcpTool("weather_get_weather", "weather", "get_weather", originalTool, clientManager);

    var errorResult = McpSchema.CallToolResult.builder()
      .isError(true)
      .addTextContent("Location not found")
      .build();

    when(clientManager.executeTool(eq("weather"), eq("get_weather"), anyMap()))
      .thenReturn(errorResult);

    var arguments = new Tool.Arguments(Map.of("location", "Invalid"));
    var result = tool.execute(arguments);

    assertThat(result.isError()).isTrue();
    var textContent = (McpSchema.TextContent) result.toCallToolResult().content().getFirst();
    assertThat(textContent.text()).isEqualTo("Location not found");
  }

  @Test
  void execute_should_ignore_image_content_in_error_result() throws Exception {
    var tool = new ProxiedMcpTool("image_tool", "img", "process_image", originalTool, clientManager);

    var imageContent = new McpSchema.ImageContent(
      null,
      "data:image/png;base64,iVBORw0KG...",
      "image/png",
      null
    );

    var errorResult = McpSchema.CallToolResult.builder()
      .isError(true)
      .content(List.of(
        new McpSchema.TextContent(null, "Error processing image", null),
        imageContent
      ))
      .build();

    when(clientManager.executeTool(eq("img"), eq("process_image"), anyMap()))
      .thenReturn(errorResult);

    var arguments = new Tool.Arguments(Map.of("image", "test.png"));
    var result = tool.execute(arguments);

    assertThat(result.isError()).isTrue();
    var textContent = (McpSchema.TextContent) result.toCallToolResult().content().getFirst();
    // Only text content is included, image is ignored
    assertThat(textContent.text()).isEqualTo("Error processing image");
  }

}
