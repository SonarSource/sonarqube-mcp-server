/*
 * Sonar MCP Server
 * Copyright (C) 2025 SonarSource
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.mcp;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.sonar.mcp.log.McpLogger;
import org.sonar.mcp.slcore.BackendService;
import org.sonar.mcp.tools.FindAllProjectsTool;
import org.sonar.mcp.tools.FindIssuesTool;

public class SonarMcpServer {
  private static final McpLogger LOG = McpLogger.getInstance();

  public static void main(String[] args) {

    var backendService = new BackendService();
    var findIssuesTool = new FindIssuesTool(backendService);
    var findAllProjectsTool = new FindAllProjectsTool(backendService);

    var syncServer = McpServer.sync(new StdioServerTransportProvider())
      .serverInfo(new McpSchema.Implementation("sonar-mcp-server", "0.0.1"))
      .capabilities(McpSchema.ServerCapabilities.builder().tools(true).logging().build())
      .tools(findIssuesTool.spec(), findAllProjectsTool.spec())
      .build();

    Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(syncServer, backendService)));
  }

  private static void shutdown(McpSyncServer syncServer, BackendService backendService) {
    try {
      syncServer.closeGracefully();
    } catch (Exception e) {
      LOG.error("Error shutting down MCP server", e);
    }
    try {
      backendService.shutdown();
    } catch (Exception e) {
      LOG.error("Error shutting down MCP backend", e);
    }
  }
}
