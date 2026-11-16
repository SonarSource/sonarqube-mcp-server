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

import io.modelcontextprotocol.spec.McpSchema;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.SonarQubeVersionChecker;
import org.sonarsource.sonarqube.mcp.bridge.SonarQubeIdeBridgeClient;
import org.sonarsource.sonarqube.mcp.client.McpClientManager;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
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

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.sonarsource.sonarqube.mcp.client.TransportMode.HTTP;
import static org.sonarsource.sonarqube.mcp.client.TransportMode.STDIO;

class ToolDescriptorTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void it_should_supply_categories_for_every_production_tool(boolean cloud) {
    var provider = mock(ServerApiProvider.class);
    var tools = Map.ofEntries(
      entry(ToolDescriptor.GET_AGENTIC_READINESS_ASSESSMENT, new GetAgenticReadinessAssessmentTool(provider)),
      entry(ToolDescriptor.LIST_AGENTIC_READINESS_ASSESSMENTS, new ListAgenticReadinessAssessmentsTool(provider, null)),
      entry(ToolDescriptor.START_AGENTIC_READINESS_ASSESSMENT, new StartAgenticReadinessAssessmentTool(provider, null)),
      entry(ToolDescriptor.ANALYZE_CODE_SNIPPET, new AnalyzeCodeSnippetTool(mock(BackendService.class), provider, new CompletableFuture<>(), null, Path.of("workspace"), cloud)),
      entry(ToolDescriptor.ANALYZE_FILE_LIST, new AnalyzeFileListTool(mock(SonarQubeIdeBridgeClient.class))),
      entry(ToolDescriptor.RUN_ADVANCED_CODE_ANALYSIS, new RunAdvancedCodeAnalysisTool(provider, null, Path.of("workspace"), cloud)),
      entry(ToolDescriptor.TOGGLE_AUTOMATIC_ANALYSIS, new ToggleAutomaticAnalysisTool(mock(SonarQubeIdeBridgeClient.class))),
      entry(ToolDescriptor.ARCHITECTURE_GET, new ArchitectureGetTool(provider, cloud, null)),
      entry(ToolDescriptor.ARCHITECTURE_SET, new ArchitectureSetTool(provider, cloud, null)),
      entry(ToolDescriptor.LIST_BRANCHES, new ListBranchesTool(provider, cloud, null)),
      entry(ToolDescriptor.SEARCH_DEPENDENCY_RISKS, new SearchDependencyRisksTool(provider, mock(SonarQubeVersionChecker.class), null, cloud)),
      entry(ToolDescriptor.GET_DUPLICATIONS, new GetDuplicationsTool(provider)),
      entry(ToolDescriptor.SEARCH_DUPLICATED_FILES, new SearchDuplicatedFilesTool(provider, null)),
      entry(ToolDescriptor.LIST_ENTERPRISES, new ListEnterprisesTool(provider)),
      entry(ToolDescriptor.CHANGE_SECURITY_HOTSPOT_STATUS, new ChangeSecurityHotspotStatusTool(provider)),
      entry(ToolDescriptor.SEARCH_SECURITY_HOTSPOTS, new SearchSecurityHotspotsTool(provider)),
      entry(ToolDescriptor.SHOW_SECURITY_HOTSPOT, new ShowSecurityHotspotTool(provider)),
      entry(ToolDescriptor.CHANGE_ISSUE_STATUS, new ChangeIssueStatusTool(provider)),
      entry(ToolDescriptor.SEARCH_ISSUES, new SearchIssuesTool(provider, cloud)),
      entry(ToolDescriptor.LIST_LANGUAGES, new ListLanguagesTool(provider)),
      entry(ToolDescriptor.GET_COMPONENT_MEASURES, new GetComponentMeasuresTool(provider, null)),
      entry(ToolDescriptor.SEARCH_FILES_BY_COVERAGE, new SearchFilesByCoverageTool(provider, null)),
      entry(ToolDescriptor.SEARCH_METRICS, new SearchMetricsTool(provider)),
      entry(ToolDescriptor.LIST_ORGANIZATIONS, new ListOrganizationsTool(provider)),
      entry(ToolDescriptor.LIST_PORTFOLIOS, new ListPortfoliosTool(provider, cloud)),
      entry(ToolDescriptor.SEARCH_MY_PROJECTS, new SearchMyProjectsTool(provider, cloud)),
      entry(ToolDescriptor.VORTEX_PROXY, new ProxiedMcpTool("vortex", "proxy_tool", McpSchema.Tool.builder("proxy_tool", Map.of("type", "object")).build(), mock(McpClientManager.class))),
      entry(ToolDescriptor.LIST_PULL_REQUESTS, new ListPullRequestsTool(provider, null)),
      entry(ToolDescriptor.LIST_QUALITY_GATES, new ListQualityGatesTool(provider, cloud)),
      entry(ToolDescriptor.PROJECT_STATUS, new ProjectStatusTool(provider)),
      entry(ToolDescriptor.SHOW_RULE, new ShowRuleTool(provider, cloud)),
      entry(ToolDescriptor.GET_FILE_COVERAGE_DETAILS, new GetFileCoverageDetailsTool(provider)),
      entry(ToolDescriptor.GET_RAW_SOURCE, new GetRawSourceTool(provider)),
      entry(ToolDescriptor.GET_SCM_INFO, new GetScmInfoTool(provider)),
      entry(ToolDescriptor.SYSTEM_HEALTH, new SystemHealthTool(provider)),
      entry(ToolDescriptor.SYSTEM_INFO, new SystemInfoTool(provider)),
      entry(ToolDescriptor.SYSTEM_LOGS, new SystemLogsTool(provider)),
      entry(ToolDescriptor.SYSTEM_PING, new SystemPingTool(provider)),
      entry(ToolDescriptor.SYSTEM_STATUS, new SystemStatusTool(provider)),
      entry(ToolDescriptor.CREATE_WEBHOOK, new CreateWebhookTool(provider, cloud)),
      entry(ToolDescriptor.LIST_WEBHOOKS, new ListWebhooksTool(provider, cloud)));

