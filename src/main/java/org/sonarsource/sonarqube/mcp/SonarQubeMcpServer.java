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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import io.modelcontextprotocol.spec.McpServerTransportProviderBase;
import io.modelcontextprotocol.spec.McpStreamableServerTransportProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.bridge.SonarQubeIdeBridgeClient;
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
import org.sonarsource.sonarqube.mcp.tools.analysis.AnalyzeFileListTool;
import org.sonarsource.sonarqube.mcp.tools.analysis.ToggleAutomaticAnalysisTool;
import org.sonarsource.sonarqube.mcp.tools.dependencyrisks.SearchDependencyRisksTool;
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
import org.sonarsource.sonarqube.mcp.tools.sources.GetRawSourceTool;
import org.sonarsource.sonarqube.mcp.tools.sources.GetScmInfoTool;
import org.sonarsource.sonarqube.mcp.tools.system.SystemHealthTool;
import org.sonarsource.sonarqube.mcp.tools.system.SystemInfoTool;
import org.sonarsource.sonarqube.mcp.tools.system.SystemLogsTool;
import org.sonarsource.sonarqube.mcp.tools.system.SystemPingTool;
import org.sonarsource.sonarqube.mcp.tools.system.SystemStatusTool;
import org.sonarsource.sonarqube.mcp.tools.webhooks.CreateWebhookTool;
import org.sonarsource.sonarqube.mcp.tools.webhooks.ListWebhooksTool;
import org.sonarsource.sonarqube.mcp.transport.HttpServerTransportProvider;
import org.sonarsource.sonarqube.mcp.transport.StdioServerTransportProvider;

public class SonarQubeMcpServer {

  private static final McpLogger LOG = McpLogger.getInstance();
  private static final String SONARQUBE_MCP_SERVER_NAME = "sonarqube-mcp-server";

  private BackendService backendService;
  private ToolExecutor toolExecutor;
  private final HttpServerTransportProvider httpServerManager;
  private final McpServerTransportProviderBase transportProvider;
  private final List<Tool> supportedTools = new ArrayList<>();
  private final McpServerLaunchConfiguration mcpConfiguration;
  private HttpClientProvider httpClientProvider;
  private ServerApi serverApi;
  private SonarQubeVersionChecker sonarQubeVersionChecker;
  private McpSyncServer syncServer;
  private volatile boolean isShutdown = false;
  private boolean logFileLocationLogged;

  public static void main(String[] args) {
    new SonarQubeMcpServer(System.getenv()).start();
  }

  public SonarQubeMcpServer(Map<String, String> environment) {
    this.mcpConfiguration = new McpServerLaunchConfiguration(environment);

    if (mcpConfiguration.isHttpEnabled()) {
      this.httpServerManager = new HttpServerTransportProvider(
        mcpConfiguration.getHttpPort(),
        mcpConfiguration.getHttpHost()
      );
      this.transportProvider = httpServerManager.getTransportProvider();
    } else {
      this.httpServerManager = null;
      this.transportProvider = new StdioServerTransportProvider(new ObjectMapper());
    }

    initializeServices();
  }

  private void initializeServices() {
    this.backendService = new BackendService(mcpConfiguration);
    this.httpClientProvider = new HttpClientProvider(mcpConfiguration.getUserAgent());
    this.serverApi = initializeServerApi(mcpConfiguration);
    this.sonarQubeVersionChecker = new SonarQubeVersionChecker(serverApi);
    var pluginsSynchronizer = new PluginsSynchronizer(serverApi, mcpConfiguration.getStoragePath());
    this.toolExecutor = new ToolExecutor(backendService);
    sonarQubeVersionChecker.failIfSonarQubeServerVersionIsNotSupported();
    var analyzers = pluginsSynchronizer.synchronizeAnalyzers();
    // Avoid logging before backend is initialized, so that logs are properly redirected
    backendService.initialize(analyzers);
  }

