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
package org.sonarsource.sonarqube.mcp.tools;

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.sonarsource.sonarqube.mcp.SonarQubeVersionChecker;
import org.sonarsource.sonarqube.mcp.bridge.SonarQubeIdeBridgeClient;
import org.sonarsource.sonarqube.mcp.client.ProxiedToolsLoader;
import org.sonarsource.sonarqube.mcp.client.TransportMode;
import org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfiguration;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.ResolvedOrganization;
import org.sonarsource.sonarqube.mcp.slcore.BackendService;
import org.sonarsource.sonarqube.mcp.tools.agenticreadiness.GetAgenticReadinessAssessmentTool;
import org.sonarsource.sonarqube.mcp.tools.agenticreadiness.ListAgenticReadinessAssessmentsTool;
import org.sonarsource.sonarqube.mcp.tools.agenticreadiness.StartAgenticReadinessAssessmentTool;
import org.sonarsource.sonarqube.mcp.tools.analysis.AnalyzeCodeSnippetTool;
import org.sonarsource.sonarqube.mcp.tools.analysis.AnalyzeFileListTool;
import org.sonarsource.sonarqube.mcp.tools.analysis.RunAdvancedCodeAnalysisTool;
import org.sonarsource.sonarqube.mcp.tools.analysis.ToggleAutomaticAnalysisTool;
import org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureGetTool;
import org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureSetTool;
import org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureTools;
import org.sonarsource.sonarqube.mcp.tools.branches.ListBranchesTool;
import org.sonarsource.sonarqube.mcp.tools.dependencyrisks.SearchDependencyRisksTool;
import org.sonarsource.sonarqube.mcp.tools.duplications.GetDuplicationsTool;
import org.sonarsource.sonarqube.mcp.tools.duplications.SearchDuplicatedFilesTool;
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
import org.sonarsource.sonarqube.mcp.tools.organizations.ListOrganizationsTool;
import org.sonarsource.sonarqube.mcp.tools.portfolios.ListPortfoliosTool;
import org.sonarsource.sonarqube.mcp.tools.projects.SearchMyProjectsTool;
import org.sonarsource.sonarqube.mcp.tools.proxied.ProxiedMcpTool;
import org.sonarsource.sonarqube.mcp.tools.pullrequests.ListPullRequestsTool;
import org.sonarsource.sonarqube.mcp.tools.qualitygates.ListQualityGatesTool;
import org.sonarsource.sonarqube.mcp.tools.qualitygates.ProjectStatusTool;
import org.sonarsource.sonarqube.mcp.tools.rules.ShowRuleTool;
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

/** Explicit construction wiring; each tool owns the policy checked before its factory runs. */
public final class ToolCatalog {

  private static final McpLogger LOG = McpLogger.getInstance();
  private static final String AGENTIC_READINESS_INSTRUCTIONS = """

    ## Agentic Readiness Assessment

    Tools to assess how well a SonarQube Cloud project's codebase supports AI agents, scored per pillar \
    (Documentation, Workflow, Dev Environment, Build, Testing, Code Quality, Security, Observability) from \
    L1 (pre-agentic, implicit conventions) to L5 (agent-native, self-describing).

    Workflow:
    1. Call list_agentic_readiness_assessments (filtered by branch) and reuse a recent COMPLETED result if \
    one exists — assessments take ~10 minutes.
    2. Otherwise call start_agentic_readiness_assessment; it returns PENDING with an assessmentId. Do not poll immediately.
    3. After a few minutes, poll get_agentic_readiness_assessment until status is COMPLETED, FAILED, or INTERRUPTED.
    4. Present overallLevel and the per-pillar breakdown, including each pillar's recommended actions and the evidence behind them.

    To measure the impact of changes, compare a baseline assessment of the default branch against a new \
    assessment of your feature branch. Always filter the list by branch — unfiltered results mix branches.""";
  private static final String VORTEX_DEPRECATED_TOOLSET_INSTRUCTIONS =
    "\nNote: the `cag`/`analysis` toolset names for Vortex tools are deprecated; use `vortex` instead.";

  private final McpServerLaunchConfiguration configuration;
  private final ToolCapabilities capabilities;
  private final TransportMode transport;
  private final ProxiedToolsLoader proxiedToolsLoader;
  private final List<Tool> supportedTools = new ArrayList<>();
  private String instructions = "";
  @Nullable
  private Tool localAnalysisTool;
  private boolean advancedAnalysisRegistered;

