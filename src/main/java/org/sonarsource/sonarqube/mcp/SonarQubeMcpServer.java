/*
 * SonarQube MCP Server
 * Copyright (C) SonarSource
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
import io.modelcontextprotocol.server.McpSyncServerExchange;
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
import jakarta.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarqube.mcp.analytics.AnalyticsClient;
import org.sonarsource.sonarqube.mcp.analytics.AnalyticsService;
import org.sonarsource.sonarqube.mcp.analytics.ConnectionContext;
import org.sonarsource.sonarqube.mcp.bridge.SonarQubeIdeBridgeClient;
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
  private static final String PROJECT_KEY_INSTRUCTIONS = """
    ## Project Key Resolution
    Always resolve the project key using the following lookup order:
    1. Check for a `.sonarlint/connectedMode.json` file in the workspace root or any parent directory - use the `projectKey` field.
    2. Search for `sonar.projectKey` in project config files at the root folder: `sonar-project.properties`, `pom.xml`, `build.gradle`, `build.gradle.kts`, `package.json`.
    3. Search for `sonar.projectKey` in CI/CD pipeline files: `.github/workflows/*.yml`, `Jenkinsfile`, `.gitlab-ci.yml`, `azure-pipelines.yml`, `.circleci/config.yml`.
    4. When a user mentions a project by name, use `search_my_sonarqube_projects` to find the exact key.
    5. If no key is found, use `search_my_sonarqube_projects` to list projects.
    An incorrect project key will silently return results from the wrong project.
    """;
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
  @Nullable
  private AnalyticsService analyticsService;
  private final ConnectionContext connectionContext = ConnectionContext.empty();

  /**
   * ServerApi instance used for startup probing (version check, SCA availability, plugin sync).
   * - In stdio mode: created once at startup with the configured token; also used for all tool calls.
   * - In HTTP mode: created at startup only when a startup token is configured (SONARQUBE_TOKEN env var).
   *   Per-request tool calls always use a fresh ServerApi built from the token in the request's Authorization: Bearer header.
   */
  @Nullable
  private ServerApi serverApi;
  private SonarQubeVersionChecker sonarQubeVersionChecker;
  @Nullable
  private McpStatelessSyncServer statelessSyncServer;
  @Nullable
  private McpSyncServer stdioSyncServer;
  /**
   * In HTTP stateless mode, carries the McpTransportContext for the current request thread so that get() can extract the bearer token value.
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
        mcpConfiguration.isSonarQubeCloud(),
        mcpConfiguration.getSonarqubeOrg(),
        mcpConfiguration.isHttpsEnabled(),
        mcpConfiguration.getHttpsKeystorePath(),
        mcpConfiguration.getHttpsKeystorePassword(),
        mcpConfiguration.getHttpsKeystoreType(),
        mcpConfiguration.getHttpsTruststorePath(),
        mcpConfiguration.getHttpsTruststorePassword(),
        mcpConfiguration.getHttpsTruststoreType(),
        mcpConfiguration.getHttpAllowedOrigins(),
        mcpConfiguration.getAppVersion(),
        mcpConfiguration.isRunningInContainer()
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
      var enabledTools = filterForEnabledTools(supportedTools);
      statelessSyncServer = McpServer.sync(httpServerManager.getFilteringTransport(enabledTools))
        .serverInfo(new McpSchema.Implementation(SONARQUBE_MCP_SERVER_NAME, mcpConfiguration.getAppVersion()))
        .instructions(composedInstructions)
        .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
        .tools(enabledTools.stream().map(this::toStatelessSpec).toArray(McpStatelessServerFeatures.SyncToolSpecification[]::new))
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

    if (mcpConfiguration.isTelemetryEnabled()) {
      var analyticsHttpClient = httpClientProvider.getHttpClientForAnalytics(AnalyticsClient.resolveApiKey());
      var analyticsClient = new AnalyticsClient(analyticsHttpClient);
      this.analyticsService = new AnalyticsService(analyticsClient, mcpConfiguration.getMcpServerId(),
        mcpConfiguration.getAppVersion(), mcpConfiguration.isHttpEnabled(), mcpConfiguration.isHttpsEnabled(), mcpConfiguration.isSonarQubeCloud());
    }

    // In stdio mode: pass the shared pre-resolved ConnectionContext
    // In HTTP mode: pass a supplier that captures the request-scoped ServerApi synchronously to the async analytics task
    this.toolExecutor = mcpConfiguration.isHttpEnabled()
      ? new ToolExecutor(backendService, analyticsService, null, this, mcpConfiguration.getMcpServerId())
      : new ToolExecutor(backendService, analyticsService, connectionContext, null, mcpConfiguration.getMcpServerId());

    // Create ServerApi for startup probing (version check, SCA availability, plugin sync).
    // In HTTP mode this is optional — only created when a startup token is configured.
    // Per-request tool calls in HTTP mode always use a fresh ServerApi from the request headers.
    this.serverApi = initializeServerApi(mcpConfiguration);
    this.sonarQubeVersionChecker = new SonarQubeVersionChecker(serverApi);
    loadBackendIndependentTools(serverApi);

    sonarQubeVersionChecker.failIfSonarQubeServerVersionIsNotSupported();

    // Initialize backend immediately with empty analyzers so we can check IDE bridge availability
    backendService.initialize(new BackendService.AnalyzersAndLanguagesEnabled(Set.of(), EnumSet.noneOf(Language.class)));
    backendService.notifyTransportModeUsed();

    setBaseInstructions();

    // Initialize proxied MCP servers and load their tools synchronously
    // Only when:
    // 1. Running in stdio mode (CAG not supported in HTTP mode)
    // 2. CAG toolset is enabled
    // 3. Organization has CAG entitlement
    if (mcpConfiguration.isHttpEnabled()) {
      LOG.debug("HTTP mode detected - skipping CAG proxied server initialization (not supported in HTTP transport)");
    } else if (!mcpConfiguration.isToolCategoryEnabled(ToolCategory.CAG)) {
      LOG.debug("CAG toolset is not enabled, skipping proxied server initialization");
    } else if (isCagEnabledForOrg(serverApi, mcpConfiguration.getSonarqubeOrg())) {
      LOG.info("CAG is enabled for organization");
      loadProxiedServerTools();
    } else {
      LOG.debug("CAG is not enabled for organization, skipping proxied server initialization");
    }

    var workspaceMount = mcpConfiguration.getWorkspacePath();

    if (!mcpConfiguration.isHttpEnabled() && isAdvancedAnalysisEnabledForOrg(serverApi, mcpConfiguration.getSonarqubeOrg())) {
      if (workspaceMount != null) {
        LOG.info("Advanced analysis mode enabled");
        supportedTools.add(new RunAdvancedCodeAnalysisTool(this, mcpConfiguration.getProjectKey(), workspaceMount));
      } else {
        LOG.info("Advanced analysis mode enabled, but no workspace path configured, skipping tool registration");
      }
    } else {
      // In HTTP mode, analysis tools requiring local analyzers are only enabled when a startup
      // token is configured (so plugins can be downloaded at startup).
      if (!mcpConfiguration.isHttpEnabled() || mcpConfiguration.getSonarQubeToken() != null) {
        loadBackendDependentTools();
      }
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

      // In stdio mode the token is fixed
      if (mcpConfiguration.isTelemetryEnabled() && !mcpConfiguration.isHttpEnabled() && serverApi != null) {
        connectionContext.resolveFrom(serverApi);
      }

      // Check if ANALYSIS tools are enabled before downloading analyzers
      if (!mcpConfiguration.isToolCategoryEnabled(ToolCategory.ANALYSIS)) {
        LOG.info("Analysis tools are disabled - skipping analyzers download");
        initializationFuture.complete(null);
        return;
      }

      // In HTTP mode without a startup token, plugins cannot be downloaded.
      if (mcpConfiguration.isHttpEnabled() && mcpConfiguration.getSonarQubeToken() == null) {
        LOG.info("HTTP mode without startup token - skipping analyzers download (set SONARQUBE_TOKEN at server level to enable local analysis in HTTP mode)");
        initializationFuture.complete(null);
        return;
      }

      // Skip analyzer download when the local analysis tool is not present
      if (supportedTools.stream().noneMatch(AnalyzeCodeSnippetTool.class::isInstance)) {
        LOG.info("Local analysis tool is not present - skipping analyzers download");
        initializationFuture.complete(null);
        return;
      }

      var pluginsSynchronizer = new PluginsSynchronizer(Objects.requireNonNull(serverApi), mcpConfiguration.getStoragePath());

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
    if (mcpConfiguration.isSonarQubeCloud()) {
      supportedTools.add(new ListEnterprisesTool(this));
    } else {
      supportedTools.addAll(List.of(
        new SystemHealthTool(this),
        new SystemInfoTool(this),
        new SystemLogsTool(this),
        new SystemPingTool(this),
        new SystemStatusTool(this)));
    }

    var configuredProjectKey = mcpConfiguration.getProjectKey();

    supportedTools.addAll(List.of(
      new ChangeIssueStatusTool(this),
      new SearchMyProjectsTool(this, mcpConfiguration.isSonarQubeCloud()),
      new SearchIssuesTool(this, mcpConfiguration.isSonarQubeCloud()),
      new SearchSecurityHotspotsTool(this),
      new ShowSecurityHotspotTool(this),
      new ChangeSecurityHotspotStatusTool(this),
      new ProjectStatusTool(this),
      new ShowRuleTool(this),
      new ListQualityGatesTool(this),
      new ListLanguagesTool(this),
      new GetComponentMeasuresTool(this),
      new SearchFilesByCoverageTool(this, configuredProjectKey),
      new GetFileCoverageDetailsTool(this),
      new SearchMetricsTool(this),
      new GetScmInfoTool(this),
      new GetRawSourceTool(this),
      new CreateWebhookTool(this, mcpConfiguration.isSonarQubeCloud()),
      new ListWebhooksTool(this, mcpConfiguration.isSonarQubeCloud()),
      new GetDuplicationsTool(this),
      new SearchDuplicatedFilesTool(this, configuredProjectKey),
      new ListPortfoliosTool(this, mcpConfiguration.isSonarQubeCloud()),
      new ListPullRequestsTool(this, configuredProjectKey)));

    if (mcpConfiguration.isHttpEnabled()) {
      // In HTTP mode there is no startup token to probe SCA availability
      supportedTools.add(new SearchDependencyRisksTool(this, sonarQubeVersionChecker, configuredProjectKey));
    } else {
      var scaSupportedOnSQC = serverApi.isSonarQubeCloud() && serverApi.scaApi().isScaEnabled();
      var scaSupportedOnSQS = !serverApi.isSonarQubeCloud() && serverApi.featuresApi().listFeatures().contains(Feature.SCA);
      if (scaSupportedOnSQC || scaSupportedOnSQS) {
        supportedTools.add(new SearchDependencyRisksTool(this, sonarQubeVersionChecker, configuredProjectKey));
      }
    }
  }

  private static boolean isAdvancedAnalysisEnabledForOrg(@Nullable ServerApi api, @Nullable String orgKey) {
    if (api == null || orgKey == null) {
      return false;
    }
    return RunAdvancedCodeAnalysisTool.isA3sEnabled(api, orgKey);
  }

  private static boolean isCagEnabledForOrg(@Nullable ServerApi api, @Nullable String orgKey) {
    if (api == null || orgKey == null) {
      return false;
    }
    var orgUuidV4 = api.organizationsApi().getOrganizationUuidV4(orgKey);
    if (orgUuidV4 == null) {
      LOG.debug("CAG entitlement check: could not resolve UUID for org '" + orgKey + "' - skipping CAG");
      return false;
    }
    var config = api.a3sAnalysisApi().getCagOrgConfig(orgUuidV4);
    if (config == null) {
      LOG.debug("CAG entitlement check: could not retrieve org config for org '" + orgKey + "' - skipping CAG");
      return false;
    }
    if (!config.enabled()) {
      LOG.debug("CAG entitlement check: CAG is not enabled for org '" + orgKey + "'");
    }
    return config.enabled();
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
      supportedTools.add(new AnalyzeCodeSnippetTool(backendService, this, initializationFuture, mcpConfiguration.getProjectKey(), mcpConfiguration.getWorkspacePath()));
    }
  }

  private void logToolsLoaded() {
    var filterReason = mcpConfiguration.isReadOnlyMode() ? "category and read-only filtering" : "category filtering";
    LOG.info("All tools loaded: " + this.supportedTools.size() + " tools after " + filterReason);
  }

  private void loadProxiedServerTools() {
    proxiedToolsLoader = new ProxiedToolsLoader();
    var currentTransportMode = mcpConfiguration.isHttpEnabled() ? TransportMode.HTTP : TransportMode.STDIO;
    var proxiedTools = proxiedToolsLoader.loadProxiedTools(currentTransportMode, mcpConfiguration.getMcpServerId());
    supportedTools.addAll(proxiedTools);

    var proxiedInstructions = proxiedToolsLoader.getProxiedInstructions();
    composedInstructions = ProxiedToolsLoader.composeInstructions(composedInstructions, proxiedInstructions);
    LOG.debug("Forwarded instructions from " + proxiedInstructions.size() + " proxied server(s)");
    proxiedInstructions.forEach(s -> LOG.debug("Proxied instructions: {}", s));
  }

  private void setBaseInstructions() {
    composedInstructions = mcpConfiguration.isToolCategoryEnabled(ToolCategory.ANALYSIS)
      ? BASE_INSTRUCTIONS_WITH_ANALYSIS
      : BASE_INSTRUCTIONS_WITHOUT_ANALYSIS;
    if (mcpConfiguration.getProjectKey() == null) {
      composedInstructions += "\n" + PROJECT_KEY_INSTRUCTIONS;
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
      .callHandler((exchange, toolRequest) -> {
        captureCallingAgent(exchange);
        return toolExecutor.execute(tool, toolRequest);
      })
      .build();
  }

  private void captureCallingAgent(McpSyncServerExchange exchange) {
    if (!mcpConfiguration.isTelemetryEnabled()) {
      return;
    }
    var clientInfo = exchange.getClientInfo();
    if (clientInfo != null) {
      connectionContext.captureCallingAgent(clientInfo.name(), clientInfo.version());
    }
  }

  private void logInitialization() {
    var transportType = mcpConfiguration.isHttpEnabled() ? "HTTP" : "stdio";
    var sonarQubeType = mcpConfiguration.isSonarQubeCloud() ? "SonarQube Cloud" : "SonarQube Server";

    LOG.info("========================================");
    LOG.info("SonarQube MCP Server Started:");
    LOG.info("Transport: " + transportType +
      (mcpConfiguration.isHttpEnabled() ? (" (" + mcpConfiguration.getHttpHost() + ":" + mcpConfiguration.getHttpPort() + ")") : ""));
    LOG.info("Instance: " + sonarQubeType);
    LOG.info("URL: " + mcpConfiguration.getSonarQubeUrl());
    if (mcpConfiguration.isSonarQubeCloud() && mcpConfiguration.getSonarqubeOrg() != null) {
      LOG.info("Organization: " + mcpConfiguration.getSonarqubeOrg());
    }
    if (mcpConfiguration.isReadOnlyMode()) {
      LOG.info("Mode: READ-ONLY (write operations disabled)");
    }
    var workspacePath = mcpConfiguration.getWorkspacePath();
    if (workspacePath != null) {
      LOG.info("Workspace: " + workspacePath);
    } else {
      LOG.info("Workspace: none");
    }
    LOG.info("Status: Server ready - tools loading in background");
    LOG.info("========================================");

    logDebugDetails();
  }

  private void logDebugDetails() {
    LOG.debug("=== Debug Level Configuration Details ===");
    httpClientProvider.logConnectionSettings();
    LOG.debug("Enabled toolsets: " + mcpConfiguration.getEnabledToolsets());
    LOG.debug("Advanced analysis: " + supportedTools.stream().anyMatch(RunAdvancedCodeAnalysisTool.class::isInstance));
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
   *   Org resolution follows strict rules:
   *   - If SONARQUBE_ORG is set at server startup, it is used for all requests and clients
   *     must NOT supply a SONARQUBE_ORG header (doing so results in an error).
   *   - If SONARQUBE_ORG is not set at server startup, clients connecting to SonarQube Cloud
   *     must supply a SONARQUBE_ORG header on every request.
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
      var orgFromRequest = (String) ctx.get(HttpServerTransportProvider.CONTEXT_ORG_KEY);
      var organization = getOrganization(orgFromRequest);
      return createServerApiWithTokenAndOrg(token, organization);
    } else {
      return Objects.requireNonNull(serverApi, "ServerApi not initialized");
    }
  }

  private String getOrganization(@Nullable String orgFromRequest) {
    var serverOrg = mcpConfiguration.getSonarqubeOrg();
    if (serverOrg != null) {
      return serverOrg;
    }
    return orgFromRequest;
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
    var apiUrl = mcpConfiguration.getSonarQubeCloudApiUrl();
    var isSonarQubeCloud = mcpConfiguration.isSonarQubeCloud();
    var httpClient = token != null ? httpClientProvider.getHttpClient(token) : httpClientProvider.getAnonymousHttpClient();
    var serverApiHelper = new ServerApiHelper(new EndpointParams(url, organization, apiUrl, isSonarQubeCloud), httpClient);
    return new ServerApi(serverApiHelper, isSonarQubeCloud);
  }

  private SonarQubeIdeBridgeClient initializeBridgeClient(McpServerLaunchConfiguration mcpConfiguration) {
    LOG.info("Initializing SonarQube for IDE bridge client...");
    var host = mcpConfiguration.getHostMachineAddress();
    var port = mcpConfiguration.getSonarQubeIdePort();
    var bridgeUrl = "http://" + host + ":" + port;
    LOG.info("Bridge URL: " + bridgeUrl);
    var httpClient = httpClientProvider.getHttpClientForBridge();
    var bridgeHelper = new ServerApiHelper(new EndpointParams(bridgeUrl, null, null, false), httpClient);
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
    shutdownAnalytics();
    shutdownBackend();
  }

  private void shutdownAnalytics() {
    if (analyticsService != null) {
      analyticsService.shutdown();
    }
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

  @VisibleForTesting
  public String getComposedInstructions() {
    return composedInstructions;
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
