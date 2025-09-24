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
import io.modelcontextprotocol.server.McpSyncServerExchange;
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
import org.sonarsource.sonarqube.mcp.tools.analysis.AutomaticAnalysisEnablementTool;
import org.sonarsource.sonarqube.mcp.tools.analysis.AnalysisTool;
import org.sonarsource.sonarqube.mcp.tools.analysis.AnalyzeListFilesTool;
import org.sonarsource.sonarqube.mcp.tools.enterprises.ListEnterprisesTool;
import org.sonarsource.sonarqube.mcp.tools.issues.ChangeIssueStatusTool;
import org.sonarsource.sonarqube.mcp.tools.issues.SearchIssuesTool;
import org.sonarsource.sonarqube.mcp.tools.languages.ListLanguagesTool;
import org.sonarsource.sonarqube.mcp.tools.measures.GetComponentMeasuresTool;
import org.sonarsource.sonarqube.mcp.tools.metrics.SearchMetricsTool;
import org.sonarsource.sonarqube.mcp.tools.portfolios.ListPortfoliosTool;
import org.sonarsource.sonarqube.mcp.tools.projects.SearchMyProjectsTool;
import org.sonarsource.sonarqube.mcp.tools.qualitygates.ListQualityGatesTool;
import org.sonarsource.sonarqube.mcp.tools.qualitygates.ProjectStatusTool;
import org.sonarsource.sonarqube.mcp.tools.rules.ListRuleRepositoriesTool;
import org.sonarsource.sonarqube.mcp.tools.rules.ShowRuleTool;
import org.sonarsource.sonarqube.mcp.tools.dependencyrisks.SearchDependencyRisksTool;
import org.sonarsource.sonarqube.mcp.tools.sources.GetRawSourceTool;
import org.sonarsource.sonarqube.mcp.tools.sources.GetScmInfoTool;
import org.sonarsource.sonarqube.mcp.tools.system.SystemHealthTool;
import org.sonarsource.sonarqube.mcp.tools.system.SystemInfoTool;
import org.sonarsource.sonarqube.mcp.tools.system.SystemLogsTool;
import org.sonarsource.sonarqube.mcp.tools.system.SystemPingTool;
import org.sonarsource.sonarqube.mcp.tools.system.SystemStatusTool;
import org.sonarsource.sonarqube.mcp.tools.webhooks.CreateWebhookTool;
import org.sonarsource.sonarqube.mcp.tools.webhooks.ListWebhooksTool;
import org.sonarsource.sonarqube.mcp.bridge.SonarQubeIdeBridgeClient;
import org.sonarsource.sonarqube.mcp.transport.StdioServerTransportProvider;

public class SonarQubeMcpServer {

  private static final McpLogger LOG = McpLogger.getInstance();
  private final BackendService backendService;
  private final ToolExecutor toolExecutor;
  private final StdioServerTransportProvider transportProvider;
  private final List<Tool> supportedTools = new ArrayList<>();
  private final McpServerLaunchConfiguration mcpConfiguration;
  private final HttpClientProvider httpClientProvider;
  private final PluginsSynchronizer pluginsSynchronizer;
  private final SonarQubeVersionChecker sonarQubeVersionChecker;
  private McpSyncServer syncServer;
  private volatile boolean isShutdown = false;
  private boolean logFileLocationLogged;

  public static void main(String[] args) {
    new SonarQubeMcpServer(new StdioServerTransportProvider(), System.getenv()).start();
  }

