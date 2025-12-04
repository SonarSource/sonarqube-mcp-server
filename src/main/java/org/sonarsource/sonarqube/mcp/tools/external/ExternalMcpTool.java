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
import org.sonarsource.sonarqube.mcp.client.McpClientManager;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

/**
 * Proxy tool that forwards requests to an integrated MCP component.
 * This allows tools from other components to be exposed through the SonarQube MCP server
 * as if they were native tools.
 * 
 * The internal architecture (providers, external servers) is not exposed to users.
 * Users see these as regular SonarQube tools.
 */
public class ExternalMcpTool extends Tool {
  
  private static final String UNAVAILABLE_MESSAGE = 
    "This feature is temporarily unavailable. Please try again later.";
  
  private final McpClientManager clientManager;
  private final String serverId;
  private final String originalToolName;
  
  /**
   * Create a proxy tool for an integrated MCP component tool.
   * 
   * @param prefixedName The prefixed tool name (e.g., "context_get_code")
   * @param serverId The internal ID of the component (not user-visible)
   * @param originalToolName The original tool name on the component
   * @param originalTool The original tool definition from the component
   * @param clientManager The client manager to use for executing the tool
   */
  public ExternalMcpTool(
    String prefixedName,
    String serverId,
    String originalToolName,
    McpSchema.Tool originalTool,
    McpClientManager clientManager
  ) {
    super(createProxyDefinition(prefixedName, originalTool), ToolCategory.EXTERNAL);
    this.serverId = serverId;
    this.originalToolName = originalToolName;
    this.clientManager = clientManager;
  }
  
  private static McpSchema.Tool createProxyDefinition(
    String prefixedName,
    McpSchema.Tool originalTool
  ) {
    // Use original description without exposing internal architecture
    var title = originalTool.title() != null ? originalTool.title() : originalTool.name();
    var description = originalTool.description() != null 
      ? originalTool.description()
      : "SonarQube tool";
    
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
      // Forward the request to the component
      var result = clientManager.executeTool(
        serverId,
        originalToolName,
        arguments.toMap()
      );
      
      // Convert the CallToolResult to our Tool.Result format
      if (result.isError()) {
        return Result.failure(formatContent(result.content()));
      } else {
        // Since we're proxying, return the result directly
        return new Result(result);
      }
      
    } catch (McpClientManager.ProviderUnavailableException e) {
      // User-friendly error - don't expose internal details
      return Result.failure(UNAVAILABLE_MESSAGE);
    } catch (Exception e) {
      // Generic error - don't expose internal details
      return Result.failure(UNAVAILABLE_MESSAGE);
    }
  }
  
  private String formatContent(Object content) {
    if (content instanceof java.util.List<?> contentList) {
      // Handle list of content items
      var sb = new StringBuilder();
      for (var item : contentList) {
        if (item instanceof McpSchema.TextContent textContent) {
          sb.append(textContent.text());
        } else if (item instanceof McpSchema.ImageContent imageContent) {
          sb.append("[Image: ").append(imageContent.mimeType()).append("]");
        } else if (item instanceof McpSchema.ResourceContents resourceContents) {
          sb.append("[Resource: ").append(resourceContents.uri()).append("]");
        } else {
          sb.append(item.toString());
        }
        sb.append("\n");
      }
      return sb.toString().trim();
    }
    return content != null ? content.toString() : "";
  }
  
  /**
   * Get the internal server ID this tool belongs to.
   * This is for internal use only and not exposed to users.
   */
  public String getServerId() {
    return serverId;
  }
  
  /**
   * Get the original tool name on the component.
   */
  public String getOriginalToolName() {
    return originalToolName;
  }
}
