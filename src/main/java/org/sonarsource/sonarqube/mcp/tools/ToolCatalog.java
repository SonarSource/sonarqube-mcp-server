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

import com.google.common.base.Suppliers;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.sonarsource.sonarqube.mcp.SonarQubeVersionChecker;
import org.sonarsource.sonarqube.mcp.client.ProxiedToolsLoader;
import org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfiguration;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.slcore.BackendService;
import org.sonarsource.sonarqube.mcp.tools.analysis.AnalyzeCodeSnippetTool;
import org.sonarsource.sonarqube.mcp.tools.analysis.AnalyzeFileListTool;
import org.sonarsource.sonarqube.mcp.tools.analysis.RunAdvancedCodeAnalysisTool;
import org.sonarsource.sonarqube.mcp.tools.analysis.ToggleAutomaticAnalysisTool;
import org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureTools;
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
import org.sonarsource.sonarqube.mcp.tools.organizations.ListOrganizationsTool;
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
import org.sonarsource.sonarqube.mcp.tools.branches.ListBranchesTool;
import org.sonarsource.sonarqube.mcp.tools.pullrequests.ListPullRequestsTool;
import org.sonarsource.sonarqube.mcp.tools.agenticreadiness.GetAgenticReadinessAssessmentTool;
import org.sonarsource.sonarqube.mcp.tools.agenticreadiness.ListAgenticReadinessAssessmentsTool;
import org.sonarsource.sonarqube.mcp.tools.agenticreadiness.StartAgenticReadinessAssessmentTool;
import org.sonarsource.sonarqube.mcp.tools.webhooks.CreateWebhookTool;
import org.sonarsource.sonarqube.mcp.tools.webhooks.ListWebhooksTool;

