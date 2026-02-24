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

import com.google.common.annotations.VisibleForTesting;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpStatelessServerFeatures;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarqube.mcp.bridge.SonarQubeIdeBridgeClient;
import org.sonarsource.sonarqube.mcp.client.ProxiedServerConfigParser;
import org.sonarsource.sonarqube.mcp.client.ProxiedToolsLoader;
import org.sonarsource.sonarqube.mcp.client.TransportMode;
import org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfiguration;
import org.sonarsource.sonarqube.mcp.http.HttpClientProvider;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.plugins.PluginsSynchronizer;
import org.sonarsource.sonarqube.mcp.serverapi.EndpointParams;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.features.Feature;
import org.sonarsource.sonarqube.mcp.slcore.BackendService;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;
import org.sonarsource.sonarqube.mcp.tools.ToolExecutor;
import org.sonarsource.sonarqube.mcp.tools.analysis.AnalyzeCodeSnippetTool;
import org.sonarsource.sonarqube.mcp.tools.analysis.AnalyzeFileListTool;
import org.sonarsource.sonarqube.mcp.tools.analysis.RunAdvancedCodeAnalysisTool;
import org.sonarsource.sonarqube.mcp.tools.analysis.ToggleAutomaticAnalysisTool;
import org.sonarsource.sonarqube.mcp.tools.dependencyrisks.SearchDependencyRisksTool;
import org.sonarsource.sonarqube.mcp.tools.enterprises.ListEnterprisesTool;
import org.sonarsource.sonarqube.mcp.tools.hotspots.ChangeSecurityHotspotStatusTool;
import org.sonarsource.sonarqube.mcp.tools.hotspots.SearchSecurityHotspotsTool;
import org.sonarsource.sonarqube.mcp.tools.hotspots.ShowSecurityHotspotTool;
import org.sonarsource.sonarqube.mcp.tools.issues.ChangeIssueStatusTool;
import org.sonarsource.sonarqube.mcp.tools.issues.SearchIssuesTool;
import org.sonarsource.sonarqube.mcp.tools.languages.ListLanguagesTool;
import org.sonarsource.sonarqube.mcp.tools.measures.GetComponentMeasuresTool;
import org.sonarsource.sonarqube.mcp.tools.measures.SearchFilesByCoverageTool;
import org.sonarsource.sonarqube.mcp.tools.metrics.SearchMetricsTool;
import org.sonarsource.sonarqube.mcp.tools.portfolios.ListPortfoliosTool;
import org.sonarsource.sonarqube.mcp.tools.projects.SearchMyProjectsTool;
import org.sonarsource.sonarqube.mcp.tools.qualitygates.ListQualityGatesTool;
import org.sonarsource.sonarqube.mcp.tools.qualitygates.ProjectStatusTool;
import org.sonarsource.sonarqube.mcp.tools.rules.ShowRuleTool;
import org.sonarsource.sonarqube.mcp.tools.duplications.GetDuplicationsTool;
import org.sonarsource.sonarqube.mcp.tools.duplications.SearchDuplicatedFilesTool;
import org.sonarsource.sonarqube.mcp.tools.sources.GetFileCoverageDetailsTool;
import org.sonarsource.sonarqube.mcp.tools.sources.GetRawSourceTool;
import org.sonarsource.sonarqube.mcp.tools.sources.GetScmInfoTool;
import org.sonarsource.sonarqube.mcp.tools.system.SystemHealthTool;
import org.sonarsource.sonarqube.mcp.tools.system.SystemInfoTool;
import org.sonarsource.sonarqube.mcp.tools.system.SystemLogsTool;
import org.sonarsource.sonarqube.mcp.tools.system.SystemPingTool;
import org.sonarsource.sonarqube.mcp.tools.system.SystemStatusTool;
import org.sonarsource.sonarqube.mcp.tools.webhooks.CreateWebhookTool;
import org.sonarsource.sonarqube.mcp.tools.webhooks.ListWebhooksTool;
import org.sonarsource.sonarqube.mcp.tools.pullrequests.ListPullRequestsTool;
import org.sonarsource.sonarqube.mcp.transport.HttpServerTransportProvider;
import org.sonarsource.sonarqube.mcp.transport.StdioServerTransportProvider;

