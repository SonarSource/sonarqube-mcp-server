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
package org.sonarsource.sonarqube.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.bridge.SonarQubeIdeBridgeClient;
import org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfiguration;
import org.sonarsource.sonarqube.mcp.context.RequestContext;
import org.sonarsource.sonarqube.mcp.http.HttpClientProvider;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.plugins.PluginsSynchronizer;
import org.sonarsource.sonarqube.mcp.serverapi.EndpointParams;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
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

public class SonarQubeMcpServer implements ServerApiProvider {

  private static final McpLogger LOG = McpLogger.getInstance();
  private static final String SONARQUBE_MCP_SERVER_NAME = "sonarqube-mcp-server";

  private BackendService backendService;
  private ToolExecutor toolExecutor;
  private final HttpServerTransportProvider httpServerManager;
  private final McpServerTransportProviderBase transportProvider;
  private final List<Tool> supportedTools = new ArrayList<>();
  private final McpServerLaunchConfiguration mcpConfiguration;
  private HttpClientProvider httpClientProvider;
  
  /**
   * ServerApi instance.
   * - In stdio mode: created once at startup with the configured token
   * - In HTTP mode: null (created per-request using client's token from Authorization header)
   */
  @Nullable
  private ServerApi serverApi;
  
  private SonarQubeVersionChecker sonarQubeVersionChecker;
  private McpSyncServer syncServer;
  private volatile boolean isShutdown = false;
  private boolean logFileLocationLogged;
  private final CompletableFuture<Void> initializationFuture = new CompletableFuture<>();

  public static void main(String[] args) {
    new SonarQubeMcpServer(System.getenv()).start();
  }

  public SonarQubeMcpServer(Map<String, String> environment) {
    this.mcpConfiguration = new McpServerLaunchConfiguration(environment);
    var authConfig = mcpConfiguration.getAuthMode();

    if (mcpConfiguration.isHttpEnabled() && authConfig != null) {
      this.httpServerManager = new HttpServerTransportProvider(
        mcpConfiguration.getHttpPort(),
        mcpConfiguration.getHttpHost(),
        authConfig,
        mcpConfiguration.isHttpsEnabled(),
        mcpConfiguration.getHttpsKeystorePath(),
        mcpConfiguration.getHttpsKeystorePassword(),
        mcpConfiguration.getHttpsKeystoreType(),
        mcpConfiguration.getHttpsTruststorePath(),
        mcpConfiguration.getHttpsTruststorePassword(),
        mcpConfiguration.getHttpsTruststoreType()
      );
      this.transportProvider = httpServerManager.getTransportProvider();
    } else {
      this.httpServerManager = null;
      this.transportProvider = new StdioServerTransportProvider(new ObjectMapper());
    }

    initializeBasicServices();
  }



  public void start() {
    // Start HTTP server if enabled
    if (httpServerManager != null) {
      LOG.info("Starting HTTP server on " + mcpConfiguration.getHttpHost() + ":" + mcpConfiguration.getHttpPort() + "...");
      httpServerManager.startServer().join();
      LOG.info("HTTP server started");
    }

    // Build and start MCP server immediately with NO tools
    // Tools will be added dynamically once background initialization completes
    Function<Object, McpSyncServer> serverBuilder = provider -> {
      var builder = switch (provider) {
        case McpServerTransportProvider p -> McpServer.sync(p);
        case McpStreamableServerTransportProvider p -> McpServer.sync(p);
        default -> throw new IllegalArgumentException("Unsupported transport provider type: " + provider.getClass().getName());
      };
      return builder
        .serverInfo(new McpSchema.Implementation(SONARQUBE_MCP_SERVER_NAME, mcpConfiguration.getAppVersion()))
        .instructions("Transform your code quality workflow with SonarQube integration. " +
          "Analyze code, monitor project health, investigate issues, and understand quality gates. " +
          "Note: Tools are being loaded in the background and will be available shortly.")
        .capabilities(McpSchema.ServerCapabilities.builder().tools(true).logging().build())
        // Start with no tools - they will be added dynamically after initialization
        .build();
    };

    syncServer = serverBuilder.apply(transportProvider);

    Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

    logInitialization();

    // Start background initialization in a separate thread
    CompletableFuture.runAsync(this::initializeBackgroundServices)
      .exceptionally(ex -> {
        LOG.error("Fatal error during background initialization", ex);
        return null;
      });
  }

