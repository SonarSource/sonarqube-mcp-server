/*
 * Sonar MCP Server
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
package org.sonar.mcp;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.sonar.mcp.configuration.McpServerLaunchConfiguration;
import org.sonar.mcp.http.HttpClientProvider;
import org.sonar.mcp.log.McpLogger;
import org.sonar.mcp.serverapi.EndpointParams;
import org.sonar.mcp.serverapi.ServerApi;
import org.sonar.mcp.serverapi.ServerApiHelper;
import org.sonar.mcp.slcore.BackendService;
import org.sonar.mcp.tools.Tool;
import org.sonar.mcp.tools.ToolExecutor;
import org.sonar.mcp.tools.issues.ChangeIssueStatusTool;
import org.sonar.mcp.tools.issues.SearchIssuesTool;
import org.sonar.mcp.tools.languages.ListLanguagesTool;
import org.sonar.mcp.tools.projects.SearchMyProjectsTool;
import org.sonar.mcp.tools.qualitygates.ListQualityGatesTool;
import org.sonar.mcp.tools.qualitygates.ProjectStatusTool;
import org.sonar.mcp.tools.rules.ShowRuleTool;
import org.sonar.mcp.tools.rules.ListRuleRepositoriesTool;

public class SonarMcpServer {

  private static final McpLogger LOG = McpLogger.getInstance();
  private final BackendService backendService;
  private final ToolExecutor toolExecutor;
  private final StdioServerTransportProvider transportProvider;
  private final List<Tool> supportedTools;
  private final McpServerLaunchConfiguration mcpConfiguration;

  public static void main(String[] args) {
    new SonarMcpServer(new StdioServerTransportProvider(), System.getenv()).start();
  }

  public SonarMcpServer(StdioServerTransportProvider transportProvider, Map<String, String> environment) {
    this.transportProvider = transportProvider;
    this.mcpConfiguration = new McpServerLaunchConfiguration(environment);
    this.backendService = new BackendService(mcpConfiguration);
    var serverApi = initializeServerApi(mcpConfiguration);
    this.toolExecutor = new ToolExecutor(backendService);
    this.supportedTools = List.of(
      new ChangeIssueStatusTool(serverApi),
      new SearchMyProjectsTool(serverApi),
      new SearchIssuesTool(serverApi),
      new ProjectStatusTool(serverApi),
      new ShowRuleTool(serverApi),
      new ListRuleRepositoriesTool(serverApi),
      new ListQualityGatesTool(serverApi),
      new ListLanguagesTool(serverApi)
    );
  }

  public void start() {
    var syncServer = McpServer.sync(transportProvider)
      .serverInfo(new McpSchema.Implementation("sonar-mcp-server", mcpConfiguration.getAppVersion()))
      .capabilities(McpSchema.ServerCapabilities.builder().tools(true).logging().build())
      .tools(supportedTools.stream().map(this::toSpec).toArray(McpServerFeatures.SyncToolSpecification[]::new))
      .build();
    LOG.setOutput(syncServer);
    Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(syncServer, backendService)));
  }

  private McpServerFeatures.SyncToolSpecification toSpec(Tool tool) {
    return new McpServerFeatures.SyncToolSpecification(
      tool.definition(),
      (exchange, argMap) -> toolExecutor.execute(tool, argMap)
    );
  }

  private static ServerApi initializeServerApi(McpServerLaunchConfiguration mcpConfiguration) {
    var organization = mcpConfiguration.getSonarqubeCloudOrg();
    var token = mcpConfiguration.getSonarqubeCloudToken();

    var httpClientProvider = new HttpClientProvider(mcpConfiguration.getUserAgent());
    var httpClient = httpClientProvider.getHttpClient(token);

    var serverApiHelper = new ServerApiHelper(new EndpointParams(mcpConfiguration.getSonarqubeCloudUrl(), organization), httpClient);
    return new ServerApi(serverApiHelper, token);
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
    McpLogger.getInstance().setOutput(null);
  }

  // Returns the list of supported tools for testing purposes.
  public List<Tool> getSupportedTools() {
    return List.copyOf(supportedTools);
  }

}
