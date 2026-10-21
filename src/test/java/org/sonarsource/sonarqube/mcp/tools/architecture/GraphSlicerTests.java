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
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.StructureGraph;

import static org.assertj.core.api.Assertions.assertThat;

class GraphSlicerTests {

  @Test
  void it_should_keep_whole_levels_and_report_the_depth_that_fit() {
    var roots = IntStream.range(0, 78).mapToObj(id -> node(id, "module" + id, "module", List.of(),
      IntStream.range(0, 2).mapToObj(child -> node(1000 + 2 * id + child, "package" + child, "package", List.of(), List.of())).toList())).toList();

    var response = GraphSlicer.slice("project", List.of(graph("java", roots)), null, 3);

    assertThat(response.requestedDepth()).isEqualTo(3);
    assertThat(response.depth()).isEqualTo(1);
    assertThat(response.capped()).isTrue();
    var shown = response.graphs().getFirst();
    assertThat(shown.nodes()).hasSize(78);
    assertThat(shown.nodesInScope()).isEqualTo(234);
    assertThat(shown.depth()).isEqualTo(1);
    assertThat(shown.partialLevel()).isNull();
    assertThat(shown.omittedNodes()).isEqualTo(156);
    assertThat(response.drillDown()).hasSize(GraphSlicer.MAX_DRILL_DOWN);
  }

  @Test
  void it_should_share_nodes_without_hiding_a_small_language() {
    var response = GraphSlicer.slice("project", List.of(graph("java", roots(262)), graph("py", roots(7))), null, 1);

    assertThat(response.graphs()).hasSize(2);
    var javaGraph = response.graphs().getFirst();
    var python = response.graphs().getLast();
    assertThat(javaGraph.nodes()).hasSize(143);
    assertThat(javaGraph.capped()).isTrue();
    assertThat(javaGraph.depth()).isZero();
    assertThat(javaGraph.partialLevel()).isEqualTo(1);
    assertThat(javaGraph.omittedNodes()).isEqualTo(119);
    assertThat(python.nodes()).hasSize(7);
    assertThat(python.depth()).isEqualTo(1);
    assertThat(python.capped()).isFalse();
    assertThat(python.omittedNodes()).isZero();
    assertThat(response.graphs().stream().mapToInt(value -> value.nodes().size()).sum()).isEqualTo(GraphSlicer.MAX_NODES);
    assertThat(response.depth()).isZero();
  }

  @Test
  void it_should_share_the_node_budget_evenly_between_large_languages() {
    var response = GraphSlicer.slice("project", List.of(graph("java", roots(200)), graph("ts", roots(200))), null, 1);

    assertThat(response.graphs()).allSatisfy(value -> {
      assertThat(value.nodes()).hasSize(75);
      assertThat(value.capped()).isTrue();
      assertThat(value.omittedNodes()).isEqualTo(125);
      assertThat(value.partialLevel()).isEqualTo(1);
    });
  }

  @Test
  void it_should_limit_dependencies_and_feedback_pairs_across_all_languages() {
    var response = GraphSlicer.slice("project", List.of(denseGraph("java", "java", 1), denseGraph("py", "python", 100)), null, 1);

    assertThat(response.graphs().stream().mapToInt(value -> value.edges().size()).sum()).isEqualTo(GraphSlicer.MAX_EDGES);
    assertThat(response.graphs().stream().mapToInt(value -> value.feedbackPairs().heaviest().size()).sum()).isEqualTo(GraphSlicer.MAX_FEEDBACK_PAIRS);
    var javaGraph = response.graphs().getFirst();
    var python = response.graphs().getLast();
    assertThat(javaGraph.edges()).isEmpty();
    assertThat(javaGraph.feedbackPairs().heaviest()).isEmpty();
    assertThat(javaGraph.feedbackPairs().total()).isEqualTo(15);
    assertThat(python.edges()).hasSize(20);
    assertThat(python.feedbackPairs().heaviest()).hasSize(5);
    assertThat(javaGraph.edgesInScope()).isEqualTo(30);
    assertThat(javaGraph.omittedEdges()).isEqualTo(30);
    assertThat(python.omittedEdges()).isEqualTo(10);
    assertThat(response.capped()).isTrue();
    assertThat(response.message()).contains("20 of 60 dependencies between shown nodes");
    response.graphs().forEach(value -> {
      var keys = value.nodes().stream().map(ArchitectureGetCurrentToolResponse.Node::key).collect(java.util.stream.Collectors.toSet());
      assertThat(value.edges()).allSatisfy(edge -> assertThat(keys).contains(edge.from(), edge.to()));
    });
  }

