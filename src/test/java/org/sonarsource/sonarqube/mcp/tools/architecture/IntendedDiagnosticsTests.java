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
package org.sonarsource.sonarqube.mcp.tools.architecture;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.IntendedArchitecture;
import org.sonarsource.sonarqube.mcp.serverapi.branches.ProjectBranchesApi;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.StructureGraph;
import static org.assertj.core.api.Assertions.assertThat;

class IntendedDiagnosticsTests {
  private static final String GRAPH = """
    {"nodeCount":5,"edgeCount":0,"nodes":[{"id":1,"name":"gctoolkit-parser","kind":"module","nodes":[
      {"id":2,"name":"com.microsoft.gctoolkit.parser","kind":"package","nodes":[
        {"id":3,"name":"jvm","kind":"package","nodes":[{"id":5,"name":"Parser","kind":"class"}]},
        {"id":4,"name":"unified","kind":"package"}]}]}]}
    """;
  private static final String MODEL = """
    [{"type":"namespace","language":"java","constraints":[],"groups":[
      {"label":"gctoolkit-parser","patterns":["gctoolkit-parser:**"],"groups":[
        {"label":"com.microsoft.gctoolkit.parser.jvm","patterns":["gctoolkit-parser:com.microsoft.gctoolkit.parser.jvm"]},
        {"label":"com.microsoft.gctoolkit.parser.unified","patterns":["gctoolkit-parser:com.microsoft.gctoolkit.parser.unified"]}]}]}]
    """;

  @Test
  void it_should_not_acquire_capacity_when_no_groups_need_discovery() {
    var api = mock(ServerApi.class);
    var reads = mock(CurrentGraphReads.class);
    assertThat(IntendedDiagnostics.inspect(api, "project", List.of(), reads)).isEmpty();
    var empty = new IntendedArchitecture("namespace", "java", List.of(), List.of());
    assertThat(IntendedDiagnostics.inspect(api, "project", List.of(empty), reads)).isEmpty();
    org.mockito.Mockito.verifyNoInteractions(api, reads);
  }

  @Test
  void it_should_diagnose_missing_wildcard_and_skipped_parent_without_rewriting() {
    var models = IntendedModels.parse(MODEL);
    var graph = new Gson().fromJson(GRAPH, StructureGraph.class);
    var warnings = IntendedDiagnostics.inspect(models, graph);
    assertThat(warnings).anyMatch(warning -> warning.contains("names a container"));
    assertThat(warnings.stream().filter(warning -> warning.contains("different parent"))).hasSize(2);
    assertThat(new Gson().toJson(models)).isEqualTo(new Gson().toJson(IntendedModels.parse(MODEL)));
  }

  @Test
  void it_should_accept_exact_leaf_patterns_and_complete_physical_chains() {
    var model = """
      [{"type":"namespace","language":"java","constraints":[],"groups":[
        {"label":"gctoolkit-parser","patterns":["gctoolkit-parser:**"],"groups":[
          {"label":"com.microsoft.gctoolkit.parser","patterns":["gctoolkit-parser:com.microsoft.gctoolkit.parser.**"],"groups":[
            {"label":"jvm","patterns":["gctoolkit-parser:com.microsoft.gctoolkit.parser.jvm.**"]},
            {"label":"unified","patterns":["gctoolkit-parser:com.microsoft.gctoolkit.parser.unified"]}]}]}]}]
      """;
    assertThat(IntendedDiagnostics.inspect(IntendedModels.parse(model), new Gson().fromJson(GRAPH, StructureGraph.class))).isEmpty();
  }

  @Test
  void it_should_not_invent_errors_for_logical_or_imported_groups() {
    var model = """
      [{"type":"namespace","language":"java","constraints":[],"groups":[
        {"label":"Logical","patterns":["future/**"]},
        {"label":"Imported","patternId":"11111111-1111-4111-8111-111111111111","patterns":["gctoolkit-parser"],"groups":[]}]}]
      """;
    assertThat(IntendedDiagnostics.inspect(IntendedModels.parse(model), new Gson().fromJson(GRAPH, StructureGraph.class))).isEmpty();
  }
  @Test
  void it_should_keep_unavailable_languages_and_count_omitted_warnings() {
    var api = mock(ServerApi.class);
    var architecture = mock(ArchitectureApi.class);
    var branches = mock(ProjectBranchesApi.class);
    when(api.architectureApi()).thenReturn(architecture);
    when(api.projectBranchesApi()).thenReturn(branches);
    when(branches.getProjectId("project")).thenReturn(Optional.of("branch"));
    when(architecture.getStructure("project", ArchitectureApi.SERVER_ORGANIZATION_ID, "branch", "java"))
      .thenReturn(List.of(new Gson().fromJson(GRAPH, StructureGraph.class)));
    var backendReason = "The response does not match the supported public Architecture API contract. Check compatible API versions.";
    when(architecture.getStructure("project", ArchitectureApi.SERVER_ORGANIZATION_ID, "branch", "ts"))
      .thenThrow(new IllegalStateException(backendReason));
    var groups = IntStream.range(0, 13).mapToObj(i -> new IntendedArchitecture.Group("group" + i,
      List.of("gctoolkit-parser"), null, null, null)).toList();
    var models = List.of(new IntendedArchitecture(null, "namespace", "java", groups, List.of()),
      new IntendedArchitecture(null, "file", "ts", groups, List.of()));

    var warnings = IntendedDiagnostics.inspect(api, "project", models, new CurrentGraphReads());

    assertThat(warnings.getFirst()).contains("ts: pattern and physical hierarchy checks unavailable", backendReason, "Structural validation still applies");
    assertThat(warnings.stream().filter(warning -> warning.contains("names a container"))).hasSize(10);
    assertThat(warnings.getLast()).isEqualTo("3 additional discovery warning(s) omitted.");
    assertThat(warnings).hasSize(12);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void it_should_keep_discovery_best_effort_when_the_cause_has_no_message(String reason) {
    var api = mock(ServerApi.class);
    var architecture = mock(ArchitectureApi.class);
    var branches = mock(ProjectBranchesApi.class);
    when(api.architectureApi()).thenReturn(architecture);
    when(api.projectBranchesApi()).thenReturn(branches);
    when(branches.getProjectId("project")).thenReturn(Optional.of("branch"));
    when(architecture.getStructure("project", ArchitectureApi.SERVER_ORGANIZATION_ID, "branch", "java"))
      .thenThrow(new IllegalStateException(reason));

    var warnings = IntendedDiagnostics.inspect(api, "project", IntendedModels.parse(MODEL), new CurrentGraphReads());

    assertThat(warnings).singleElement().asString()
      .contains("checks unavailable", "Structural validation still applies")
      .doesNotContain("(null)", "()");
  }

}
