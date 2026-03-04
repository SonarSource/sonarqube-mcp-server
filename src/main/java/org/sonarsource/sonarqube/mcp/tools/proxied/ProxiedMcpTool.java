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
package org.sonarsource.sonarqube.mcp.tools.proxied;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import org.sonarsource.sonarqube.mcp.client.McpClientManager;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class ProxiedMcpTool extends Tool {
  
  private static final McpLogger LOG = McpLogger.getInstance();
  
  private final McpClientManager clientManager;
  private final String serverId;
  private final String originalToolName;

  public ProxiedMcpTool(String toolName, String serverId, String originalToolName, McpSchema.Tool originalTool,
    McpClientManager clientManager) {
    super(createProxyDefinition(toolName, originalTool), ToolCategory.CAG);
    this.serverId = serverId;
    this.originalToolName = originalToolName;
    this.clientManager = clientManager;
  }
  
  private static McpSchema.Tool createProxyDefinition(String toolName, McpSchema.Tool originalTool) {
    var title = originalTool.title() != null ? originalTool.title() : originalTool.name();
    var description = originalTool.description() != null ? originalTool.description() : "Proxied MCP tool";
    
    return new McpSchema.Tool(
      toolName,
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
      // Forward the request to the proxied MCP server
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
      var errorMessage = "Failed to execute proxied tool '" + originalToolName + "' on server '" + serverId + "': " + e.getMessage();
      LOG.error(errorMessage, e);
      return Result.failure(errorMessage);
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
