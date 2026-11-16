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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.SonarQubeVersionChecker;
import org.sonarsource.sonarqube.mcp.client.TransportMode;
import org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfiguration;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.slcore.BackendService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ToolCatalogTest {

  @Test
  void it_should_construct_only_eligible_factories_once_and_keep_their_instances_in_order() {
    var constructions = new AtomicInteger();
    var first = tool("first", ToolDescriptor.SEARCH_MY_PROJECTS, true);
    var second = tool("second", ToolDescriptor.SEARCH_ISSUES, true);
    var entries = List.of(
      new ToolCatalog.Entry(ToolDescriptor.SEARCH_MY_PROJECTS, () -> {
        constructions.incrementAndGet();
        return first;
      }),
      new ToolCatalog.Entry(ToolDescriptor.SEARCH_ISSUES, () -> {
        constructions.incrementAndGet();
        return second;
      }),
      unavailable(ToolDescriptor.RUN_ADVANCED_CODE_ANALYSIS),
      unavailable(ToolDescriptor.LIST_ENTERPRISES),
      unavailable(ToolDescriptor.ARCHITECTURE_GET));
    var facts = new ToolAvailability.StartupFacts(TransportMode.HTTP, false, true, false, false, false, false);

    var tools = ToolCatalog.register(entries, Set.of(ToolCategory.ISSUES, ToolCategory.VORTEX, ToolCategory.PORTFOLIOS), false,
      facts, AnalysisToolSelection.Mode.VORTEX);

    assertThat(tools).containsExactly(first, second);
    assertThat(constructions).hasValue(2);
    assertThatThrownBy(() -> tools.add(first)).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void it_should_apply_read_only_after_lazy_construction() {
    var read = tool("read", ToolDescriptor.SEARCH_ISSUES, true);
    var write = tool("write", ToolDescriptor.CHANGE_ISSUE_STATUS, false);
    var facts = new ToolAvailability.StartupFacts(TransportMode.STDIO, true, false, false, false, false, false);

    assertThat(ToolCatalog.register(List.of(new ToolCatalog.Entry(ToolDescriptor.SEARCH_ISSUES, () -> read),
        new ToolCatalog.Entry(ToolDescriptor.CHANGE_ISSUE_STATUS, () -> write)), Set.of(ToolCategory.ISSUES), true,
      facts, AnalysisToolSelection.Mode.NONE)).containsExactly(read);
  }

  @Test
  void it_should_preserve_discovery_order_and_group_instructions_without_resolving_an_invocation_api() {
    var configuration = mock(McpServerLaunchConfiguration.class);
    when(configuration.isSonarQubeCloud()).thenReturn(true);
    when(configuration.getEnabledToolsets()).thenReturn(ToolCategory.all());
    var provider = mock(ServerApiProvider.class);
    var catalog = new ToolCatalog(configuration, provider, mock(BackendService.class), mock(SonarQubeVersionChecker.class), new CompletableFuture<>());
    var proxy = tool("proxied_context", ToolDescriptor.VORTEX_PROXY, true);
    var facts = new ToolAvailability.StartupFacts(TransportMode.STDIO, true, true, true, true, true, false);

    var result = catalog.create(facts, new AnalysisToolSelection(AnalysisToolSelection.Mode.NONE, null), List.of(proxy), List.of("Proxy instructions"));

    assertThat(result.tools()).extracting(tool -> tool.definition().name()).containsSubsequence("list_enterprises", "list_organizations",
      "change_sonar_issue_status", "architecture_get", "architecture_set", "search_dependency_risks", "proxied_context",
      "start_agentic_readiness_assessment", "get_agentic_readiness_assessment", "list_agentic_readiness_assessments");
    assertThat(result.tools()).contains(proxy);
    assertThat(result.instructions()).contains("## Architecture", "Proxy instructions", "## Agentic Readiness Assessment");
    verifyNoInteractions(provider);
  }

  @Test
  void it_should_forward_proxy_instructions_even_when_read_only_rejects_its_tools() {
    var configuration = mock(McpServerLaunchConfiguration.class);
    when(configuration.getEnabledToolsets()).thenReturn(Set.of(ToolCategory.VORTEX));
    when(configuration.isReadOnlyMode()).thenReturn(true);
    var catalog = new ToolCatalog(configuration, mock(ServerApiProvider.class), mock(BackendService.class),
      mock(SonarQubeVersionChecker.class), new CompletableFuture<>());
    var proxy = tool("proxied_write", ToolDescriptor.VORTEX_PROXY, false);
    var facts = new ToolAvailability.StartupFacts(TransportMode.STDIO, false, false, false, false, true, false);

    var result = catalog.create(facts, new AnalysisToolSelection(AnalysisToolSelection.Mode.NONE, null), List.of(proxy), List.of("Proxy instructions"));

    assertThat(result.tools()).doesNotContain(proxy);
    assertThat(result.instructions()).contains("Proxy instructions").doesNotContain("## Architecture", "## Agentic Readiness Assessment");
  }

  private static ToolCatalog.Entry unavailable(ToolDescriptor descriptor) {
    return new ToolCatalog.Entry(descriptor, () -> {
      throw new AssertionError("Unavailable factory was called: " + descriptor);
    });
  }

  private static Tool tool(String name, ToolDescriptor descriptor, boolean readOnly) {
    var definition = McpSchema.Tool.builder(name, Map.of("type", "object"))
      .annotations(new McpSchema.ToolAnnotations(null, readOnly, null, null, null, null)).build();
    return new Tool(definition, descriptor) {
      @Override
      public Result execute(Arguments arguments) {
        return Result.failure("unused");
      }
    };
  }
}