  /**
   * Quick initialization - only creates basic services needed for configuration validation.
   * Heavy operations (version check, plugin download, backend init) are deferred to background.
   */
  private void initializeBasicServices() {
    this.backendService = new BackendService(mcpConfiguration);
    this.httpClientProvider = new HttpClientProvider(mcpConfiguration.getUserAgent());
    this.toolExecutor = new ToolExecutor(backendService, initializationFuture);

    // Create ServerApi and SonarQubeVersionChecker early (doesn't make network calls yet)
    // This allows initTools() to reference sonarQubeVersionChecker before background init completes
    if (mcpConfiguration.isHttpEnabled()) {
      var initServerApi = createServerApiWithToken(mcpConfiguration.getSonarQubeToken());
      this.sonarQubeVersionChecker = new SonarQubeVersionChecker(initServerApi);
    } else {
      this.serverApi = initializeServerApi(mcpConfiguration);
      this.sonarQubeVersionChecker = new SonarQubeVersionChecker(serverApi);
    }
  }

  /**
   * Heavy initialization that runs in background after the server has started.
   * Downloads analyzers, checks version, initializes the backend, and dynamically loads tools.
   */
  private void initializeBackgroundServices() {
    try {
      sonarQubeVersionChecker.failIfSonarQubeServerVersionIsNotSupported();

      PluginsSynchronizer pluginsSynchronizer;
      if (mcpConfiguration.isHttpEnabled()) {
        var initServerApi = createServerApiWithToken(mcpConfiguration.getSonarQubeToken());
        pluginsSynchronizer = new PluginsSynchronizer(initServerApi, mcpConfiguration.getStoragePath());
      } else {
        pluginsSynchronizer = new PluginsSynchronizer(Objects.requireNonNull(serverApi), mcpConfiguration.getStoragePath());
      }
      var analyzers = pluginsSynchronizer.synchronizeAnalyzers();

      backendService.initialize(analyzers);
      backendService.notifyTransportModeUsed();

      // Load and register tools dynamically
      LOG.info("Loading tools...");
      initTools();
      notifyToolsLoaded();

      initializationFuture.complete(null);
      LOG.info("Background initialization completed successfully");
    } catch (Exception e) {
      LOG.error("Background initialization failed", e);
      initializationFuture.completeExceptionally(e);
      throw e;
    }
  }

  /**
   * Notifies connected clients about newly loaded tools.
   * This may fail if no client is connected yet (common during startup or in tests).
   * Failure is acceptable as clients will discover tools via listTools() when they connect.
   */
  private void notifyToolsLoaded() {
    try {
      for (var tool : supportedTools) {
        syncServer.addTool(toSpec(tool));
      }
      syncServer.notifyToolsListChanged();
      LOG.info(supportedTools.size() + " tools loaded and registered");
    } catch (Exception e) {
      LOG.info(supportedTools.size() + " tools loaded (client notification deferred)");
    }
  }

