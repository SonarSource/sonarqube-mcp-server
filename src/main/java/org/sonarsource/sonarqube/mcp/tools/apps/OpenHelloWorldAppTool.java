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
package org.sonarsource.sonarqube.mcp.tools.apps;

import io.modelcontextprotocol.spec.McpSchema;
import org.sonarsource.sonarqube.mcp.apps.HelloWorldApp;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class OpenHelloWorldAppTool extends Tool {

  public static final String TOOL_NAME = "open_hello_world_app";

  public OpenHelloWorldAppTool() {
    super(SchemaToolBuilder.forOutput(OpenHelloWorldAppToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Open Hello World MCP App")
      .setDescription("Open a minimal Hello World MCP app to verify that UI resources are served correctly.")
      .setMeta(HelloWorldApp.toolDescriptorMeta())
      .setReadOnlyHint()
      .build(),
      ToolCategory.APPS);
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    return new Tool.Result(McpSchema.CallToolResult.builder()
      .isError(false)
      .addContent(HelloWorldApp.embeddedResource())
      .addContent(HelloWorldApp.resourceLink())
      .addTextContent("Opened the SonarQube Hello World MCP app.")
      .structuredContent(new OpenHelloWorldAppToolResponse(HelloWorldApp.RESOURCE_URI))
      .meta(HelloWorldApp.toolDescriptorMeta())
      .build());
  }

}