  @Test
  void it_should_keep_a_deterministic_edge_order_when_weights_tie() {
    var first = GraphSlicer.slice("project", List.of(denseGraph("java", "same", 1)), null, 1);
    var second = GraphSlicer.slice("project", List.of(denseGraph("java", "same", 1)), null, 1);

    assertThat(first).isEqualTo(second);
    assertThat(first.graphs().getFirst().edges()).isSortedAccordingTo(java.util.Comparator
      .comparingInt(ArchitectureGetCurrentToolResponse.Edge::weight).reversed()
      .thenComparing(ArchitectureGetCurrentToolResponse.Edge::from).thenComparing(ArchitectureGetCurrentToolResponse.Edge::to));
  }

  @Test
  void it_should_keep_exact_long_keys_for_nodes_edges_and_drill_down() {
    var prefix = "longpackage".repeat(100);
    var children = IntStream.range(0, 30).mapToObj(id -> node(id + 1, prefix + id, "package",
      List.of(new StructureGraph.Edge((id + 1) % 30 + 1, id + 1)), List.of(node(100 + id, "Class", "class", List.of(), List.of())))).toList();
    var response = GraphSlicer.slice("project", List.of(graph("java", List.of(node(0, "app", "module", List.of(), children)))), "app", 1);

    assertThat(response.graphs().getFirst().nodes()).hasSize(30);
    assertThat(response.graphs().getFirst().edges()).hasSize(20);
    assertThat(response.graphs().getFirst().nodes().getFirst().key()).isEqualTo("app:" + prefix + "0");
    assertThat(response.graphs().getFirst().edges()).allSatisfy(edge -> {
      assertThat(edge.from()).startsWith("app:" + prefix);
      assertThat(edge.to()).startsWith("app:" + prefix);
    });
    assertThat(response.drillDown()).allSatisfy(value -> assertThat(value.component()).startsWith("app:" + prefix));
    assertThat(new Gson().toJson(response)).doesNotContain("…");
  }

  @Test
  void it_should_roll_dependencies_up_without_returning_unshown_endpoints() {
    var source = node(1, "source", "module", List.of(), List.of(node(3, "C", "class", List.of(new StructureGraph.Edge(4, 7)), List.of())));
    var target = node(2, "target", "module", List.of(), List.of(node(4, "C", "class", List.of(), List.of())));

    var response = GraphSlicer.slice("project", List.of(graph("java", List.of(source, target))), null, 1);

    assertThat(response.graphs().getFirst().edges()).containsExactly(new ArchitectureGetCurrentToolResponse.Edge("source", "target", 7));
    assertThat(response.graphs().getFirst().nodes().stream().map(ArchitectureGetCurrentToolResponse.Node::key).toList())
      .containsExactly("source", "target");
  }

  @Test
  void it_should_keep_an_empty_graph_distinct_from_a_capped_graph() {
    var response = GraphSlicer.slice("project", List.of(graph("java", List.of())), null, 3);

    assertThat(response.depth()).isZero();
    assertThat(response.requestedDepth()).isEqualTo(3);
    assertThat(response.capped()).isFalse();
    assertThat(response.graphs().getFirst().capped()).isFalse();
    assertThat(response.graphs().getFirst().omittedNodes()).isZero();
    assertThat(response.message()).isNull();
  }

  private static GraphSlicer.LanguageGraph denseGraph(String language, String prefix, int weight) {
    var nodes = IntStream.range(0, 6).mapToObj(id -> node(id, prefix + id, "module",
      IntStream.range(0, 6).filter(to -> to != id).mapToObj(to -> new StructureGraph.Edge(to, weight)).toList(), List.of())).toList();
    return graph(language, nodes);
  }

  private static List<StructureGraph.Node> roots(int count) {
    return IntStream.range(0, count).mapToObj(id -> node(id, "root" + id, "module", List.of(), List.of())).toList();
  }

  private static GraphSlicer.LanguageGraph graph(String language, List<StructureGraph.Node> roots) {
    return new GraphSlicer.LanguageGraph(language, new StructureGraph(0, 0, roots));
  }

  private static StructureGraph.Node node(int id, String name, String kind, List<StructureGraph.Edge> edges, List<StructureGraph.Node> children) {
    return new StructureGraph.Node(id, name, kind, null, edges, children);
  }
}