  private void initTools() {
    var sonarqubeIdeBridgeClient = initializeBridgeClient(mcpConfiguration);

    if (sonarqubeIdeBridgeClient.isAvailable()) {
      LOG.info("SonarQube for IDE integration detected - loading IDE specific tools");
      backendService.notifySonarQubeIdeIntegration();
      this.supportedTools.add(new AnalyzeFileListTool(sonarqubeIdeBridgeClient));
      this.supportedTools.add(new ToggleAutomaticAnalysisTool(sonarqubeIdeBridgeClient));
    } else {
      LOG.info("SonarQube for IDE integration not detected - loading standard analysis tool");
      this.supportedTools.add(new AnalysisTool(backendService, serverApi));
    }

    // SonarQube Cloud specific tools
    if (mcpConfiguration.isSonarCloud()) {
      LOG.info("SonarQube Cloud detected - loading SonarQube Cloud specific tools");
      this.supportedTools.add(new ListEnterprisesTool(serverApi));
    } else {
      LOG.info("SonarQube Server detected - loading SonarQube Server specific tools");
      // SonarQube Server specific tools
      this.supportedTools.addAll(List.of(
        new SystemHealthTool(serverApi),
        new SystemInfoTool(serverApi),
        new SystemLogsTool(serverApi),
        new SystemPingTool(serverApi),
        new SystemStatusTool(serverApi)));
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
      new GetComponentMeasuresTool(serverApi),
      new SearchMetricsTool(serverApi),
      new GetScmInfoTool(serverApi),
      new GetRawSourceTool(serverApi),
      new CreateWebhookTool(serverApi),
      new ListWebhooksTool(serverApi),
      new ListPortfoliosTool(serverApi),
      new SearchDependencyRisksTool(serverApi, sonarQubeVersionChecker)));
  }

  public void start() {
    initTools();

    if (httpServerManager != null) {
      LOG.info("Starting HTTP server on " + mcpConfiguration.getHttpHost() + ":" + mcpConfiguration.getHttpPort() + "...");
      httpServerManager.startServer().join();
      LOG.info("HTTP server started");
    }

    Function<Object, McpSyncServer> serverBuilder = provider -> {
      var builder = switch (provider) {
        case McpServerTransportProvider p -> McpServer.sync(p);
        case McpStreamableServerTransportProvider p -> McpServer.sync(p);
        default -> throw new IllegalArgumentException("Unsupported transport provider type: " + provider.getClass().getName());
      };
      return builder
        .serverInfo(new McpSchema.Implementation(SONARQUBE_MCP_SERVER_NAME, mcpConfiguration.getAppVersion()))
        .instructions("Transform your code quality workflow with SonarQube integration. " +
          "Analyze code, monitor project health, investigate issues, and understand quality gates.")
        .capabilities(McpSchema.ServerCapabilities.builder().tools(true).logging().build())
        .tools(supportedTools.stream().map(this::toSpec).toArray(McpServerFeatures.SyncToolSpecification[]::new))
        .build();
    };

    syncServer = serverBuilder.apply(transportProvider);

    Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

    logInitialization();
  }

  private McpServerFeatures.SyncToolSpecification toSpec(Tool tool) {
    return new McpServerFeatures.SyncToolSpecification.Builder()
      .tool(tool.definition())
      .callHandler((exchange, toolRequest) -> {
        logLogFileLocation(exchange);
        return toolExecutor.execute(tool, toolRequest);
      })
      .build();
  }

  private void logInitialization() {
    var transportType = mcpConfiguration.isHttpEnabled() ? "HTTP" : "stdio";
    var sonarQubeType = mcpConfiguration.isSonarCloud() ? "SonarQube Cloud" : "SonarQube Server";

    LOG.info("========================================");
    LOG.info("SonarQube MCP Server Startup Configuration:");
    LOG.info("Transport: " + transportType +
      (mcpConfiguration.isHttpEnabled() ? (" (" + mcpConfiguration.getHttpHost() + ":" + mcpConfiguration.getHttpPort() + ")") : ""));
    LOG.info("Instance: " + sonarQubeType);
    LOG.info("URL: " + mcpConfiguration.getSonarQubeUrl());
    if (mcpConfiguration.isSonarCloud() && mcpConfiguration.getSonarqubeOrg() != null) {
      LOG.info("Organization: " + mcpConfiguration.getSonarqubeOrg());
    }
    LOG.info("Tools loaded: " + supportedTools.size());
    LOG.info("========================================");
  }

  private void logLogFileLocation(McpSyncServerExchange exchange) {
    if (!logFileLocationLogged) {
      exchange.loggingNotification(new McpSchema.LoggingMessageNotification(McpSchema.LoggingLevel.INFO, SONARQUBE_MCP_SERVER_NAME,
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

    // Stop HTTP server if running
    if (httpServerManager != null) {
      try {
        LOG.info("Stopping HTTP server...");
        httpServerManager.stopServer().join();
        LOG.info("HTTP server stopped");
      } catch (Exception e) {
        LOG.error("Error shutting down HTTP server", e);
      }
    }

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

  // Constructor for testing - allows injecting custom transport provider
  public SonarQubeMcpServer(McpServerTransportProviderBase transportProvider, @Nullable HttpServerTransportProvider httpServerManager, Map<String,
    String> environment) {
    this.mcpConfiguration = new McpServerLaunchConfiguration(environment);
    this.transportProvider = transportProvider;
    this.httpServerManager = httpServerManager;
    initializeServices();
  }

  // Package-private getters for testing
  McpServerLaunchConfiguration getMcpConfiguration() {
    return mcpConfiguration;
  }

  public List<Tool> getSupportedTools() {
    return List.copyOf(supportedTools);
  }

}
