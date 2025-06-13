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
package org.sonarsource.sonarqube.mcp;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfiguration;
import org.sonarsource.sonarqube.mcp.http.HttpClientProvider;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.plugins.PluginsSynchronizer;
import org.sonarsource.sonarqube.mcp.serverapi.EndpointParams;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.slcore.BackendService;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolExecutor;
import org.sonarsource.sonarqube.mcp.tools.analysis.AnalysisTool;
import org.sonarsource.sonarqube.mcp.tools.issues.ChangeIssueStatusTool;
import org.sonarsource.sonarqube.mcp.tools.issues.SearchIssuesTool;
import org.sonarsource.sonarqube.mcp.tools.languages.ListLanguagesTool;
import org.sonarsource.sonarqube.mcp.tools.measures.GetComponentMeasuresTool;
import org.sonarsource.sonarqube.mcp.tools.metrics.SearchMetricsTool;
import org.sonarsource.sonarqube.mcp.tools.projects.SearchMyProjectsTool;
import org.sonarsource.sonarqube.mcp.tools.qualitygates.ListQualityGatesTool;
import org.sonarsource.sonarqube.mcp.tools.qualitygates.ProjectStatusTool;
import org.sonarsource.sonarqube.mcp.tools.rules.ListRuleRepositoriesTool;
import org.sonarsource.sonarqube.mcp.tools.rules.ShowRuleTool;
import org.sonarsource.sonarqube.mcp.tools.sources.GetRawSourceTool;
import org.sonarsource.sonarqube.mcp.tools.sources.GetScmInfoTool;
import org.sonarsource.sonarqube.mcp.transport.StdioServerTransportProvider;
import org.sonarsource.sonarqube.mcp.tools.system.SystemHealthTool;
import org.sonarsource.sonarqube.mcp.tools.system.SystemInfoTool;
import org.sonarsource.sonarqube.mcp.tools.system.SystemLogsTool;
import org.sonarsource.sonarqube.mcp.tools.system.SystemPingTool;
import org.sonarsource.sonarqube.mcp.tools.system.SystemStatusTool;

public class SonarQubeMcpServer {

  private static final McpLogger LOG = McpLogger.getInstance();
  private final BackendService backendService;
  private final ToolExecutor toolExecutor;
  private final StdioServerTransportProvider transportProvider;
  private final List<Tool> supportedTools = new ArrayList<>();
  private final McpServerLaunchConfiguration mcpConfiguration;
  private final HttpClientProvider httpClientProvider;
  private final PluginsSynchronizer pluginsSynchronizer;
  private McpSyncServer syncServer;
  private volatile boolean isShutdown = false;

  public static void main(String[] args) {
    new SonarQubeMcpServer(new StdioServerTransportProvider(), System.getenv()).start();
  }

  public SonarQubeMcpServer(StdioServerTransportProvider transportProvider, Map<String, String> environment) {
    this.transportProvider = transportProvider;
    this.mcpConfiguration = new McpServerLaunchConfiguration(environment);
    this.backendService = new BackendService(mcpConfiguration);
    this.httpClientProvider = new HttpClientProvider(mcpConfiguration.getUserAgent());
    var serverApi = initializeServerApi(mcpConfiguration);
    this.pluginsSynchronizer = new PluginsSynchronizer(serverApi, mcpConfiguration.getStoragePath());
    this.toolExecutor = new ToolExecutor(backendService);

    // SonarQube Server specific tools
    if (!mcpConfiguration.isSonarCloud()) {
      this.supportedTools.addAll(List.of(
        new SystemHealthTool(serverApi),
        new SystemInfoTool(serverApi),
        new SystemLogsTool(serverApi),
        new SystemPingTool(serverApi),
        new SystemStatusTool(serverApi)
      ));
    }

    this.supportedTools.addAll(List.of(
      new ChangeIssueStatusTool(serverApi),
      new SearchMyProjectsTool(serverApi),
      new SearchIssuesTool(serverApi),
      new ProjectStatusTool(serverApi),
      new ShowRuleTool(serverApi),
      new ListRuleRepositoriesTool(serverApi),
      new ListQualityGatesTool(serverApi),
      new ListLanguagesTool(serverApi),
      new AnalysisTool(backendService),
      new GetComponentMeasuresTool(serverApi),
      new SearchMetricsTool(serverApi),
      new GetScmInfoTool(serverApi),
      new GetRawSourceTool(serverApi)));
  }

  public void start() {
    syncServer = McpServer.sync(transportProvider)
      .serverInfo(new McpSchema.Implementation("sonarqube-mcp-server", mcpConfiguration.getAppVersion()))
      .capabilities(McpSchema.ServerCapabilities.builder().tools(true).logging().build())
      .tools(supportedTools.stream().map(this::toSpec).toArray(McpServerFeatures.SyncToolSpecification[]::new))
      .build();
    LOG.setOutput(syncServer);

    var analyzers = pluginsSynchronizer.synchronizeAnalyzers();
    backendService.initialize(analyzers);
    Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
  }

  private McpServerFeatures.SyncToolSpecification toSpec(Tool tool) {
    return new McpServerFeatures.SyncToolSpecification(
      tool.definition(),
      (exchange, argMap) -> toolExecutor.execute(tool, argMap));
  }

  private ServerApi initializeServerApi(McpServerLaunchConfiguration mcpConfiguration) {
    var organization = mcpConfiguration.getSonarqubeOrg();
    var token = mcpConfiguration.getSonarQubeToken();
    var url = mcpConfiguration.getSonarQubeUrl();

    var httpClient = httpClientProvider.getHttpClient(token);

    var serverApiHelper = new ServerApiHelper(new EndpointParams(url, organization), httpClient);
    return new ServerApi(serverApiHelper);
  }

  public void shutdown() {
    if (isShutdown) {
      return;
    }
    isShutdown = true;
    try {
      httpClientProvider.shutdown();
    } catch (Exception e) {
      LOG.error("Error shutting down HTTP client", e);
    }
    try {
      if (syncServer != null) {
        syncServer.closeGracefully();
      }
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