  public ToolCatalog(McpServerLaunchConfiguration configuration, ToolCapabilities capabilities) {
    this(configuration, capabilities, new ProxiedToolsLoader());
  }

  ToolCatalog(McpServerLaunchConfiguration configuration, ToolCapabilities capabilities, ProxiedToolsLoader proxiedToolsLoader) {
    this.configuration = configuration;
    this.capabilities = capabilities;
    this.proxiedToolsLoader = proxiedToolsLoader;
    this.transport = configuration.isHttpEnabled() ? TransportMode.HTTP : TransportMode.STDIO;
  }

  public void load(ServerApiProvider provider, SonarQubeVersionChecker versionChecker, BackendService backendService,
    CompletableFuture<Void> initializationFuture, @Nullable SonarQubeIdeBridgeClient bridgeClient, @Nullable ResolvedOrganization organization) {
    loadRestTools(provider, versionChecker);
    if (configuration.isToolCategoryEnabled(ToolCategory.ARCHITECTURE) && capabilities.architecture()) {
      instructions += ArchitectureTools.INSTRUCTIONS;
    }
    if ((capabilities.vortexProxy() || capabilities.vortexAnalysis()) && !configuration.isToolCategoryEnabled(ToolCategory.VORTEX)) {
      LOG.warn("Vortex tools registered via the deprecated 'cag'/'analysis' toolset name(s) - consider adding 'vortex' to SONARQUBE_TOOLSETS instead.");
      instructions += VORTEX_DEPRECATED_TOOLSET_INSTRUCTIONS;
    }
    loadProxiedTools(organization);

    var projectKey = configuration.getProjectKey();
    var cloud = configuration.isSonarQubeCloud();
    register(StartAgenticReadinessAssessmentTool.POLICY, () -> new StartAgenticReadinessAssessmentTool(provider, projectKey));
    register(GetAgenticReadinessAssessmentTool.POLICY, () -> new GetAgenticReadinessAssessmentTool(provider));
    register(ListAgenticReadinessAssessmentsTool.POLICY, () -> new ListAgenticReadinessAssessmentsTool(provider, projectKey));
    if (capabilities.agenticReadiness()) {
      instructions += AGENTIC_READINESS_INSTRUCTIONS;
    }
    advancedAnalysisRegistered = register(RunAdvancedCodeAnalysisTool.POLICY,
      () -> new RunAdvancedCodeAnalysisTool(provider, projectKey, Objects.requireNonNull(configuration.getWorkspacePath()), cloud)) != null;
    if (advancedAnalysisRegistered) {
      LOG.info("Advanced analysis tool registered");
    }
    register(AnalyzeFileListTool.POLICY, () -> new AnalyzeFileListTool(Objects.requireNonNull(bridgeClient)));
    register(ToggleAutomaticAnalysisTool.POLICY, () -> new ToggleAutomaticAnalysisTool(Objects.requireNonNull(bridgeClient)));
    localAnalysisTool = register(AnalyzeCodeSnippetTool.POLICY,
      () -> new AnalyzeCodeSnippetTool(backendService, provider, initializationFuture, projectKey, configuration.getWorkspacePath(), cloud));

    var filterReason = configuration.isReadOnlyMode() ? "category and read-only filtering" : "category filtering";
    LOG.info("All tools loaded: " + supportedTools.size() + " tools after " + filterReason);
  }