public class SonarQubeMcpServer implements ServerApiProvider {

  private static final McpLogger LOG = McpLogger.getInstance();
  private static final String SONARQUBE_MCP_SERVER_NAME = "sonarqube-mcp-server";
  private static final String BASE_INSTRUCTIONS_WITH_ANALYSIS = "Transform your code quality workflow with SonarQube integration. " +
    "Analyze code, monitor project health, investigate issues, and understand quality gates. " +
    "Note: Analyzers are being downloaded in the background and will be available shortly for code analysis.";
  private static final String BASE_INSTRUCTIONS_WITHOUT_ANALYSIS = "Transform your code quality workflow with SonarQube integration. " +
    "Monitor project health, investigate issues, and understand quality gates.";

  private BackendService backendService;
  private ToolExecutor toolExecutor;
  private final HttpServerTransportProvider httpServerManager;
  private final McpServerTransportProvider transportProvider;
  private final List<Tool> supportedTools = new ArrayList<>();
  private final McpServerLaunchConfiguration mcpConfiguration;
  private HttpClientProvider httpClientProvider;
  private String composedInstructions;

  /**
   * ServerApi instance.
   * - In stdio mode: created once at startup with the configured token
   * - In HTTP mode: null (created per-request using client's token from Authorization header)
   */
  @Nullable
  private ServerApi serverApi;
  private SonarQubeVersionChecker sonarQubeVersionChecker;
  @Nullable
  private McpStatelessSyncServer statelessSyncServer;
  @Nullable
  private McpSyncServer stdioSyncServer;
  /**
   * In HTTP stateless mode, carries the McpTransportContext for the current request thread
   * so that get() can extract the SONARQUBE_TOKEN and SONARQUBE_ORG headers.
   */
  private final ThreadLocal<McpTransportContext> currentTransportContext = new ThreadLocal<>();
  private volatile boolean isShutdown = false;
  private final CompletableFuture<Void> initializationFuture = new CompletableFuture<>();
  private ProxiedToolsLoader proxiedToolsLoader;

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
      this.transportProvider = null;
    } else {
      this.httpServerManager = null;
      this.transportProvider = new StdioServerTransportProvider(this::shutdown);
    }

    initializeBasicServicesAndTools();
  }

  public void start() {
    if (httpServerManager != null) {
      httpServerManager.startServer().join();
      statelessSyncServer = McpServer.sync(httpServerManager.getTransportProvider())
        .serverInfo(new McpSchema.Implementation(SONARQUBE_MCP_SERVER_NAME, mcpConfiguration.getAppVersion()))
        .instructions(composedInstructions)
        .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
        .tools(filterForEnabledTools(supportedTools).stream().map(this::toStatelessSpec).toArray(McpStatelessServerFeatures.SyncToolSpecification[]::new))
        .build();
    } else {
      stdioSyncServer = McpServer.sync(transportProvider)
        .serverInfo(new McpSchema.Implementation(SONARQUBE_MCP_SERVER_NAME, mcpConfiguration.getAppVersion()))
        .instructions(composedInstructions)
        .capabilities(McpSchema.ServerCapabilities.builder().tools(true).logging().build())
        .tools(filterForEnabledTools(supportedTools).stream().map(this::toStdioSpec).toArray(McpServerFeatures.SyncToolSpecification[]::new))
        .build();
    }

    Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

    // Start background initialization in a separate thread
    CompletableFuture.runAsync(this::initializeBackgroundServices)
      .exceptionally(ex -> {
        LOG.error("Fatal error during background initialization", ex);
        return null;
      });
  }

  /**
   * Initializes all services and loads all tools synchronously.
   * The backend is initialized immediately (without analyzers) so that tools can be registered.
   * Analyzers are downloaded in the background and the backend is restarted with them later.
   */
  private void initializeBasicServicesAndTools() {
    this.backendService = new BackendService(mcpConfiguration);
    this.httpClientProvider = new HttpClientProvider(mcpConfiguration.getUserAgent());
    this.toolExecutor = new ToolExecutor(backendService);

    // Create ServerApi and SonarQubeVersionChecker
    if (mcpConfiguration.isHttpEnabled()) {
      var initServerApi = createServerApiWithToken(mcpConfiguration.getSonarQubeToken());
      this.sonarQubeVersionChecker = new SonarQubeVersionChecker(initServerApi);
      loadBackendIndependentTools(initServerApi);
    } else {
      this.serverApi = initializeServerApi(mcpConfiguration);
      this.sonarQubeVersionChecker = new SonarQubeVersionChecker(serverApi);
      loadBackendIndependentTools(serverApi);
    }

    sonarQubeVersionChecker.failIfSonarQubeServerVersionIsNotSupported();

    // Initialize backend immediately with empty analyzers so we can check IDE bridge availability
    backendService.initialize(new BackendService.AnalyzersAndLanguagesEnabled(Set.of(), EnumSet.noneOf(Language.class)));
    backendService.notifyTransportModeUsed();

    // Initialize proxied MCP servers and load their tools synchronously
    loadProxiedServerTools();

    if (mcpConfiguration.isAdvancedAnalysisEnabled() && mcpConfiguration.isSonarCloud()) {
      LOG.info("Advanced analysis mode enabled");
      supportedTools.add(new RunAdvancedCodeAnalysisTool(this));
    } else {
      if (mcpConfiguration.isAdvancedAnalysisEnabled() && !mcpConfiguration.isSonarCloud()) {
        LOG.warn("SONARQUBE_ADVANCED_ANALYSIS_ENABLED is set but advanced analysis is only available on SonarCloud. Falling back to standard analysis.");
      }
      loadBackendDependentTools();
    }

    logToolsLoaded();
  }

  /**
   * Downloads analyzers in background and restarts the backend with them.
   * Tools are already loaded synchronously during startup.
   * Skips analyzer download if ANALYSIS tools are disabled or advanced analysis mode is enabled.
   */
  private void initializeBackgroundServices() {
    try {
      logInitialization();

      // Check if ANALYSIS tools are enabled before downloading analyzers
      if (!mcpConfiguration.isToolCategoryEnabled(ToolCategory.ANALYSIS)) {
        LOG.info("Analysis tools are disabled - skipping analyzers download");
        initializationFuture.complete(null);
        return;
      }

      // Skip analyzer download when advanced analysis mode is enabled (no local analysis)
      if (mcpConfiguration.isAdvancedAnalysisEnabled() && mcpConfiguration.isSonarCloud()) {
        LOG.info("Advanced analysis mode enabled - skipping analyzers download (no local analysis needed)");
        initializationFuture.complete(null);
        return;
      }

      PluginsSynchronizer pluginsSynchronizer;
      if (mcpConfiguration.isHttpEnabled()) {
        var initServerApi = createServerApiWithToken(mcpConfiguration.getSonarQubeToken());
        pluginsSynchronizer = new PluginsSynchronizer(initServerApi, mcpConfiguration.getStoragePath());
      } else {
        pluginsSynchronizer = new PluginsSynchronizer(Objects.requireNonNull(serverApi), mcpConfiguration.getStoragePath());
      }

      LOG.info("Downloading analyzers in background...");
      var analyzers = pluginsSynchronizer.synchronizeAnalyzers();

      // Restart backend with the downloaded analyzers
      LOG.info("Restarting backend with downloaded analyzers...");
      backendService.restartWithAnalyzers(analyzers);

      initializationFuture.complete(null);
      LOG.info("Background initialization completed successfully - analyzers are now available");
    } catch (Exception e) {
      LOG.error("Background initialization failed", e);
      initializationFuture.completeExceptionally(e);
      throw e;
    }
  }

  /**
   * Loads tools that DON'T depend on the backend service.
   * These can be loaded BEFORE plugin synchronization (which is slow).
   * This makes most tools available to users within seconds instead of minutes.
   */
  private void loadBackendIndependentTools(ServerApi serverApi) {
    if (mcpConfiguration.isSonarCloud()) {
      supportedTools.add(new ListEnterprisesTool(this));
    } else {
      supportedTools.addAll(List.of(
        new SystemHealthTool(this),
        new SystemInfoTool(this),
        new SystemLogsTool(this),
        new SystemPingTool(this),
        new SystemStatusTool(this)));
    }

    supportedTools.addAll(List.of(
      new ChangeIssueStatusTool(this),
      new SearchMyProjectsTool(this, mcpConfiguration.isSonarCloud()),
      new SearchIssuesTool(this, mcpConfiguration.isSonarCloud()),
      new SearchSecurityHotspotsTool(this),
      new ShowSecurityHotspotTool(this),
      new ChangeSecurityHotspotStatusTool(this),
      new ProjectStatusTool(this),
      new ShowRuleTool(this),
      new ListQualityGatesTool(this),
      new ListLanguagesTool(this),
      new GetComponentMeasuresTool(this),
      new SearchFilesByCoverageTool(this),
      new GetFileCoverageDetailsTool(this),
      new SearchMetricsTool(this),
      new GetScmInfoTool(this),
      new GetRawSourceTool(this),
      new CreateWebhookTool(this, mcpConfiguration.isSonarCloud()),
      new ListWebhooksTool(this, mcpConfiguration.isSonarCloud()),
      new GetDuplicationsTool(this),
      new SearchDuplicatedFilesTool(this),
      new ListPortfoliosTool(this, mcpConfiguration.isSonarCloud()),
      new ListPullRequestsTool(this)));

    var scaSupportedOnSQC = serverApi.isSonarQubeCloud() && serverApi.scaApi().isScaEnabled();
    var scaSupportedOnSQS = !serverApi.isSonarQubeCloud() && serverApi.featuresApi().listFeatures().contains(Feature.SCA);
    if (scaSupportedOnSQC || scaSupportedOnSQS) {
      supportedTools.add(new SearchDependencyRisksTool(this, sonarQubeVersionChecker));
    }
  }

  /**
   * Loads tools that depend on the backend service or IDE bridge.
   * This is called during startup after the backend is initialized (with empty analyzers).
   * The IDE bridge availability check is done here so that analysis tools can be registered.
   */
  private void loadBackendDependentTools() {
    boolean useIdeBridge = false;
    if (!mcpConfiguration.isHttpEnabled() && mcpConfiguration.getSonarQubeIdePort() != null) {
      var sonarqubeIdeBridgeClient = initializeBridgeClient(mcpConfiguration);
      if (sonarqubeIdeBridgeClient.isAvailable()) {
        LOG.info("SonarQube for IDE integration detected");
        backendService.notifySonarQubeIdeIntegration();
        supportedTools.add(new AnalyzeFileListTool(sonarqubeIdeBridgeClient));
        supportedTools.add(new ToggleAutomaticAnalysisTool(sonarqubeIdeBridgeClient));
        useIdeBridge = true;
      }
    }
    if (!useIdeBridge) {
      LOG.info("Standard analysis mode (no IDE bridge)");
      supportedTools.add(new AnalyzeCodeSnippetTool(backendService, this, initializationFuture));
    }
  }

  private void logToolsLoaded() {
    var filterReason = mcpConfiguration.isReadOnlyMode() ? "category and read-only filtering" : "category filtering";
    LOG.info("All tools loaded: " + this.supportedTools.size() + " tools after " + filterReason);
  }

  private void loadProxiedServerTools() {
    proxiedToolsLoader = new ProxiedToolsLoader();
    var currentTransportMode = mcpConfiguration.isHttpEnabled() ? TransportMode.HTTP : TransportMode.STDIO;
    var proxiedTools = proxiedToolsLoader.loadProxiedTools(currentTransportMode);
    supportedTools.addAll(proxiedTools);
    var filterReason = mcpConfiguration.isReadOnlyMode() ? "category and read-only filtering" : "category filtering";
    LOG.info("All tools loaded: " + this.supportedTools.size() + " tools after " + filterReason);

    // Select base instructions based on whether ANALYSIS tools are enabled
    var baseInstructions = mcpConfiguration.isToolCategoryEnabled(ToolCategory.ANALYSIS)
      ? BASE_INSTRUCTIONS_WITH_ANALYSIS
      : BASE_INSTRUCTIONS_WITHOUT_ANALYSIS;

    // Compose instructions with external provider contributions
    var parseResult = ProxiedServerConfigParser.parse();
    if (parseResult.success() && !parseResult.configs().isEmpty()) {
      composedInstructions = ProxiedToolsLoader.composeInstructions(baseInstructions, parseResult.configs());
    } else {
      composedInstructions = baseInstructions;
    }
  }

  private List<Tool> filterForEnabledTools(List<Tool> toolsToFilter) {
    return toolsToFilter.stream()
      .filter(tool -> mcpConfiguration.isToolCategoryEnabled(tool.getCategory()))
      .filter(tool -> !mcpConfiguration.isReadOnlyMode() || tool.definition().annotations().readOnlyHint())
      .toList();
  }

  private McpStatelessServerFeatures.SyncToolSpecification toStatelessSpec(Tool tool) {
    return new McpStatelessServerFeatures.SyncToolSpecification.Builder()
      .tool(tool.definition())
      .callHandler((transportContext, toolRequest) -> {
        currentTransportContext.set(transportContext);
        try {
          return toolExecutor.execute(tool, toolRequest);
        } finally {
          currentTransportContext.remove();
        }
      })
      .build();
  }

  private McpServerFeatures.SyncToolSpecification toStdioSpec(Tool tool) {
    return new McpServerFeatures.SyncToolSpecification.Builder()
      .tool(tool.definition())
      .callHandler((exchange, toolRequest) -> toolExecutor.execute(tool, toolRequest))
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

    logDebugDetails();
  }

  private void logDebugDetails() {
    LOG.debug("=== Debug Level Configuration Details ===");
    httpClientProvider.logConnectionSettings();
    LOG.debug("Enabled toolsets: " + mcpConfiguration.getEnabledToolsets());
    LOG.debug("Advanced analysis: " + mcpConfiguration.isAdvancedAnalysisEnabled());
    LOG.debug("Telemetry enabled: " + mcpConfiguration.isTelemetryEnabled());
    LOG.debug("App version: " + mcpConfiguration.getAppVersion());
    LOG.debug("Storage path: " + mcpConfiguration.getStoragePath());
    LOG.debug("Log file: " + mcpConfiguration.getLogFilePath().toAbsolutePath());
    LOG.debug("IDE port: " + (mcpConfiguration.getSonarQubeIdePort() != null ? mcpConfiguration.getSonarQubeIdePort() : "not set"));
    LOG.debug("================================");
  }

  /**
   * Get ServerApi instance for the current request context.
   * - In HTTP stateless mode: Creates a new ServerApi per tool call using the token and org
   *   extracted from the HTTP request headers via McpTransportContext.
   * - In stdio mode: Returns the global ServerApi instance created at startup.
   */
  @Override
  public ServerApi get() {
    if (mcpConfiguration.isHttpEnabled()) {
      var ctx = currentTransportContext.get();
      if (ctx == null) {
        throw new IllegalStateException("No transport context available for HTTP stateless mode");
      }
      var token = (String) ctx.get(HttpServerTransportProvider.CONTEXT_TOKEN_KEY);
      if (token == null || token.isBlank()) {
        throw new IllegalStateException("No SONARQUBE_TOKEN in transport context");
      }
      return createServerApiWithTokenAndOrg(token, mcpConfiguration.getSonarqubeOrg());
    } else {
      return Objects.requireNonNull(serverApi, "ServerApi not initialized");
    }
  }

  private ServerApi initializeServerApi(McpServerLaunchConfiguration mcpConfiguration) {
    var token = mcpConfiguration.getSonarQubeToken();
    return createServerApiWithToken(token);
  }
  
  private ServerApi createServerApiWithToken(@Nullable String token) {
    return createServerApiWithTokenAndOrg(token, mcpConfiguration.getSonarqubeOrg());
  }

  private ServerApi createServerApiWithTokenAndOrg(@Nullable String token, @Nullable String organization) {
    var url = mcpConfiguration.getSonarQubeUrl();
    var httpClient = token != null ? httpClientProvider.getHttpClient(token) : httpClientProvider.getAnonymousHttpClient();
    var isSonarCloud = mcpConfiguration.isSonarCloud() || organization != null;
    var serverApiHelper = new ServerApiHelper(new EndpointParams(url, organization), httpClient);
    return new ServerApi(serverApiHelper, isSonarCloud);
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

    awaitBackgroundInitialization();
    shutdownProxiedServers();
    shutdownHttpServer();
    shutdownHttpClient();
    shutdownMcpServer();
    shutdownBackend();
  }

  private void shutdownProxiedServers() {
    if (proxiedToolsLoader != null) {
      proxiedToolsLoader.shutdown();
    }
  }

  private void awaitBackgroundInitialization() {
    if (initializationFuture.isDone()) {
      return;
    }
    LOG.info("Waiting for background initialization to complete before shutdown...");
    try {
      initializationFuture.get(30, TimeUnit.SECONDS);
    } catch (TimeoutException | ExecutionException e) {
      LOG.warn("Background initialization did not complete within 30 seconds, proceeding with shutdown");
      initializationFuture.cancel(true);
    } catch (Exception e) {
      LOG.error("Background initialization failed or was interrupted", e);
    }
  }

  private void shutdownHttpServer() {
    if (httpServerManager == null) {
      return;
    }
    try {
      LOG.info("Stopping HTTP server...");
      httpServerManager.stopServer().join();
      LOG.info("HTTP server stopped");
    } catch (Exception e) {
      LOG.error("Error shutting down HTTP server", e);
    }
  }

  private void shutdownHttpClient() {
    if (httpClientProvider == null) {
      return;
    }
    try {
      httpClientProvider.shutdown();
    } catch (Exception e) {
      LOG.error("Error shutting down HTTP client", e);
    }
  }

  private void shutdownMcpServer() {
    try {
      if (statelessSyncServer != null) {
        statelessSyncServer.closeGracefully().block();
      }
      if (stdioSyncServer != null) {
        stdioSyncServer.closeGracefully();
      }
    } catch (Exception e) {
      LOG.error("Error shutting down MCP server", e);
    }
  }

  private void shutdownBackend() {
    try {
      backendService.shutdown();
    } catch (Exception e) {
      LOG.error("Error shutting down MCP backend", e);
    }
  }

  // Constructor for testing - allows injecting custom transport provider
  public SonarQubeMcpServer(McpServerTransportProvider transportProvider, @Nullable HttpServerTransportProvider httpServerManager, Map<String,
    String> environment) {
    this.mcpConfiguration = new McpServerLaunchConfiguration(environment);
    this.transportProvider = transportProvider;
    this.httpServerManager = httpServerManager;
    initializeBasicServicesAndTools();
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


  @VisibleForTesting
  public void withTransportContext(McpTransportContext context, Runnable action) {
    currentTransportContext.set(context);
    try {
      action.run();
    } finally {
      currentTransportContext.remove();
    }
  }

}
