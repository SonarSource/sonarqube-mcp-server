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
}
