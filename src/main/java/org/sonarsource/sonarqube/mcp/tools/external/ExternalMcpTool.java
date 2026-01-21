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
package org.sonarsource.sonarqube.mcp.tools.external;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import org.sonarsource.sonarqube.mcp.client.McpClientManager;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class ExternalMcpTool extends Tool {
  
  private static final String UNAVAILABLE_MESSAGE = "This feature is temporarily unavailable. Please try again later.";
  
  private final McpClientManager clientManager;
  private final String serverId;
  private final String originalToolName;

  public ExternalMcpTool(String prefixedName, String serverId, String originalToolName, McpSchema.Tool originalTool,
    McpClientManager clientManager) {
    super(createProxyDefinition(prefixedName, originalTool), ToolCategory.EXTERNAL);
    this.serverId = serverId;
    this.originalToolName = originalToolName;
    this.clientManager = clientManager;
  }
  
  private static McpSchema.Tool createProxyDefinition(String prefixedName, McpSchema.Tool originalTool) {
    var title = originalTool.title() != null ? originalTool.title() : originalTool.name();
    var description = originalTool.description() != null ? originalTool.description() : "External tool";
    
    return new McpSchema.Tool(
      prefixedName,
      title,
      description,
      originalTool.inputSchema(),
      null,
      originalTool.annotations(),
      null
    );
  }
  
  @Override
  public Result execute(Arguments arguments) {
    try {
      // Forward the request to the external server
      var result = clientManager.executeTool(
        serverId,
        originalToolName,
        arguments.toMap()
      );

      if (Boolean.TRUE.equals(result.isError())) {
        return Result.failure(formatContent(result.content()));
      } else {
        return new Result(result);
      }
    } catch (Exception e) {
      return Result.failure(UNAVAILABLE_MESSAGE);
    }
  }
  
  private static String formatContent(Object content) {
    if (content instanceof List<?> contentList) {
      var sb = new StringBuilder();
      contentList.forEach(item -> {
        if (item instanceof McpSchema.TextContent textContent) {
          sb.append(textContent.text()).append("\n");
        }
      });
      return sb.toString().trim();
    }
    return content.toString();
  }

  public String getServerId() {
    return serverId;
  }

  public String getOriginalToolName() {
    return originalToolName;
  }

}
