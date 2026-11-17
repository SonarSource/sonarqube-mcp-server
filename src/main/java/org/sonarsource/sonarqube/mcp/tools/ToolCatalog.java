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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.sonarsource.sonarqube.mcp.SonarQubeVersionChecker;
import org.sonarsource.sonarqube.mcp.client.TransportMode;
import org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfiguration;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.slcore.BackendService;
import org.sonarsource.sonarqube.mcp.tools.ToolCapabilitiesResolver.Resolution;
import org.sonarsource.sonarqube.mcp.tools.agenticreadiness.GetAgenticReadinessAssessmentTool;
import org.sonarsource.sonarqube.mcp.tools.agenticreadiness.ListAgenticReadinessAssessmentsTool;
import org.sonarsource.sonarqube.mcp.tools.agenticreadiness.StartAgenticReadinessAssessmentTool;
import org.sonarsource.sonarqube.mcp.tools.analysis.AnalyzeCodeSnippetTool;
import org.sonarsource.sonarqube.mcp.tools.analysis.AnalyzeFileListTool;
import org.sonarsource.sonarqube.mcp.tools.analysis.RunAdvancedCodeAnalysisTool;
import org.sonarsource.sonarqube.mcp.tools.analysis.ToggleAutomaticAnalysisTool;
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

/** Explicit construction wiring; each tool owns its registration policy. */
public final class ToolCatalog {

  private static final McpLogger LOG = McpLogger.getInstance();
  private final McpServerLaunchConfiguration configuration;
  private final ToolCapabilities capabilities;
  private final TransportMode transport;
  private final List<Tool> supportedTools = new ArrayList<>();
  private final List<Tool> enabledTools;

  public ToolCatalog(McpServerLaunchConfiguration configuration, Resolution resolution, ServerApiProvider provider,
    SonarQubeVersionChecker versionChecker, BackendService backendService, CompletableFuture<Void> initializationFuture, List<Tool> proxiedTools) {
    this.configuration = configuration;
    this.capabilities = resolution.capabilities();
    this.transport = configuration.getTransportMode();
    loadRestTools(provider, versionChecker);
    proxiedTools.forEach(this::register);

    var projectKey = configuration.getProjectKey();
    var cloud = configuration.isSonarQubeCloud();
    register(new StartAgenticReadinessAssessmentTool(provider, projectKey));
    register(new GetAgenticReadinessAssessmentTool(provider));
    register(new ListAgenticReadinessAssessmentsTool(provider, projectKey));
    register(RunAdvancedCodeAnalysisTool.POLICY,
      () -> new RunAdvancedCodeAnalysisTool(provider, projectKey, Objects.requireNonNull(configuration.getWorkspacePath()), cloud));
    register(AnalyzeFileListTool.POLICY, () -> new AnalyzeFileListTool(Objects.requireNonNull(resolution.bridgeClient())));
    register(ToggleAutomaticAnalysisTool.POLICY, () -> new ToggleAutomaticAnalysisTool(Objects.requireNonNull(resolution.bridgeClient())));
    register(new AnalyzeCodeSnippetTool(backendService, provider, initializationFuture, projectKey, configuration.getWorkspacePath(), cloud));
    enabledTools = filterEnabled(supportedTools);

    var filterReason = configuration.isReadOnlyMode() ? "category and read-only filtering" : "category filtering";
    LOG.info("All tools loaded: " + enabledTools.size() + " tools after " + filterReason);
  }

  private void loadRestTools(ServerApiProvider provider, SonarQubeVersionChecker versionChecker) {
    var projectKey = configuration.getProjectKey();
    var cloud = configuration.isSonarQubeCloud();
    register(new ListEnterprisesTool(provider));
    register(new ListOrganizationsTool(provider));
    register(new SystemHealthTool(provider));
    register(new SystemInfoTool(provider));
    register(new SystemLogsTool(provider));
    register(new SystemPingTool(provider));
    register(new SystemStatusTool(provider));
    register(new ChangeIssueStatusTool(provider));
    register(new SearchMyProjectsTool(provider, cloud));
    register(new SearchIssuesTool(provider, cloud));
    register(new SearchSecurityHotspotsTool(provider));
    register(new ShowSecurityHotspotTool(provider));
    register(new ChangeSecurityHotspotStatusTool(provider));
    register(new ProjectStatusTool(provider));
    register(new ShowRuleTool(provider, cloud));
    register(new ListQualityGatesTool(provider, cloud));
    register(new ListLanguagesTool(provider));
    register(new GetComponentMeasuresTool(provider, projectKey));
    register(new SearchFilesByCoverageTool(provider, projectKey));
    register(new GetFileCoverageDetailsTool(provider));
    register(new SearchMetricsTool(provider));
    register(new GetScmInfoTool(provider));
    register(new GetRawSourceTool(provider));
    register(new CreateWebhookTool(provider, cloud));
    register(new ListWebhooksTool(provider, cloud));
    register(new GetDuplicationsTool(provider));
    register(new SearchDuplicatedFilesTool(provider, projectKey));
    register(new ListPortfoliosTool(provider, cloud));
    register(new ListPullRequestsTool(provider, projectKey));
    register(new ListBranchesTool(provider, cloud, projectKey));
    ArchitectureTools.create(provider, cloud, projectKey).forEach(this::register);
    register(new SearchDependencyRisksTool(provider, versionChecker, projectKey, cloud));
  }

  private void register(Tool tool) {
    if (tool.policy().isSupported(transport, capabilities)) {
      supportedTools.add(tool);
    }
  }

  private void register(ToolPolicy policy, Supplier<? extends Tool> factory) {
    if (policy.isSupported(transport, capabilities)) {
      register(factory.get());
    }
  }

  public List<Tool> supportedTools() {
    return List.copyOf(supportedTools);
  }

  public List<Tool> enabledTools() {
    return enabledTools;
  }

  public List<Tool> filterEnabled(List<Tool> tools) {
    return tools.stream()
      .filter(tool -> tool.getCategories().stream().anyMatch(configuration::isToolCategoryEnabled))
      .filter(tool -> !configuration.isReadOnlyMode() || (tool.definition().annotations() != null && Boolean.TRUE.equals(tool.definition().annotations().readOnlyHint())))
      .toList();
  }

  public boolean needsLocalAnalyzers() {
    return enabledTools.stream().anyMatch(AnalyzeCodeSnippetTool.class::isInstance);
  }

  public boolean advancedAnalysisRegistered() {
    return supportedTools.stream().anyMatch(RunAdvancedCodeAnalysisTool.class::isInstance);
  }
}