  private void initTools() {
    var allTools = new ArrayList<Tool>();
    
    boolean useIdeBridge = false;
    if (!mcpConfiguration.isHttpEnabled() && mcpConfiguration.getSonarQubeIdePort() != null) {
      var sonarqubeIdeBridgeClient = initializeBridgeClient(mcpConfiguration);
      if (sonarqubeIdeBridgeClient.isAvailable()) {
        LOG.info("SonarQube for IDE integration detected - loading IDE specific tools");
        backendService.notifySonarQubeIdeIntegration();
        allTools.add(new AnalyzeFileListTool(sonarqubeIdeBridgeClient));
        allTools.add(new ToggleAutomaticAnalysisTool(sonarqubeIdeBridgeClient));
        useIdeBridge = true;
      }
    }

    // Load standard analysis tool when IDE bridge is not used
    if (!useIdeBridge) {
      LOG.info("SonarQube for IDE integration not detected - loading standard analysis tool");
      allTools.add(new AnalysisTool(backendService, this));
    }

    // SonarQube Cloud specific tools
    if (mcpConfiguration.isSonarCloud()) {
      LOG.info("SonarQube Cloud detected - loading SonarQube Cloud specific tools");
      allTools.add(new ListEnterprisesTool(this));
    } else {
      LOG.info("SonarQube Server detected - loading SonarQube Server specific tools");
      // SonarQube Server specific tools
      allTools.addAll(List.of(
        new SystemHealthTool(this),
        new SystemInfoTool(this),
        new SystemLogsTool(this),
        new SystemPingTool(this),
        new SystemStatusTool(this)));
    }

    allTools.addAll(List.of(
      new ChangeIssueStatusTool(this),
      new SearchMyProjectsTool(this),
      new SearchIssuesTool(this),
      new ProjectStatusTool(this),
      new ShowRuleTool(this),
      new ListRuleRepositoriesTool(this),
      new ListQualityGatesTool(this),
      new ListLanguagesTool(this),
      new GetComponentMeasuresTool(this),
      new SearchMetricsTool(this),
      new GetScmInfoTool(this),
      new GetRawSourceTool(this),
      new CreateWebhookTool(this),
      new ListWebhooksTool(this),
      new ListPortfoliosTool(this, mcpConfiguration.isSonarCloud()),
      new SearchDependencyRisksTool(this, sonarQubeVersionChecker)));
      
    // Filter tools based on enabled categories and read-only mode
    this.supportedTools.addAll(allTools.stream()
      .filter(tool -> mcpConfiguration.isToolCategoryEnabled(tool.getCategory()))
      .filter(tool -> !mcpConfiguration.isReadOnlyMode() || tool.definition().annotations().readOnlyHint())
      .toList());
      
    var filterReason = mcpConfiguration.isReadOnlyMode() ? "category and read-only filtering" : "category filtering";
    LOG.info("Loaded " + this.supportedTools.size() + " tools after " + filterReason);
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
    LOG.info("SonarQube MCP Server Started:");
    LOG.info("Transport: " + transportType +
      (mcpConfiguration.isHttpEnabled() ? (" (" + mcpConfiguration.getHttpHost() + ":" + mcpConfiguration.getHttpPort() + ")") : ""));
    LOG.info("Instance: " + sonarQubeType);
    LOG.info("URL: " + mcpConfiguration.getSonarQubeUrl());
    if (mcpConfiguration.isSonarCloud() && mcpConfiguration.getSonarqubeOrg() != null) {
      LOG.info("Organization: " + mcpConfiguration.getSonarqubeOrg());
    }
    if (mcpConfiguration.isReadOnlyMode()) {
      LOG.info("Mode: READ-ONLY (write operations disabled)");
    }
    LOG.info("Status: Server ready - tools loading in background");
    LOG.info("========================================");
  }

  private void logLogFileLocation(McpSyncServerExchange exchange) {
    if (!logFileLocationLogged) {
      exchange.loggingNotification(new McpSchema.LoggingMessageNotification(McpSchema.LoggingLevel.INFO, SONARQUBE_MCP_SERVER_NAME,
        "Logs are redirected to " + mcpConfiguration.getLogFilePath().toAbsolutePath()));
      logFileLocationLogged = true;
    }
  }

  /**
   * Get ServerApi instance for the current request context.
   * - In stdio mode: Returns the global ServerApi instance created at startup
   * - In HTTP mode: Creates a new ServerApi instance using the token from RequestContext
   */
  @Override
  public ServerApi get() {
    if (mcpConfiguration.isHttpEnabled()) {
      return createServerApiWithToken(RequestContext.current().sonarQubeToken());
    } else {
      if (serverApi == null) {
        throw new IllegalStateException("ServerApi not initialized");
      }
      return serverApi;
    }
  }
  
  private ServerApi initializeServerApi(McpServerLaunchConfiguration mcpConfiguration) {
    var token = mcpConfiguration.getSonarQubeToken();
    return createServerApiWithToken(token);
  }
  
  private ServerApi createServerApiWithToken(String token) {
    var organization = mcpConfiguration.getSonarqubeOrg();
    var url = mcpConfiguration.getSonarQubeUrl();
    var httpClient = httpClientProvider.getHttpClient(token);
    var serverApiHelper = new ServerApiHelper(new EndpointParams(url, organization), httpClient);
    return new ServerApi(serverApiHelper, mcpConfiguration.isSonarCloud());
  }

  private SonarQubeIdeBridgeClient initializeBridgeClient(McpServerLaunchConfiguration mcpConfiguration) {
    LOG.info("Initializing SonarQube for IDE bridge client...");
    var host = mcpConfiguration.getHostMachineAddress();
    var port = mcpConfiguration.getSonarQubeIdePort();
    var bridgeUrl = "http://" + host + ":" + port;
    LOG.info("Bridge URL: " + bridgeUrl);
    var httpClient = httpClientProvider.getHttpClientForBridge();
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
    initializeBasicServices();
  }

  // Package-private getters for testing
  McpServerLaunchConfiguration getMcpConfiguration() {
    return mcpConfiguration;
  }

  public List<Tool> getSupportedTools() {
    return List.copyOf(supportedTools);
  }

  /**
   * For testing: wait for background initialization to complete.
   * This ensures tools are fully loaded before tests proceed.
   */
  @VisibleForTesting
  public void waitForInitialization() throws ExecutionException, InterruptedException {
    initializationFuture.get();
  }

}