  public SonarQubeMcpServer(StdioServerTransportProvider transportProvider, Map<String, String> environment) {
    this.transportProvider = transportProvider;
    this.mcpConfiguration = new McpServerLaunchConfiguration(environment);
    this.backendService = new BackendService(mcpConfiguration);
    this.httpClientProvider = new HttpClientProvider(mcpConfiguration.getUserAgent());
    var serverApi = initializeServerApi(mcpConfiguration);
    this.sonarQubeVersionChecker = new SonarQubeVersionChecker(serverApi);
    this.pluginsSynchronizer = new PluginsSynchronizer(serverApi, mcpConfiguration.getStoragePath());
    this.toolExecutor = new ToolExecutor(backendService);
    var sonarqubeIdeBridgeClient = initializeBridgeClient(mcpConfiguration);

    if (sonarqubeIdeBridgeClient.isAvailable()) {
      LOG.info("SonarQube for IDE integration is available, enabling related tools.");
      this.supportedTools.add(new AnalyzeListFilesTool(sonarqubeIdeBridgeClient));
      this.supportedTools.add(new AutomaticAnalysisEnablementTool(sonarqubeIdeBridgeClient));
    }

    // SonarQube Server specific tools
    if (!mcpConfiguration.isSonarCloud()) {
      this.supportedTools.addAll(List.of(
        new SystemHealthTool(serverApi),
        new SystemInfoTool(serverApi),
        new SystemLogsTool(serverApi),
        new SystemPingTool(serverApi),
        new SystemStatusTool(serverApi)));

      if (sonarQubeVersionChecker.isSonarQubeServerVersionHigherOrEqualsThan("2025.4")) {
        if (sonarQubeVersionChecker.isScaEnabled()) {
          this.supportedTools.add(new SearchDependencyRisksTool(serverApi));
        } else {
          LOG.info("Search Dependency Risks tool is not available because Advanced Security is not enabled.");
        }
      } else {
        LOG.info("Search Dependency Risks tool is not available because it requires SonarQube Server 2025.4 Enterprise or higher.");
      }
    }

    // SonarQube Cloud specific tools
    if (mcpConfiguration.isSonarCloud()) {
      this.supportedTools.add(new ListEnterprisesTool(serverApi));
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
      new AnalysisTool(backendService, serverApi),
      new GetComponentMeasuresTool(serverApi),
      new SearchMetricsTool(serverApi),
      new GetScmInfoTool(serverApi),
      new GetRawSourceTool(serverApi),
      new CreateWebhookTool(serverApi),
      new ListWebhooksTool(serverApi),
      new ListPortfoliosTool(serverApi)));
  }

  public void start() {
    sonarQubeVersionChecker.failIfSonarQubeServerVersionIsNotSupported();
    syncServer = McpServer.sync(transportProvider)
      .serverInfo(new McpSchema.Implementation("sonarqube-mcp-server", mcpConfiguration.getAppVersion()))
      .capabilities(McpSchema.ServerCapabilities.builder().tools(true).logging().build())
      .tools(supportedTools.stream().map(this::toSpec).toArray(McpServerFeatures.SyncToolSpecification[]::new))
      .build();

    var analyzers = pluginsSynchronizer.synchronizeAnalyzers();
    backendService.initialize(analyzers);
    Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
  }

  private McpServerFeatures.SyncToolSpecification toSpec(Tool tool) {
    return new McpServerFeatures.SyncToolSpecification(
      tool.definition(),
      (exchange, argMap) -> {
        logLogFileLocation(exchange);
        return toolExecutor.execute(tool, argMap);
      });
  }

  private void logLogFileLocation(McpSyncServerExchange exchange) {
    if (!logFileLocationLogged) {
      exchange.loggingNotification(new McpSchema.LoggingMessageNotification(McpSchema.LoggingLevel.INFO, "sonarqube-mcp-server",
        "Logs are redirected to " + mcpConfiguration.getLogFilePath().toAbsolutePath()));
      logFileLocationLogged = true;
    }
  }

  private ServerApi initializeServerApi(McpServerLaunchConfiguration mcpConfiguration) {
    var organization = mcpConfiguration.getSonarqubeOrg();
    var token = mcpConfiguration.getSonarQubeToken();
    var url = mcpConfiguration.getSonarQubeUrl();

    var httpClient = httpClientProvider.getHttpClient(token);

    var serverApiHelper = new ServerApiHelper(new EndpointParams(url, organization), httpClient);
    return new ServerApi(serverApiHelper);
  }

  private SonarQubeIdeBridgeClient initializeBridgeClient(McpServerLaunchConfiguration mcpConfiguration) {
    var bridgeUrl = "http://localhost:" + mcpConfiguration.getSonarQubeIdePort();
    var httpClient = httpClientProvider.getHttpClientWithoutToken();
    var bridgeHelper = new ServerApiHelper(new EndpointParams(bridgeUrl, null), httpClient);
    return new SonarQubeIdeBridgeClient(bridgeHelper);
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
  }

  // Returns the list of supported tools for testing purposes.
  public List<Tool> getSupportedTools() {
    return List.copyOf(supportedTools);
  }

}
