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
import org.sonarsource.sonarqube.mcp.client.ProxiedToolsLoader;
import org.sonarsource.sonarqube.mcp.client.TransportMode;
import org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfiguration;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.ResolvedOrganization;
import org.sonarsource.sonarqube.mcp.slcore.BackendService;
import org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureTools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarqube.mcp.tools.ToolCapabilities.AnalysisMode.LOCAL;
import static org.sonarsource.sonarqube.mcp.tools.ToolCapabilities.AnalysisMode.NONE;

class ToolCatalogTest {

  private static final ToolCapabilities SERVER = new ToolCapabilities(false, true, false, false, false, NONE);
  @TempDir
  Path storage;

  @Test
  void it_should_reject_factories_before_construction() {
    var catalog = new ToolCatalog(configuration(Map.of("SONARQUBE_TRANSPORT", "http")), SERVER);

    assertThat(catalog.register(ToolPolicy.of(ToolCategory.ANALYSIS).stdioOnly(), () -> {
      throw new AssertionError("HTTP must not construct a stdio-only tool");
    })).isNull();
    assertThat(catalog.register(ToolPolicy.of(ToolCategory.PORTFOLIOS).requires(ToolCapabilities::cloud), () -> {
      throw new AssertionError("Server must not construct a Cloud-only tool");
    })).isNull();
    assertThat(catalog.supportedTools()).isEmpty();
  }

  @Test
  void it_should_keep_supported_tools_separate_from_enabled_tools() {
    var catalog = new ToolCatalog(configuration(Map.of("SONARQUBE_TOOLSETS", "issues")), SERVER);
    var issue = tool("issue", ToolPolicy.of(ToolCategory.ISSUES), true);
    var source = tool("source", ToolPolicy.of(ToolCategory.SOURCES), true);
    var project = tool("project", ToolPolicy.of(ToolCategory.PROJECTS), true);
    catalog.register(ToolPolicy.of(ToolCategory.ISSUES), () -> issue);
    catalog.register(ToolPolicy.of(ToolCategory.SOURCES), () -> source);
    catalog.register(ToolPolicy.of(ToolCategory.PROJECTS), () -> project);

    assertThat(catalog.supportedTools()).containsExactly(issue, source, project);
    assertThat(catalog.enabledTools()).containsExactly(issue, project);
    var supportedTools = catalog.supportedTools();
    assertThatThrownBy(supportedTools::clear).isInstanceOf(UnsupportedOperationException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"analysis", "cag", "vortex"})
  void it_should_enable_a_tool_through_any_category_alias(String toolset) {
    var catalog = new ToolCatalog(configuration(Map.of("SONARQUBE_TOOLSETS", toolset)), SERVER);
    var aliases = ToolPolicy.of(ToolCategory.ANALYSIS, ToolCategory.CAG, ToolCategory.VORTEX);
    var tool = tool("vortex_tool", aliases, true);
    catalog.register(aliases, () -> tool);

    assertThat(catalog.enabledTools()).containsExactly(tool);
  }

  @Test
  void it_should_preserve_defaults_and_fail_closed_for_read_only_annotations() {
    var catalog = new ToolCatalog(configuration(Map.of("SONARQUBE_READ_ONLY", "true")), SERVER);
    var issues = ToolPolicy.of(ToolCategory.ISSUES);
    var read = tool("read", issues, true);
    var write = tool("write", issues, false);
    var unknown = tool("unknown", issues, null);
    var optional = tool("sources", ToolPolicy.of(ToolCategory.SOURCES), true);
    catalog.register(issues, () -> read);
    catalog.register(issues, () -> write);
    catalog.register(issues, () -> unknown);
    catalog.register(ToolPolicy.of(ToolCategory.SOURCES), () -> optional);

    assertThat(catalog.enabledTools()).containsExactly(read);
    assertThat(catalog.supportedTools()).hasSize(4);
  }

  @ParameterizedTest
  @ValueSource(strings = {"analysis", "issues"})
  void it_should_download_analyzers_only_for_enabled_local_analysis(String toolset) {
    var capabilities = new ToolCapabilities(false, true, false, false, false, LOCAL);
    var proxy = mock(ProxiedToolsLoader.class);
    var catalog = new ToolCatalog(configuration(Map.of("SONARQUBE_TOOLSETS", toolset, "SONARQUBE_READ_ONLY", "true")), capabilities, proxy);
    var provider = mock(ServerApiProvider.class);
    load(catalog, provider);

    assertThat(catalog.needsLocalAnalyzers()).isEqualTo(toolset.equals("analysis"));
    assertThat(catalog.advancedAnalysisRegistered()).isFalse();
    verifyNoInteractions(provider, proxy);
  }

  @Test
  void it_should_compose_instructions_and_own_proxy_shutdown() {
    var capabilities = new ToolCapabilities(true, true, false, true, true, NONE);
    var configuration = configuration(Map.of("SONARQUBE_IS_CLOUD", "true", "SONARQUBE_TOOLSETS", "architecture,cag,agentic-readiness"));
    var proxy = mock(ProxiedToolsLoader.class);
    var proxyTool = tool("proxied", ToolPolicy.of(ToolCategory.CAG), true);
    when(proxy.loadProxiedTools(eq(TransportMode.STDIO), any(), eq("org"))).thenReturn(List.of(proxyTool));
    when(proxy.getProxiedInstructions()).thenReturn(List.of("Proxy instructions"));
    var catalog = new ToolCatalog(configuration, capabilities, proxy);
    load(catalog, mock(ServerApiProvider.class));

    assertThat(catalog.instructions()).startsWith(ArchitectureTools.INSTRUCTIONS
      + "\nNote: the `cag`/`analysis` toolset names for Vortex tools are deprecated; use `vortex` instead.\n\nProxy instructions");
    assertThat(catalog.instructions()).contains("## Agentic Readiness Assessment");
    assertThat(catalog.supportedTools()).contains(proxyTool);
    assertThat(catalog.needsLocalAnalyzers()).isFalse();
    verify(proxy).loadProxiedTools(TransportMode.STDIO, configuration.getMcpServerId(), "org");
    catalog.shutdown();
    verify(proxy).shutdown();
  }

  private static void load(ToolCatalog catalog, ServerApiProvider provider) {
    catalog.load(provider, mock(SonarQubeVersionChecker.class), mock(BackendService.class), CompletableFuture.completedFuture(null),
      null, ResolvedOrganization.fromKey("org"));
  }

  private McpServerLaunchConfiguration configuration(Map<String, String> overrides) {
    var environment = new HashMap<>(Map.of("STORAGE_PATH", storage.toString(), "SONARQUBE_TOKEN", "test-token", "SONARQUBE_URL", "http://localhost:9000"));
    environment.putAll(overrides);
    return new McpServerLaunchConfiguration(environment);
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
