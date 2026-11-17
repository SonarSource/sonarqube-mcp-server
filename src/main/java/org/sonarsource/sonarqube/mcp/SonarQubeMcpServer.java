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
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import jakarta.annotation.Nullable;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarqube.mcp.analytics.AnalyticsClient;
import org.sonarsource.sonarqube.mcp.analytics.AnalyticsService;
import org.sonarsource.sonarqube.mcp.analytics.ConnectionContext;
import org.sonarsource.sonarqube.mcp.authentication.Auth0OAuthAuthenticationService;
import org.sonarsource.sonarqube.mcp.authentication.AuthenticationFilter.OAuthSupport;
import org.sonarsource.sonarqube.mcp.authentication.OAuthProtectedResourceMetadata;
import org.sonarsource.sonarqube.mcp.client.ProxiedToolsLoader;
import org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfiguration;
import org.sonarsource.sonarqube.mcp.http.HttpClientProvider;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.plugins.PluginsSynchronizer;
import org.sonarsource.sonarqube.mcp.serverapi.EndpointParams;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.OrganizationsApi;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.ResolvedOrganization;
import org.sonarsource.sonarqube.mcp.serverapi.plugins.SonarCloudCdnPluginsApi;
import org.sonarsource.sonarqube.mcp.slcore.BackendService;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCapabilities;
import org.sonarsource.sonarqube.mcp.tools.ToolCapabilitiesResolver;
import org.sonarsource.sonarqube.mcp.tools.ToolCatalog;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;
import org.sonarsource.sonarqube.mcp.tools.ToolExecutor;
import org.sonarsource.sonarqube.mcp.tools.ToolDefinitionBuilder;
import org.sonarsource.sonarqube.mcp.tools.ToolParameters;
import org.sonarsource.sonarqube.mcp.tools.agenticreadiness.ListAgenticReadinessAssessmentsTool;
import org.sonarsource.sonarqube.mcp.tools.agenticreadiness.StartAgenticReadinessAssessmentTool;
import org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureGetTool;
import org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureTools;
import org.sonarsource.sonarqube.mcp.tools.proxied.ProxiedMcpTool;
import org.sonarsource.sonarqube.mcp.transport.HttpServerTransportProvider;
import org.sonarsource.sonarqube.mcp.transport.HttpTransportSettings;
import org.sonarsource.sonarqube.mcp.transport.StdioInitializeErrorReporter;
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
  private static final String BRANCH_PULL_REQUEST_INSTRUCTIONS_COMMON = """
    - Never pass a git branch name to a pullRequest parameter — it expects the SonarQube PR key.
    - Never provide both branch and pullRequest on the same call.
    - Omit both to query the default (main) branch analysis.
    """;
  private static final String BRANCH_PULL_REQUEST_INSTRUCTIONS_CLOUD = """
    ## Branch vs Pull Request Context
    SonarQube Cloud has three analysis contexts: long-lived branches, short-lived branches, and pull requests.
    - Long-lived branches (main, develop): use `branch`. Discover with `list_branches` (type LONG) or `branchTypes=LONG`.
    - Short-lived branches (no PR): use `branch`. Discover via `list_branches` (SHORT) or `branchTypes=SHORT`; git branch name also works.
    - Pull request analysis: use `pullRequest`. Discover keys with `list_pull_requests`.
    - If the user is working on a pull request, prefer `pullRequest`. Otherwise use `branch`.
    """ + BRANCH_PULL_REQUEST_INSTRUCTIONS_COMMON;
  private static final String BRANCH_PULL_REQUEST_INSTRUCTIONS_SERVER = """
    ## Branch vs Pull Request Context
    SonarQube Server has two analysis contexts: branches and pull requests.
    - Branch analysis: use `branch`. Discover names with `list_branches`, or pass the current git branch name directly.
    - Pull request analysis: use `pullRequest`. Discover keys with `list_pull_requests`.
    - If the user is working on a pull request, prefer `pullRequest`. Otherwise use `branch`.
    """ + BRANCH_PULL_REQUEST_INSTRUCTIONS_COMMON;
  private static final String BASE_INSTRUCTIONS_WITH_ANALYSIS = "Transform your code quality workflow with SonarQube integration. " +
    "Analyze code, monitor project health, investigate issues, and understand quality gates. " +
    "Note: Analyzers are being downloaded in the background and will be available shortly for code analysis.";
  private static final String BASE_INSTRUCTIONS_WITHOUT_ANALYSIS = "Transform your code quality workflow with SonarQube integration. " +
    "Monitor project health, investigate issues, and understand quality gates.";
  private static final String VORTEX_DEPRECATED_TOOLSET_INSTRUCTIONS =
    "\nNote: the `cag`/`analysis` toolset names for Vortex tools are deprecated; use `vortex` instead.";

  private BackendService backendService;
  private ToolExecutor toolExecutor;
  private final HttpServerTransportProvider httpServerManager;
  private final McpServerTransportProvider transportProvider;
  private final McpServerLaunchConfiguration mcpConfiguration;
  private final HttpClientProvider httpClientProvider;
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
  /**
   * Effective SonarQube Cloud organization for the stdio session (key and optional cached UUID v4).
   * Set by {@link #resolveOrganizationAtStartup()}. HTTP mode resolves organization per request instead.
   */
  @Nullable
  private ResolvedOrganization resolvedOrganization;
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
  private final AtomicReference<Thread> backgroundInitializationThread = new AtomicReference<>();
  private ToolCatalog toolCatalog;
  private final ProxiedToolsLoader proxiedToolsLoader = new ProxiedToolsLoader();

  public static void main(String[] args) {
    try {
      new SonarQubeMcpServer(System.getenv()).start();
    } catch (RuntimeException e) {
      handleStartupFailure(e, System.getenv(), System.in, System.out);
      System.exit(1);
    }
  }

  @VisibleForTesting
  static void handleStartupFailure(RuntimeException error, Map<String, String> environment, InputStream in, OutputStream out) {
    LOG.error("Failed to start SonarQube MCP Server", error);
    if (!McpServerLaunchConfiguration.isHttpTransport(environment)) {
      StdioInitializeErrorReporter.report(error, in, out);
    }
  }

  public SonarQubeMcpServer(Map<String, String> environment) {
    this.mcpConfiguration = new McpServerLaunchConfiguration(environment);
    this.httpClientProvider = new HttpClientProvider(mcpConfiguration.getUserAgent());

    initializeAnalytics();

    if (mcpConfiguration.isHttpEnabled()) {
      var oauthConfiguration = mcpConfiguration.getOAuthConfiguration();
      this.httpServerManager = new HttpServerTransportProvider(HttpTransportSettings.from(mcpConfiguration), oauthConfiguration == null ? null : new OAuthSupport(
        new OAuthProtectedResourceMetadata(oauthConfiguration.resource(), oauthConfiguration.issuer().toString(), mcpConfiguration.getOAuthMetadataUrl()),
        Auth0OAuthAuthenticationService.create(oauthConfiguration, httpClientProvider.getAnonymousHttpClient(), result -> {
          if (analyticsService != null) {
            analyticsService.notifyOAuthAuthenticationFinished(result);
          }
        })::authenticate));
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
      var enabledTools = toolCatalog.enabledTools();
      statelessSyncServer = McpServer.sync(httpServerManager.getFilteringTransport(enabledTools, analyticsService,
        context -> createServerApiWithTokenAndOrg((String) context.get(HttpServerTransportProvider.CONTEXT_TOKEN_KEY), null)))
        .serverInfo(McpSchema.Implementation.builder(SONARQUBE_MCP_SERVER_NAME, mcpConfiguration.getAppVersion()).build())
        .instructions(composedInstructions)
        .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
        .tools(enabledTools.stream().map(this::toStatelessSpec).toArray(McpStatelessServerFeatures.SyncToolSpecification[]::new))
        .build();
    } else {
      stdioSyncServer = McpServer.sync(transportProvider)
        .serverInfo(McpSchema.Implementation.builder(SONARQUBE_MCP_SERVER_NAME, mcpConfiguration.getAppVersion()).build())
        .instructions(composedInstructions)
        .capabilities(McpSchema.ServerCapabilities.builder().tools(true).logging().build())
        .tools(toolCatalog.enabledTools().stream().map(this::toStdioSpec).toArray(McpServerFeatures.SyncToolSpecification[]::new))
        .build();
    }

    Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

    startBackgroundInitialization();
  }

  private void startBackgroundInitialization() {
    var thread = new Thread(this::initializeBackgroundServices, "mcp-background-init");
    thread.setDaemon(false);
    backgroundInitializationThread.set(thread);
    thread.start();
  }

  private void initializeAnalytics() {
    if (mcpConfiguration.isTelemetryEnabled()) {
      var analyticsHttpClient = httpClientProvider.getHttpClientForAnalytics(AnalyticsClient.resolveApiKey());
      var analyticsClient = new AnalyticsClient(analyticsHttpClient);
      this.analyticsService = new AnalyticsService(analyticsClient, mcpConfiguration.getMcpServerId(),
        mcpConfiguration.getAppVersion(), mcpConfiguration.isHttpEnabled(), mcpConfiguration.isHttpsEnabled(), mcpConfiguration.isSonarQubeCloud());
    }
  }

  /**
   * Initializes all services and loads all tools synchronously.
   * The backend is initialized immediately (without analyzers) so that tools can be registered.
   * Analyzers are downloaded in the background and the backend is restarted with them later.
   */
  private void initializeBasicServicesAndTools() {
    this.backendService = new BackendService(mcpConfiguration);

    // Capture the invocation API for analytics; stdio reuses session identity, HTTP resolves identity per call.
    this.toolExecutor = new ToolExecutor(backendService, analyticsService,
      mcpConfiguration.isHttpEnabled() ? null : connectionContext, this,
      mcpConfiguration.getMcpServerId(), this::resolveEnabledToolsets);

    var configuredOrgKey = mcpConfiguration.getSonarqubeOrg();
    if (configuredOrgKey != null) {
      this.resolvedOrganization = ResolvedOrganization.fromKey(configuredOrgKey);
    }
    this.serverApi = initializeServerApi(mcpConfiguration);
    var organizations = resolveOrganizationAtStartup();
    var versionChecker = new SonarQubeVersionChecker(serverApi);
    organizations.forEach(serverApi::cacheOrganizationUuid);
    var serverVersion = versionChecker.failIfSonarQubeServerVersionIsNotSupported();

    // The bridge probe requires the backend initialized, but never waits for analyzer downloads.
    backendService.initialize(new BackendService.AnalyzersAndLanguagesEnabled(Set.of(), EnumSet.noneOf(Language.class)));
    backendService.notifyTransportModeUsed();
    var resolver = new ToolCapabilitiesResolver(mcpConfiguration, serverApi, httpClientProvider, serverVersion);
    var resolution = resolver.resolve(resolvedOrganization, organizations);
    var capabilities = resolution.capabilities();
    if (capabilities.ideAnalysis()) {
      backendService.notifySonarQubeIdeIntegration();
    }
    var proxiedTools = loadProxiedTools(capabilities);
    toolCatalog = new ToolCatalog(mcpConfiguration, resolution, this, versionChecker, backendService, initializationFuture, proxiedTools);
    setInstructions(capabilities);
  }

  private List<Tool> loadProxiedTools(ToolCapabilities capabilities) {
    var transport = mcpConfiguration.getTransportMode();
    if (!ProxiedMcpTool.REGISTRATION_AVAILABILITY.isAvailable(transport, capabilities)) {
      return List.of();
    }
    LOG.info("Vortex is enabled");
    return proxiedToolsLoader.loadProxiedTools(transport, mcpConfiguration.getMcpServerId(),
      resolvedOrganization != null ? resolvedOrganization.key() : null);
  }

  /**
   * Downloads analyzers in background and restarts the backend with them.
   * Tools are already loaded synchronously during startup.
   * Downloads analyzers only when the local analysis tool is enabled.
   */
  private void initializeBackgroundServices() {
    try {
      logInitialization();

      // In stdio mode the token is fixed
      if (mcpConfiguration.isTelemetryEnabled() && !mcpConfiguration.isHttpEnabled() && serverApi != null) {
        connectionContext.resolveFrom(serverApi);
      }

      if (!toolCatalog.needsLocalAnalyzers()) {
        LOG.info("Local analysis tool is not enabled - skipping analyzers download");
        initializationFuture.complete(null);
        return;
      }

      var sonarCloudCdnPluginsApi = new SonarCloudCdnPluginsApi(mcpConfiguration.getSonarQubeUrl(), httpClientProvider.getAnonymousHttpClient());
      var pluginsSynchronizer = new PluginsSynchronizer(Objects.requireNonNull(serverApi), sonarCloudCdnPluginsApi, mcpConfiguration.getStoragePath());

      LOG.info("Downloading analyzers in background...");
      var analyzers = pluginsSynchronizer.synchronizeAnalyzers();

      if (isShutdown) {
        initializationFuture.cancel(false);
        return;
      }

      // Restart backend with the downloaded analyzers
      LOG.info("Restarting backend with downloaded analyzers...");
      backendService.restartWithAnalyzers(analyzers);

      initializationFuture.complete(null);
      LOG.info("Background initialization completed successfully - analyzers are now available");
    } catch (Exception e) {
      if (isShutdown) {
        initializationFuture.cancel(false);
      } else {
        LOG.error("Background initialization failed", e);
        initializationFuture.completeExceptionally(e);
      }
    }
  }

  private void setInstructions(ToolCapabilities capabilities) {
    composedInstructions = mcpConfiguration.isToolCategoryEnabled(ToolCategory.ANALYSIS)
      ? BASE_INSTRUCTIONS_WITH_ANALYSIS
      : BASE_INSTRUCTIONS_WITHOUT_ANALYSIS;
    composedInstructions += "\n" + (mcpConfiguration.isSonarQubeCloud()
      ? BRANCH_PULL_REQUEST_INSTRUCTIONS_CLOUD
      : BRANCH_PULL_REQUEST_INSTRUCTIONS_SERVER);
    if (mcpConfiguration.isSonarQubeCloud() && mcpConfiguration.getSonarqubeOrg() == null) {
      composedInstructions += "\nIf the organization is ambiguous, call list_organizations first, then pass its key as organization to organization-aware tools. "
        + "When a SONARQUBE_ORG request header is set, the organization is fixed for that request; omit the organization argument.";
    }
    if (mcpConfiguration.getProjectKey() == null) {
      composedInstructions += "\n" + PROJECT_KEY_INSTRUCTIONS;
    }
    if (toolCatalog.enabledTools().stream().anyMatch(ArchitectureGetTool.class::isInstance)) {
      composedInstructions += ArchitectureTools.INSTRUCTIONS;
    }
    if ((capabilities.vortexProxy() || capabilities.vortexAnalysis()) && !mcpConfiguration.isToolCategoryEnabled(ToolCategory.VORTEX)) {
      LOG.warn("Vortex tools registered via the deprecated 'cag'/'analysis' toolset name(s) - consider adding 'vortex' to SONARQUBE_TOOLSETS instead.");
      composedInstructions += VORTEX_DEPRECATED_TOOLSET_INSTRUCTIONS;
    }
    var proxiedInstructions = proxiedToolsLoader.getProxiedInstructions();
    composedInstructions = ProxiedToolsLoader.composeInstructions(composedInstructions, proxiedInstructions);
    LOG.debug("Forwarded instructions from " + proxiedInstructions.size() + " proxied server(s)");
    proxiedInstructions.forEach(s -> LOG.debug("Proxied instructions: {}", s));
    if (toolCatalog.enabledTools().stream().anyMatch(ListAgenticReadinessAssessmentsTool.class::isInstance)) {
      composedInstructions += StartAgenticReadinessAssessmentTool.INSTRUCTIONS;
    }
  }

  @VisibleForTesting
  List<Tool> filterForEnabledTools(List<Tool> toolsToFilter) {
    return toolCatalog.filterEnabled(toolsToFilter);
  }

  private McpStatelessServerFeatures.SyncToolSpecification toStatelessSpec(Tool tool) {
    return new McpStatelessServerFeatures.SyncToolSpecification.Builder()
      .tool(toolDefinition(tool))
      .callHandler((transportContext, toolRequest) -> {
        currentTransportContext.set(transportContext);
        try {
          return executeTool(tool, toolRequest);
        } finally {
          currentTransportContext.remove();
        }
      })
      .build();
  }

  private McpServerFeatures.SyncToolSpecification toStdioSpec(Tool tool) {
    return new McpServerFeatures.SyncToolSpecification.Builder()
      .tool(toolDefinition(tool))
      .callHandler((exchange, toolRequest) -> {
        captureCallingAgent(exchange);
        return executeTool(tool, toolRequest);
      })
      .build();
  }

  private McpSchema.Tool toolDefinition(Tool tool) {
    return fixedOrganization() == null ? tool.definition() : ToolDefinitionBuilder.withoutOrganization(tool.definition());
  }

  private McpSchema.CallToolResult executeTool(Tool tool, McpSchema.CallToolRequest request) {
    if (fixedOrganization() != null && request.arguments() != null && request.arguments().containsKey(ToolParameters.ORGANIZATION)) {
      return Tool.Result.failure("Organization is fixed by SONARQUBE_ORG. Remove the organization argument.").toCallToolResult();
    }
    return toolExecutor.execute(tool, request);
  }

  @VisibleForTesting
  Set<ToolCategory> resolveEnabledToolsets() {
    var enabledToolsets = ToolCategory.all().stream()
      .filter(mcpConfiguration::isToolCategoryEnabled)
      .collect(Collectors.toCollection(() -> EnumSet.noneOf(ToolCategory.class)));

    var transportContext = currentTransportContext.get();
    if (transportContext != null) {
      var requestToolsets = transportContext.get(HttpServerTransportProvider.CONTEXT_TOOLSETS_KEY);
      if (requestToolsets instanceof Set<?> toolsets) {
        enabledToolsets.retainAll(toolsets);
        enabledToolsets.add(ToolCategory.PROJECTS);
      }
    }
    return enabledToolsets;
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
      (httpServerManager != null ? (" (" + httpServerManager.getServerUrl() + ")") : ""));
    LOG.info("Instance: " + sonarQubeType);
    LOG.info("URL: " + mcpConfiguration.getSonarQubeUrl());
    if (mcpConfiguration.isSonarQubeCloud() && resolvedOrganization != null) {
      LOG.info("Organization: " + resolvedOrganization.key());
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
    LOG.debug("Advanced analysis tool registered: " + toolCatalog.advancedAnalysisRegistered());
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
   *   An optional SONARQUBE_ORG header fixes the organization for that request.
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
      return createServerApiWithTokenAndOrg(token, orgFromRequest);
    } else {
      return Objects.requireNonNull(serverApi, "ServerApi not initialized");
    }
  }

  @Nullable
  private String fixedOrganization() {
    if (!mcpConfiguration.isHttpEnabled()) {
      return mcpConfiguration.getSonarqubeOrg();
    }
    var context = currentTransportContext.get();
    if (context != null && context.get(HttpServerTransportProvider.CONTEXT_ORG_KEY) instanceof String key && !key.isBlank()) {
      return key;
    }
    return null;
  }

  private ServerApi initializeServerApi(McpServerLaunchConfiguration mcpConfiguration) {
    var token = mcpConfiguration.getSonarQubeToken();
    return createServerApiWithToken(token);
  }

  /**
   * Auto-detects the SonarQube Cloud organization in stdio mode when {@code SONARQUBE_ORG} is unset.
   * When the token belongs to exactly one organization it is adopted and the startup {@link ServerApi} is rebuilt.
   * Returns the relevant organizations with their discovered UUIDs for startup analysis selection.
   */
  private List<ResolvedOrganization> resolveOrganizationAtStartup() {
    if (mcpConfiguration.isHttpEnabled() || !mcpConfiguration.isSonarQubeCloud()) {
      return List.of();
    }
    if (resolvedOrganization != null) {
      return List.of(resolvedOrganization);
    }
    var token = mcpConfiguration.getSonarQubeToken();
    if (token == null || serverApi == null) {
      return List.of();
    }
    List<OrganizationsApi.Organization> organizations;
    try {
      organizations = serverApi.organizationsApi().listOrganizations();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to list SonarQube Cloud organizations for the provided token. " +
        "Verify the token is valid, or set SONARQUBE_ORG explicitly.", e);
    }
    if (organizations.size() == 1) {
      var organization = organizations.getFirst();
      this.resolvedOrganization = ResolvedOrganization.from(organization);
      this.serverApi = createServerApiWithToken(token);
      LOG.info("Auto-selected SonarQube Cloud organization: " + resolvedOrganization.key());
    } else {
      LOG.info("SonarQube Cloud organization is unresolved; use list_organizations and the organization tool argument.");
    }
    return organizations.stream().map(ResolvedOrganization::from).toList();
  }

  private ServerApi createServerApiWithToken(@Nullable String token) {
    var orgKey = resolvedOrganization != null ? resolvedOrganization.key() : null;
    return createServerApiWithTokenAndOrg(token, orgKey);
  }

  private ServerApi createServerApiWithTokenAndOrg(@Nullable String token, @Nullable String organization) {
    var url = mcpConfiguration.getSonarQubeUrl();
    var apiUrl = mcpConfiguration.getSonarQubeCloudApiUrl();
    var isSonarQubeCloud = mcpConfiguration.isSonarQubeCloud();
    var httpClient = token != null ? httpClientProvider.getHttpClient(token) : httpClientProvider.getAnonymousHttpClient();
    var serverApiHelper = new ServerApiHelper(new EndpointParams(url, organization, apiUrl, isSonarQubeCloud), httpClient);
    return new ServerApi(serverApiHelper, isSonarQubeCloud);
  }

  public void shutdown() {
    if (isShutdown) {
      return;
    }
    isShutdown = true;

    stopBackgroundInitialization();
    proxiedToolsLoader.shutdown();
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

  private void stopBackgroundInitialization() {
    initializationFuture.cancel(false);
    var thread = backgroundInitializationThread.get();
    if (thread == null) {
      return;
    }
    thread.interrupt();
    try {
      if (!thread.join(Duration.ofSeconds(30))) {
        LOG.warn("Background initialization did not stop within 30 seconds, proceeding with shutdown");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.warn("Interrupted while waiting for background initialization to stop");
    }
  }

  private void shutdownHttpServer() {
    if (httpServerManager == null) {
      return;
    }
    try {
      LOG.info("Stopping HTTP server...");
      httpServerManager.stopServer();
      LOG.info("HTTP server stopped");
    } catch (Exception e) {
      LOG.error("Error shutting down HTTP server", e);
    }
  }

  private void shutdownHttpClient() {
    try {
      httpClientProvider.shutdown();
    } catch (Exception e) {
      LOG.error("Error shutting down HTTP client", e);
    }
  }

  private void shutdownMcpServer() {
    try {
      if (statelessSyncServer != null) {
        statelessSyncServer.closeGracefully();
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
    this.httpClientProvider = new HttpClientProvider(mcpConfiguration.getUserAgent());
    this.transportProvider = transportProvider;
    this.httpServerManager = httpServerManager;
    initializeAnalytics();
    initializeBasicServicesAndTools();
  }

  // Package-private getters for testing
  McpServerLaunchConfiguration getMcpConfiguration() {
    return mcpConfiguration;
  }

  @VisibleForTesting
  @Nullable
  public String getHttpServerUrl() {
    return httpServerManager == null ? null : httpServerManager.getServerUrl();
  }

  public List<Tool> getSupportedTools() {
    return toolCatalog.supportedTools();
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