    assertThat(tools).containsOnlyKeys(ToolDescriptor.values());
    tools.forEach((descriptor, tool) -> assertThat(tool.getCategories()).as(tool.definition().name()).isEqualTo(descriptor.getCategories()));
  }

  @ParameterizedTest
  @EnumSource(ToolDescriptor.class)
  void it_should_publish_immutable_complete_metadata(ToolDescriptor descriptor) {
    var categories = descriptor.getCategories();
    var transports = descriptor.getTransports();
    assertThat(categories).isNotEmpty();
    assertThat(descriptor.getAvailability()).isNotNull();
    var stdioOnly = Set.of(ToolDescriptor.ANALYZE_FILE_LIST, ToolDescriptor.TOGGLE_AUTOMATIC_ANALYSIS,
      ToolDescriptor.RUN_ADVANCED_CODE_ANALYSIS, ToolDescriptor.VORTEX_PROXY);
    assertThat(transports).isEqualTo(stdioOnly.contains(descriptor) ? Set.of(STDIO) : Set.of(STDIO, HTTP));
    assertThatThrownBy(categories::clear).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(transports::clear).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void it_should_keep_explicit_overlapping_analysis_memberships() {
    assertThat(ToolDescriptor.ANALYZE_CODE_SNIPPET.getCategories()).containsExactly(ToolCategory.ANALYSIS);
    assertThat(ToolDescriptor.ANALYZE_FILE_LIST.getCategories()).containsExactlyInAnyOrder(ToolCategory.ANALYSIS, ToolCategory.IDE);
    assertThat(ToolDescriptor.TOGGLE_AUTOMATIC_ANALYSIS.getCategories()).containsExactlyInAnyOrder(ToolCategory.ANALYSIS, ToolCategory.IDE);
    assertThat(ToolDescriptor.RUN_ADVANCED_CODE_ANALYSIS.getCategories()).containsExactlyInAnyOrder(ToolCategory.ANALYSIS, ToolCategory.CAG, ToolCategory.VORTEX);
    assertThat(ToolDescriptor.VORTEX_PROXY.getCategories()).isEqualTo(ToolDescriptor.RUN_ADVANCED_CODE_ANALYSIS.getCategories());
  }
}