  private void loadRestTools(ServerApiProvider provider, SonarQubeVersionChecker versionChecker) {
    var projectKey = configuration.getProjectKey();
    var cloud = configuration.isSonarQubeCloud();
    register(ListEnterprisesTool.POLICY, () -> new ListEnterprisesTool(provider));
    register(ListOrganizationsTool.POLICY, () -> new ListOrganizationsTool(provider));
    register(SystemHealthTool.POLICY, () -> new SystemHealthTool(provider));
    register(SystemInfoTool.POLICY, () -> new SystemInfoTool(provider));
    register(SystemLogsTool.POLICY, () -> new SystemLogsTool(provider));
    register(SystemPingTool.POLICY, () -> new SystemPingTool(provider));
    register(SystemStatusTool.POLICY, () -> new SystemStatusTool(provider));
    register(ChangeIssueStatusTool.POLICY, () -> new ChangeIssueStatusTool(provider));
    register(SearchMyProjectsTool.POLICY, () -> new SearchMyProjectsTool(provider, cloud));
    register(SearchIssuesTool.POLICY, () -> new SearchIssuesTool(provider, cloud));
    register(SearchSecurityHotspotsTool.POLICY, () -> new SearchSecurityHotspotsTool(provider));
    register(ShowSecurityHotspotTool.POLICY, () -> new ShowSecurityHotspotTool(provider));
    register(ChangeSecurityHotspotStatusTool.POLICY, () -> new ChangeSecurityHotspotStatusTool(provider));
    register(ProjectStatusTool.POLICY, () -> new ProjectStatusTool(provider));
    register(ShowRuleTool.POLICY, () -> new ShowRuleTool(provider, cloud));
    register(ListQualityGatesTool.POLICY, () -> new ListQualityGatesTool(provider, cloud));
    register(ListLanguagesTool.POLICY, () -> new ListLanguagesTool(provider));
    register(GetComponentMeasuresTool.POLICY, () -> new GetComponentMeasuresTool(provider, projectKey));
    register(SearchFilesByCoverageTool.POLICY, () -> new SearchFilesByCoverageTool(provider, projectKey));
    register(GetFileCoverageDetailsTool.POLICY, () -> new GetFileCoverageDetailsTool(provider));
    register(SearchMetricsTool.POLICY, () -> new SearchMetricsTool(provider));
    register(GetScmInfoTool.POLICY, () -> new GetScmInfoTool(provider));
    register(GetRawSourceTool.POLICY, () -> new GetRawSourceTool(provider));
    register(CreateWebhookTool.POLICY, () -> new CreateWebhookTool(provider, cloud));
    register(ListWebhooksTool.POLICY, () -> new ListWebhooksTool(provider, cloud));
    register(GetDuplicationsTool.POLICY, () -> new GetDuplicationsTool(provider));
    register(SearchDuplicatedFilesTool.POLICY, () -> new SearchDuplicatedFilesTool(provider, projectKey));
    register(ListPortfoliosTool.POLICY, () -> new ListPortfoliosTool(provider, cloud));
    register(ListPullRequestsTool.POLICY, () -> new ListPullRequestsTool(provider, projectKey));
    register(ListBranchesTool.POLICY, () -> new ListBranchesTool(provider, cloud, projectKey));
    var architectureTools = new ArchitectureTools();
    register(ArchitectureGetTool.POLICY, () -> architectureTools.createGet(provider, cloud, projectKey));
    register(ArchitectureSetTool.POLICY, () -> architectureTools.createSet(provider, cloud, projectKey));
    register(SearchDependencyRisksTool.POLICY, () -> new SearchDependencyRisksTool(provider, versionChecker, projectKey, cloud));
  }

  @Nullable
  Tool register(ToolPolicy policy, Supplier<? extends Tool> factory) {
    if (!policy.isSupported(transport, capabilities)) {
      return null;
    }
    var tool = factory.get();
    supportedTools.add(tool);
    return tool;
  }

  private void loadProxiedTools(@Nullable ResolvedOrganization organization) {
    if (!ProxiedMcpTool.POLICY.isSupported(transport, capabilities)) {
      return;
    }
    LOG.info("Vortex is enabled");
    supportedTools.addAll(proxiedToolsLoader.loadProxiedTools(transport, configuration.getMcpServerId(), organization != null ? organization.key() : null));
    var proxiedInstructions = proxiedToolsLoader.getProxiedInstructions();
    instructions = ProxiedToolsLoader.composeInstructions(instructions, proxiedInstructions);
    LOG.debug("Forwarded instructions from " + proxiedInstructions.size() + " proxied server(s)");
    proxiedInstructions.forEach(s -> LOG.debug("Proxied instructions: {}", s));
  }

  public List<Tool> supportedTools() {
    return List.copyOf(supportedTools);
  }

  public List<Tool> enabledTools() {
    return filterEnabled(supportedTools);
  }

  public List<Tool> filterEnabled(List<Tool> tools) {
    return tools.stream()
      .filter(tool -> tool.getCategories().stream().anyMatch(configuration::isToolCategoryEnabled))
      .filter(tool -> !configuration.isReadOnlyMode() || (tool.definition().annotations() != null && Boolean.TRUE.equals(tool.definition().annotations().readOnlyHint())))
      .toList();
  }

  public String instructions() {
    return instructions;
  }

  public boolean needsLocalAnalyzers() {
    return localAnalysisTool != null && enabledTools().contains(localAnalysisTool);
  }

  public boolean advancedAnalysisRegistered() {
    return advancedAnalysisRegistered;
  }

  public void shutdown() {
    proxiedToolsLoader.shutdown();
  }
}
