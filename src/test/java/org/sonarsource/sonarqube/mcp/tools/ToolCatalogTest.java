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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.SonarQubeVersionChecker;
import org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfiguration;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.slcore.BackendService;
import org.sonarsource.sonarqube.mcp.tools.ToolCapabilitiesResolver.Resolution;
import org.sonarsource.sonarqube.mcp.tools.analysis.AnalyzeCodeSnippetTool;
import org.sonarsource.sonarqube.mcp.tools.analysis.AnalyzeFileListTool;
import org.sonarsource.sonarqube.mcp.tools.analysis.RunAdvancedCodeAnalysisTool;
import org.sonarsource.sonarqube.mcp.tools.analysis.ToggleAutomaticAnalysisTool;
import org.sonarsource.sonarqube.mcp.tools.issues.SearchIssuesTool;
import org.sonarsource.sonarqube.mcp.tools.organizations.ListOrganizationsTool;
import org.sonarsource.sonarqube.mcp.tools.projects.SearchMyProjectsTool;
import org.sonarsource.sonarqube.mcp.tools.sources.GetRawSourceTool;
import org.sonarsource.sonarqube.mcp.tools.system.SystemPingTool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.sonarsource.sonarqube.mcp.tools.ToolCapabilities.AnalysisMode.IDE;
import static org.sonarsource.sonarqube.mcp.tools.ToolCapabilities.AnalysisMode.LOCAL;
import static org.sonarsource.sonarqube.mcp.tools.ToolCapabilities.AnalysisMode.NONE;

class ToolCatalogTest {

  private static final ToolCapabilities SERVER = new ToolCapabilities(false, true, false, false, false, NONE);
  private final ServerApiProvider provider = mock(ServerApiProvider.class);
  private final BackendService backend = mock(BackendService.class);
  @TempDir
  Path storage;

  @Test
  void it_should_apply_transport_and_deployment_policies_before_constructing_missing_dependencies() {
    var capabilities = new ToolCapabilities(false, true, false, false, false, IDE);
    var catalog = catalog(Map.of("SONARQUBE_TRANSPORT", "http"), capabilities, List.of());

    assertThat(catalog.supportedTools()).extracting(tool -> tool.definition().name())
      .contains(SystemPingTool.TOOL_NAME)
      .doesNotContain(ListOrganizationsTool.TOOL_NAME, AnalyzeFileListTool.TOOL_NAME, ToggleAutomaticAnalysisTool.TOOL_NAME,
        RunAdvancedCodeAnalysisTool.TOOL_NAME, AnalyzeCodeSnippetTool.TOOL_NAME);
    verifyNoInteractions(provider, backend);
  }

  @Test
  void it_should_keep_supported_tools_separate_from_enabled_tools() {
    var catalog = catalog(Map.of("SONARQUBE_TOOLSETS", "issues"), SERVER, List.of());

    assertThat(catalog.supportedTools()).extracting(tool -> tool.definition().name())
      .contains(SearchIssuesTool.TOOL_NAME, GetRawSourceTool.TOOL_NAME, SearchMyProjectsTool.TOOL_NAME);
    assertThat(catalog.enabledTools()).extracting(tool -> tool.definition().name())
      .contains(SearchIssuesTool.TOOL_NAME, SearchMyProjectsTool.TOOL_NAME)
      .doesNotContain(GetRawSourceTool.TOOL_NAME, SystemPingTool.TOOL_NAME);
    var supported = catalog.supportedTools();
    var enabled = catalog.enabledTools();
    assertThatThrownBy(supported::clear).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(enabled::clear).isInstanceOf(UnsupportedOperationException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"analysis", "cag", "vortex"})
  void it_should_enable_discovered_tools_through_any_category_alias(String toolset) {
    var aliases = ToolPolicy.of(ToolCategory.ANALYSIS, ToolCategory.CAG, ToolCategory.VORTEX);
    var tool = tool("vortex_tool", aliases, true);
    var catalog = catalog(Map.of("SONARQUBE_TOOLSETS", toolset), SERVER, List.of(tool));

    assertThat(catalog.enabledTools()).contains(tool);
  }

  @Test
  void it_should_preserve_defaults_and_fail_closed_for_discovered_tool_annotations() {
    var issues = ToolPolicy.of(ToolCategory.ISSUES);
    var read = tool("read", issues, true);
    var write = tool("write", issues, false);
    var unknown = tool("unknown", issues, null);
    var optional = tool("sources", ToolPolicy.of(ToolCategory.SOURCES), true);
    var catalog = catalog(Map.of("SONARQUBE_READ_ONLY", "true"), SERVER, List.of(read, write, unknown, optional));

    assertThat(catalog.enabledTools()).contains(read).doesNotContain(write, unknown, optional);
    assertThat(catalog.supportedTools()).contains(read, write, unknown, optional);
  }

  @Test
  void it_should_apply_declared_policies_to_discovered_tools() {
    var stdio = tool("stdio", ToolPolicy.of(ToolCategory.ISSUES).stdioOnly(), true);
    var cloud = tool("cloud", ToolPolicy.of(ToolCategory.ISSUES).requires(ToolCapabilities::cloud), true);
    var catalog = catalog(Map.of("SONARQUBE_TRANSPORT", "http"), SERVER, List.of(stdio, cloud));

    assertThat(catalog.supportedTools()).doesNotContain(stdio, cloud);
    assertThat(catalog.enabledTools()).doesNotContain(stdio, cloud);
  }

  @ParameterizedTest
  @ValueSource(strings = {"analysis", "issues"})
  void it_should_download_analyzers_only_for_enabled_local_analysis(String toolset) {
    var capabilities = new ToolCapabilities(false, true, false, false, false, LOCAL);
    var catalog = catalog(Map.of("SONARQUBE_TOOLSETS", toolset, "SONARQUBE_READ_ONLY", "true"), capabilities, List.of());

    assertThat(catalog.needsLocalAnalyzers()).isEqualTo(toolset.equals("analysis"));
    assertThat(catalog.advancedAnalysisRegistered()).isFalse();
    verifyNoInteractions(provider, backend);
  }

  private ToolCatalog catalog(Map<String, String> overrides, ToolCapabilities capabilities, List<Tool> proxiedTools) {
    var environment = new HashMap<>(Map.of("STORAGE_PATH", storage.toString(), "SONARQUBE_TOKEN", "test-token", "SONARQUBE_URL", "http://localhost:9000"));
    environment.putAll(overrides);
    return new ToolCatalog(new McpServerLaunchConfiguration(environment), new Resolution(capabilities, null), provider,
      mock(SonarQubeVersionChecker.class), backend, CompletableFuture.completedFuture(null), proxiedTools);
  }

  private static Tool tool(String name, ToolPolicy policy, Boolean readOnly) {
    var annotations = readOnly == null ? null : new McpSchema.ToolAnnotations(null, readOnly, null, null, null, null);
    var definition = McpSchema.Tool.builder(name, Map.of("type", "object")).annotations(annotations).build();
    return new Tool(definition, policy) {
      @Override
      public Result execute(Arguments arguments) {
        throw new UnsupportedOperationException();
      }
    };
  }
}