/** Declares tool factories in discovery order and builds the immutable startup inventory. */
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
  private final ServerApiProvider provider;
  private final BackendService backend;
  private final SonarQubeVersionChecker versionChecker;
  private final CompletableFuture<Void> initialization;

  public ToolCatalog(McpServerLaunchConfiguration configuration, ServerApiProvider provider, BackendService backend,
    SonarQubeVersionChecker versionChecker, CompletableFuture<Void> initialization) {
    this.configuration = configuration;
    this.provider = provider;
    this.backend = backend;
    this.versionChecker = versionChecker;
    this.initialization = initialization;
  }

  public Result create(ToolAvailability.StartupFacts facts, AnalysisToolSelection analysis, List<Tool> proxiedTools, List<String> proxiedInstructions) {
    var cloud = configuration.isSonarQubeCloud();
    var project = configuration.getProjectKey();
    Supplier<List<Tool>> architecture = Suppliers.memoize(() -> ArchitectureTools.create(provider, cloud, project));
    var entries = new ArrayList<>(List.of(
      new Entry(ToolDescriptor.LIST_ENTERPRISES, () -> new ListEnterprisesTool(provider)),
      new Entry(ToolDescriptor.LIST_ORGANIZATIONS, () -> new ListOrganizationsTool(provider)),
      new Entry(ToolDescriptor.SYSTEM_HEALTH, () -> new SystemHealthTool(provider)),
      new Entry(ToolDescriptor.SYSTEM_INFO, () -> new SystemInfoTool(provider)),
      new Entry(ToolDescriptor.SYSTEM_LOGS, () -> new SystemLogsTool(provider)),
      new Entry(ToolDescriptor.SYSTEM_PING, () -> new SystemPingTool(provider)),
      new Entry(ToolDescriptor.SYSTEM_STATUS, () -> new SystemStatusTool(provider)),
      new Entry(ToolDescriptor.CHANGE_ISSUE_STATUS, () -> new ChangeIssueStatusTool(provider)),
      new Entry(ToolDescriptor.SEARCH_MY_PROJECTS, () -> new SearchMyProjectsTool(provider, cloud)),
      new Entry(ToolDescriptor.SEARCH_ISSUES, () -> new SearchIssuesTool(provider, cloud)),
      new Entry(ToolDescriptor.SEARCH_SECURITY_HOTSPOTS, () -> new SearchSecurityHotspotsTool(provider)),
      new Entry(ToolDescriptor.SHOW_SECURITY_HOTSPOT, () -> new ShowSecurityHotspotTool(provider)),
      new Entry(ToolDescriptor.CHANGE_SECURITY_HOTSPOT_STATUS, () -> new ChangeSecurityHotspotStatusTool(provider)),
      new Entry(ToolDescriptor.PROJECT_STATUS, () -> new ProjectStatusTool(provider)),
      new Entry(ToolDescriptor.SHOW_RULE, () -> new ShowRuleTool(provider, cloud)),
      new Entry(ToolDescriptor.LIST_QUALITY_GATES, () -> new ListQualityGatesTool(provider, cloud)),
      new Entry(ToolDescriptor.LIST_LANGUAGES, () -> new ListLanguagesTool(provider)),
      new Entry(ToolDescriptor.GET_COMPONENT_MEASURES, () -> new GetComponentMeasuresTool(provider, project)),
      new Entry(ToolDescriptor.SEARCH_FILES_BY_COVERAGE, () -> new SearchFilesByCoverageTool(provider, project)),
      new Entry(ToolDescriptor.GET_FILE_COVERAGE_DETAILS, () -> new GetFileCoverageDetailsTool(provider)),
      new Entry(ToolDescriptor.SEARCH_METRICS, () -> new SearchMetricsTool(provider)),
      new Entry(ToolDescriptor.GET_SCM_INFO, () -> new GetScmInfoTool(provider)),
      new Entry(ToolDescriptor.GET_RAW_SOURCE, () -> new GetRawSourceTool(provider)),
      new Entry(ToolDescriptor.CREATE_WEBHOOK, () -> new CreateWebhookTool(provider, cloud)),
      new Entry(ToolDescriptor.LIST_WEBHOOKS, () -> new ListWebhooksTool(provider, cloud)),
      new Entry(ToolDescriptor.GET_DUPLICATIONS, () -> new GetDuplicationsTool(provider)),
      new Entry(ToolDescriptor.SEARCH_DUPLICATED_FILES, () -> new SearchDuplicatedFilesTool(provider, project)),
      new Entry(ToolDescriptor.LIST_PORTFOLIOS, () -> new ListPortfoliosTool(provider, cloud)),
      new Entry(ToolDescriptor.LIST_PULL_REQUESTS, () -> new ListPullRequestsTool(provider, project)),
      new Entry(ToolDescriptor.LIST_BRANCHES, () -> new ListBranchesTool(provider, cloud, project)),
      new Entry(ToolDescriptor.ARCHITECTURE_GET, () -> architecture.get().getFirst()),
      new Entry(ToolDescriptor.ARCHITECTURE_SET, () -> architecture.get().get(1)),
      new Entry(ToolDescriptor.SEARCH_DEPENDENCY_RISKS, () -> new SearchDependencyRisksTool(provider, versionChecker, project, cloud))));
    proxiedTools.forEach(tool -> entries.add(new Entry(ToolDescriptor.VORTEX_PROXY, () -> tool)));
    entries.addAll(List.of(
      new Entry(ToolDescriptor.START_AGENTIC_READINESS_ASSESSMENT, () -> new StartAgenticReadinessAssessmentTool(provider, project)),
      new Entry(ToolDescriptor.GET_AGENTIC_READINESS_ASSESSMENT, () -> new GetAgenticReadinessAssessmentTool(provider)),
      new Entry(ToolDescriptor.LIST_AGENTIC_READINESS_ASSESSMENTS, () -> new ListAgenticReadinessAssessmentsTool(provider, project)),
      new Entry(ToolDescriptor.RUN_ADVANCED_CODE_ANALYSIS,
        () -> new RunAdvancedCodeAnalysisTool(provider, project, Objects.requireNonNull(configuration.getWorkspacePath()), cloud)),
      new Entry(ToolDescriptor.ANALYZE_FILE_LIST, () -> new AnalyzeFileListTool(Objects.requireNonNull(analysis.ideBridge()))),
      new Entry(ToolDescriptor.TOGGLE_AUTOMATIC_ANALYSIS, () -> new ToggleAutomaticAnalysisTool(Objects.requireNonNull(analysis.ideBridge()))),
      new Entry(ToolDescriptor.ANALYZE_CODE_SNIPPET,
        () -> new AnalyzeCodeSnippetTool(backend, provider, initialization, project, configuration.getWorkspacePath(), cloud))));

    var tools = register(entries, configuration.getEnabledToolsets(), configuration.isReadOnlyMode(), facts, analysis.mode());
    var instructions = instructions(tools, facts, proxiedInstructions);
    return new Result(tools, instructions);
  }

  static List<Tool> register(List<Entry> entries, Set<ToolCategory> selectedToolsets, boolean readOnly,
    ToolAvailability.StartupFacts facts, AnalysisToolSelection.Mode analysisMode) {
    var registrar = new ToolRegistrar(selectedToolsets, readOnly);
    for (var entry : entries) {
      var descriptor = entry.descriptor();
      if (descriptor.getTransports().contains(facts.transport())
        && Tool.matchesCategories(descriptor.getCategories(), selectedToolsets)
        && descriptor.getAvailability().isAvailable(facts, analysisMode)) {
        registrar.register(entry.factory().get());
      }
    }
    return registrar.getRegisteredTools();
  }

  private String instructions(List<Tool> tools, ToolAvailability.StartupFacts facts, List<String> proxiedInstructions) {
    var instructions = tools.stream().anyMatch(tool -> tool.getCategories().contains(ToolCategory.ARCHITECTURE)) ? ArchitectureTools.INSTRUCTIONS : "";
    if ((facts.vortexProxy() || facts.vortexAnalysis()) && !configuration.isToolCategoryEnabled(ToolCategory.VORTEX)) {
      LOG.warn("Vortex tools registered via the deprecated 'cag'/'analysis' toolset name(s) - consider adding 'vortex' to SONARQUBE_TOOLSETS instead.");
      instructions += VORTEX_DEPRECATED_TOOLSET_INSTRUCTIONS;
    }
    instructions = ProxiedToolsLoader.composeInstructions(instructions, proxiedInstructions);
    if (tools.stream().anyMatch(tool -> tool.getCategories().contains(ToolCategory.AGENTIC_READINESS))) {
      instructions += AGENTIC_READINESS_INSTRUCTIONS;
    }
    return instructions;
  }

  record Entry(ToolDescriptor descriptor, Supplier<? extends Tool> factory) {
  }

  public record Result(List<Tool> tools, String instructions) {
    public Result {
      tools = List.copyOf(tools);
    }
  }
}
