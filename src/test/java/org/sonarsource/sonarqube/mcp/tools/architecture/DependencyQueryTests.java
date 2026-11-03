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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.StructureGraph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DependencyQueryTests {
  private static StructureGraph.Node node(int id, String name, List<StructureGraph.Node> children, StructureGraph.Edge... edges) {
    return new StructureGraph.Node(id, name, "module", null, List.of(edges), children);
  }

  private static GraphSlicer.LanguageGraph graph(String language, StructureGraph.Node... nodes) {
    return new GraphSlicer.LanguageGraph(language, new StructureGraph(0, 0, List.of(nodes)));
  }

  private static DependencyQuery.Selection selection(int page, int size) {
    return new DependencyQuery.Selection("compat", "impl", page, size);
  }

  private static GraphSlicer.LanguageGraph fixture() {
    return graph("java", node(1, "compat", List.of(node(2, "a", List.of(
      node(3, "A", List.of(), new StructureGraph.Edge(6, 4)), node(4, "B", List.of(), new StructureGraph.Edge(6, 3)))))),
      node(5, "impl", List.of(node(6, "C", List.of(), new StructureGraph.Edge(3, 8)))));
  }

  @Test
  void it_should_aggregate_directionally_and_keep_leaf_endpoints() {
    var graphs = List.of(fixture());
    var query = selection(1, 20);
    assertThat(DependencyQuery.query("p", graphs, query, 1).dependencies())
      .containsExactly(new DependencyQuery.Dependency("compat:a", "impl:C", 7));
    assertThat(DependencyQuery.query("p", graphs, query, 2).dependencies()).hasSize(2);
    var reverse = new DependencyQuery.Selection("impl", "compat", 1, 20);
    assertThat(DependencyQuery.query("p", graphs, reverse, 1).dependencies())
      .containsExactly(new DependencyQuery.Dependency("impl:C", "compat:a", 8));
    var leaves = new DependencyQuery.Selection("compat:a:A", "impl:C", 1, 20);
    assertThat(DependencyQuery.query("p", graphs, leaves, 9).dependencies())
      .containsExactly(new DependencyQuery.Dependency("compat:a:A", "impl:C", 4));
  }

  @Test
  void it_should_page_all_matches_beyond_overview_caps_without_revision_changes() {
    var children = IntStream.range(0, 160).mapToObj(i -> node(10 + i, "a" + i, List.of(), new StructureGraph.Edge(200, 1))).toList();
    var graphs = List.of(graph("java", node(1, "compat", children), node(2, "impl", List.of(node(200, "target", List.of())))));
    var all = new ArrayList<DependencyQuery.Dependency>();
    var revisions = new java.util.HashSet<String>();
    for (var page = 1; page <= 8; page++) {
      var response = DependencyQuery.query("p", graphs, selection(page, 20), 1);
      all.addAll(response.dependencies());
      revisions.add(response.revision());
      assertThat(response.page().total()).isEqualTo(160);
      assertThat(response.page().hasNextPage()).isEqualTo(page < 8);
    }
    assertThat(all).hasSize(160).doesNotHaveDuplicates();
    assertThat(revisions).hasSize(1);
    assertThat(DependencyQuery.query("p", graphs, selection(Integer.MAX_VALUE, 20), 1).dependencies()).isEmpty();
  }

  @Test
  void it_should_hash_matching_results_not_page_or_backend_order() {
    var first = fixture();
    var reordered = new GraphSlicer.LanguageGraph("java", new StructureGraph(0, 0, first.graph().nodes().reversed()));
    var response = DependencyQuery.query("p", List.of(first), selection(1, 1), 2);
    assertThat(DependencyQuery.query("p", List.of(reordered), selection(2, 2), 2).revision()).isEqualTo(response.revision());
    var changed = graph("java", node(1, "compat", List.of(node(2, "a", List.of(node(3, "A", List.of(), new StructureGraph.Edge(6, 5)))))),
      node(5, "impl", List.of(node(6, "C", List.of()))));
    assertThat(DependencyQuery.query("p", List.of(changed), selection(1, 1), 2).revision()).isNotEqualTo(response.revision());
  }

  @Test
  void it_should_omit_nested_aggregate_rows_but_keep_deeper_dependencies() {
    var nested = List.of(graph("java", node(1, "compat", List.of(node(2, "a", List.of(
      node(3, "A", List.of(), new StructureGraph.Edge(4, 9)), node(4, "B", List.of())))))));
    var forward = new DependencyQuery.Selection("compat", "compat:a", 1, 20);
    var reverse = new DependencyQuery.Selection("compat:a", "compat", 1, 20);
    assertThat(DependencyQuery.query("p", nested, forward, 1).dependencies()).isEmpty();
    assertThat(DependencyQuery.query("p", nested, reverse, 1).dependencies()).isEmpty();
    assertThat(DependencyQuery.query("p", nested, forward, 2).dependencies())
      .containsExactly(new DependencyQuery.Dependency("compat:a:A", "compat:a:B", 9));
    assertThat(DependencyQuery.query("p", nested, reverse, 2).dependencies())
      .containsExactly(new DependencyQuery.Dependency("compat:a:A", "compat:a:B", 9));
  }

  @Test
  void it_should_not_index_an_unrelated_malformed_language() {
    var unrelated = graph("py", node(99, "other", List.of()), node(99, "unrelated", List.of()));
    assertThat(DependencyQuery.query("p", List.of(unrelated, fixture()), selection(1, 20), 1).dependencies())
      .containsExactly(new DependencyQuery.Dependency("compat:a", "impl:C", 7));
  }

  @Test
  void it_should_not_combine_graphs_or_accept_ambiguous_endpoints() {
    var query = selection(1, 20);
    var graphs = List.of(fixture(), new GraphSlicer.LanguageGraph("ts", fixture().graph()));
    assertThatThrownBy(() -> DependencyQuery.query("p", graphs, query, 1)).hasMessageContaining("Specify language");
    var separate = List.of(graph("java", node(1, "compat", List.of())), graph("ts", node(2, "impl", List.of())));
    assertThatThrownBy(() -> DependencyQuery.query("p", separate, query, 1)).hasMessageContaining("same current graph");
  }

  @Test
  void it_should_refuse_malformed_or_aggregated_edges_without_claiming_completeness() {
    var query = selection(1, 20);
    var duplicateIds = List.of(graph("java", node(1, "compat", List.of()), node(1, "impl", List.of())));
    assertThatThrownBy(() -> DependencyQuery.query("p", duplicateIds, query, 1)).hasMessageContaining("duplicate node");
    var dangling = List.of(graph("java", node(1, "compat", List.of(), new StructureGraph.Edge(999, 1)), node(2, "impl", List.of())));
    assertThatThrownBy(() -> DependencyQuery.query("p", dangling, query, 1)).hasMessageContaining("unresolved");
    var duplicateEdges = List.of(graph("java", node(1, "compat", List.of(), new StructureGraph.Edge(2, 1), new StructureGraph.Edge(2, 1)), node(2, "impl", List.of())));
    assertThatThrownBy(() -> DependencyQuery.query("p", duplicateEdges, query, 1)).hasMessageContaining("duplicate dependency");
    var containers = List.of(graph("java", node(1, "compat", List.of(node(3, "A", List.of())), new StructureGraph.Edge(2, 1)), node(2, "impl", List.of())));
    assertThatThrownBy(() -> DependencyQuery.query("p", containers, query, 1)).hasMessageContaining("leaf-level");
  }

  @Test
  void it_should_use_long_weights_and_utf16_key_order() {
    var query = selection(1, 20);
    var graphs = List.of(graph("java", node(1, "compat", List.of(node(3, "😀", List.of(), new StructureGraph.Edge(7, 1)),
      node(4, "\ue000", List.of(), new StructureGraph.Edge(7, 1)))), node(2, "impl", List.of(node(7, "x", List.of())))));
    var result = DependencyQuery.query("p", graphs, query, 1);
    assertThat(result.dependencies().stream().map(DependencyQuery.Dependency::from)).containsExactly("compat:😀", "compat:\ue000");
    var large = List.of(graph("java", node(1, "compat", List.of(node(2, "a", List.of(node(3, "A", List.of(), new StructureGraph.Edge(6, Integer.MAX_VALUE)),
      node(4, "B", List.of(), new StructureGraph.Edge(6, Integer.MAX_VALUE)))))), node(5, "impl", List.of(node(6, "C", List.of())))));
    assertThat(DependencyQuery.query("p", large, query, 1).dependencies().getFirst().weight()).isEqualTo(4294967294L);
  }

  @Test
  void it_should_handle_overlapping_endpoints_and_invalid_pages() {
    var graphs = List.of(fixture());
    var same = new DependencyQuery.Selection("compat", "compat", 1, 20);
    assertThat(DependencyQuery.query("p", graphs, same, 1).dependencies()).isEmpty();
    assertThatThrownBy(() -> new DependencyQuery.Selection("compat", "impl", 0, 20)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new DependencyQuery.Selection("compat", "impl", 1, 21)).isInstanceOf(IllegalArgumentException.class);
  }
}
