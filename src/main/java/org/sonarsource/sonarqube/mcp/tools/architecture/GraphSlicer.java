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

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.StructureGraph;
import org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureGetCurrentToolResponse.DrillDown;
import org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureGetCurrentToolResponse.Edge;
import org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureGetCurrentToolResponse.FeedbackPairs;
import org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureGetCurrentToolResponse.Graph;
import org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureGetCurrentToolResponse.Node;

/**
 * Cuts the current architecture down to what an agent can read: the nodes below a component up to a depth, at most
 * {@link #MAX_NODES} nodes in total (shallowest first), with edges rolled up to the nodes shown.
 */
final class GraphSlicer {

  static final int MAX_NODES = 150;
  static final int MAX_EDGES = 20;
  static final int MAX_DRILL_DOWN = 5;
  static final int MAX_FEEDBACK_PAIRS = 5;

  private static final Comparator<Edge> HEAVIEST_FIRST = Comparator.comparingInt(Edge::weight).reversed().thenComparing(Edge::from)
    .thenComparing(Edge::to);

  record LanguageGraph(String language, StructureGraph graph) {
  }

  private GraphSlicer() {
    // static only
  }

  static ArchitectureGetCurrentToolResponse slice(String projectKey, List<LanguageGraph> graphs, @Nullable String component, int depth) {
    var indexedGraphs = graphs.stream().map(IndexedGraph::new).toList();
    var selected = component == null ? indexedGraphs : indexedGraphs.stream().filter(graph -> graph.idByKey.containsKey(component)).toList();
    if (component != null && selected.isEmpty()) {
      throw new IllegalArgumentException("No node '" + component + "' in the current architecture. Call " + ArchitectureGetTool.TOOL_NAME
        + " with target current and without component to list the top-level node keys.");
    }

    var scopes = selected.stream().map(graph -> graph.scope(component, depth)).toList();
    var budgets = nodeBudgets(scopes);
    var views = new ArrayList<Graph>();
    var drillDowns = new ArrayList<DrillDown>();
    for (var i = 0; i < selected.size(); i++) {
      var selection = select(scopes.get(i), budgets[i]);
      views.add(selected.get(i).toResponse(scopes.get(i).size(), selection));
      drillDowns.addAll(selected.get(i).drillDowns(selection.nodes()));
    }

    var slices = limitEdges(views);
    var effectiveDepth = slices.stream().filter(graph -> graph.nodesInScope() > 0).mapToInt(Graph::depth).min().orElse(0);
    var capped = slices.stream().anyMatch(Graph::capped);
    var biggestDrillDowns = drillDowns.stream()
      .sorted(Comparator.comparingInt(DrillDown::descendants).reversed().thenComparing(DrillDown::language).thenComparing(DrillDown::component))
      .limit(MAX_DRILL_DOWN)
      .toList();
    return new ArchitectureGetCurrentToolResponse(projectKey, component, effectiveDepth, depth, capped, slices, biggestDrillDowns,
      capped ? limitMessage(slices) : null);
  }

  private record Scope(List<List<Integer>> levels, int size) {
  }

  private record Selection(List<Integer> nodes, int depth, @Nullable Integer partialLevel) {
  }

  private record RankedEdge(int graph, Edge edge) {
  }

  /** Allocate one node per nonempty graph in turn, so a large language cannot consume the entire budget. */
  private static int[] nodeBudgets(List<Scope> scopes) {
    var budgets = new int[scopes.size()];
    var remaining = MAX_NODES;
    var allocated = true;
    while (remaining > 0 && allocated) {
      allocated = false;
      for (var i = 0; i < scopes.size() && remaining > 0; i++) {
        if (budgets[i] < scopes.get(i).size()) {
          budgets[i]++;
          remaining--;
          allocated = true;
        }
      }
    }
    return budgets;
  }

  private static Selection select(Scope scope, int budget) {
    var kept = new ArrayList<Integer>();
    var depth = 0;
    for (var level : scope.levels()) {
      if (kept.size() + level.size() > budget) {
        if (kept.isEmpty() && budget > 0) {
          kept.addAll(level.subList(0, budget));
          return new Selection(kept, 0, 1);
        }
        break;
      }
      kept.addAll(level);
      depth++;
    }
    return new Selection(kept, depth, null);
  }

  private static List<Graph> limitEdges(List<Graph> views) {
    var edges = heaviest(views, false, MAX_EDGES);
    var pairs = heaviest(views, true, MAX_FEEDBACK_PAIRS);
    var result = new ArrayList<Graph>();
    for (var i = 0; i < views.size(); i++) {
      var graph = views.get(i);
      var graphIndex = i;
      var keptEdges = edges.stream().filter(edge -> edge.graph() == graphIndex).map(RankedEdge::edge).toList();
      var keptPairs = pairs.stream().filter(edge -> edge.graph() == graphIndex).map(RankedEdge::edge).toList();
      var omittedEdges = graph.edgesInScope() - keptEdges.size();
      var capped = graph.omittedNodes() > 0 || omittedEdges > 0 || keptPairs.size() < graph.feedbackPairs().total();
      result.add(new Graph(graph.language(), graph.totalNodes(), graph.totalEdges(), graph.nodesInScope(), graph.edgesInScope(),
        graph.nodes(), keptEdges, new FeedbackPairs(graph.feedbackPairs().total(), keptPairs), graph.outsideViewWeight(),
        graph.depth(), graph.partialLevel(), capped, graph.omittedNodes(), omittedEdges));
    }
    return result;
  }

  private static List<RankedEdge> heaviest(List<Graph> graphs, boolean feedback, int limit) {
    var candidates = new ArrayList<RankedEdge>();
    for (var i = 0; i < graphs.size(); i++) {
      var graph = graphs.get(i);
      for (var edge : feedback ? graph.feedbackPairs().heaviest() : graph.edges()) {
        candidates.add(new RankedEdge(i, edge));
      }
    }
    return candidates.stream().sorted(Comparator.comparing(RankedEdge::edge, HEAVIEST_FIRST).thenComparingInt(RankedEdge::graph))
      .limit(limit).toList();
  }

  private static String limitMessage(List<Graph> graphs) {
    var shown = graphs.stream().mapToInt(graph -> graph.nodes().size()).sum();
    var inScope = graphs.stream().mapToInt(Graph::nodesInScope).sum();
    var edgesShown = graphs.stream().mapToInt(graph -> graph.edges().size()).sum();
    var edgesInScope = graphs.stream().mapToInt(Graph::edgesInScope).sum();
    return "Showing " + shown + " of " + inScope + " nodes in the requested scope and " + edgesShown + " of " + edgesInScope
      + " dependencies between shown nodes. Call " + ArchitectureGetTool.TOOL_NAME
      + " with target current and a component from drillDown, or with a language, to narrow the view. "
      + "Omitted siblings have no continuation token and may remain undiscoverable; this is not a complete graph export.";
  }

  private static final class IndexedGraph {
    private final String language;
    @Nullable
    private final Map<Integer, String> keyById = new HashMap<>();
    private final Map<Integer, String> kindById = new HashMap<>();
    private final Map<Integer, String> separatorById = new HashMap<>();
    private final Map<Integer, Integer> parentById = new HashMap<>();
    private final Map<Integer, List<Integer>> childrenById = new HashMap<>();
    private final Map<Integer, Integer> descendantsById = new HashMap<>();
    private final Map<String, Integer> idByKey = new HashMap<>();
    private final List<Integer> rootIds = new ArrayList<>();
    private final List<int[]> rawEdges = new ArrayList<>();

    private IndexedGraph(LanguageGraph languageGraph) {
      this.language = languageGraph.language();
      for (var root : nullToEmpty(languageGraph.graph().nodes())) {
        rootIds.add(root.id());
        add(root, null);
      }
    }

    /**
     * What SonarQube joins a node's children to it with, as its architecture pages build node keys: the node's own separator, or one inferred
     * from its kind when the response leaves it out ({@code module:package.Class}, {@code dir/file}).
     */
    private static String childSeparator(StructureGraph.Node node) {
      if (node.separator() != null && !node.separator().isEmpty()) {
        return node.separator();
      }
      return switch (node.kind()) {
        case "module" -> ":";
        case "directory", "folder" -> "/";
        default -> ".";
      };
    }

    private int add(StructureGraph.Node node, @Nullable Integer parentId) {
      var key = parentId == null ? node.name() : (keyById.get(parentId) + separatorById.get(parentId) + node.name());
      keyById.put(node.id(), key);
      kindById.put(node.id(), node.kind());
      separatorById.put(node.id(), childSeparator(node));
      idByKey.putIfAbsent(key, node.id());
      if (parentId != null) {
        parentById.put(node.id(), parentId);
      }
      for (var edge : nullToEmpty(node.edges())) {
        rawEdges.add(new int[] {node.id(), edge.toId(), edge.weight()});
      }
      var children = new ArrayList<Integer>();
      childrenById.put(node.id(), children);
      var descendants = 0;
      for (var child : nullToEmpty(node.nodes())) {
        children.add(child.id());
        descendants += 1 + add(child, node.id());
      }
      descendantsById.put(node.id(), descendants);
      return descendants;
    }

    /**
     * Node ids below the component (or the top level), level by level up to {@code depth}.
     */
    private Scope scope(@Nullable String component, int depth) {
      var level = component == null ? rootIds : childrenById.get(idByKey.get(component));
      var levels = new ArrayList<List<Integer>>();
      var size = 0;
      for (var currentDepth = 1; currentDepth <= depth && !level.isEmpty(); currentDepth++) {
        levels.add(level);
        size += level.size();
        level = level.stream().flatMap(id -> childrenById.get(id).stream()).toList();
      }
      return new Scope(levels, size);
    }

    private Graph toResponse(int nodesInScope, Selection selection) {
      var kept = selection.nodes();
      var keptIds = Set.copyOf(kept);
      var nodes = kept.stream().map(id -> new Node(keyById.get(id), kindById.get(id), childrenById.get(id).size())).toList();
      var weightByEdge = new LinkedHashMap<List<Integer>, Integer>();
      var outsideViewWeight = 0;
      for (var rawEdge : rawEdges) {
        var from = shownAncestorOrSelf(rawEdge[0], keptIds);
        var to = shownAncestorOrSelf(rawEdge[1], keptIds);
        if ((from == null) != (to == null)) {
          outsideViewWeight += rawEdge[2];
        }
        if (from != null && to != null && !from.equals(to)) {
          weightByEdge.merge(List.of(from, to), rawEdge[2], Integer::sum);
        }
      }
      var edges = weightByEdge.entrySet().stream()
        .map(entry -> new Edge(keyById.get(entry.getKey().get(0)), keyById.get(entry.getKey().get(1)), entry.getValue()))
        .sorted(HEAVIEST_FIRST)
        .limit(MAX_EDGES)
        .toList();
      return new Graph(language, keyById.size(), rawEdges.size(), nodesInScope, weightByEdge.size(), nodes, edges,
        feedbackPairs(weightByEdge), outsideViewWeight, selection.depth(), selection.partialLevel(), kept.size() < nodesInScope,
        nodesInScope - kept.size(), weightByEdge.size() - edges.size());
    }

    /**
     * Two-way dependencies between shown nodes, each pair once, from its heavier direction (key order on a tie).
     */
    private FeedbackPairs feedbackPairs(Map<List<Integer>, Integer> weightByEdge) {
      var pairs = new ArrayList<Edge>();
      weightByEdge.forEach((edge, weight) -> {
        var back = weightByEdge.get(List.of(edge.get(1), edge.get(0)));
        if (back == null) {
          return;
        }
        var from = keyById.get(edge.get(0));
        var to = keyById.get(edge.get(1));
        if (weight > back || (weight.equals(back) && from.compareTo(to) < 0)) {
          pairs.add(new Edge(from, to, weight + back));
        }
      });
      return new FeedbackPairs(pairs.size(), pairs.stream().sorted(HEAVIEST_FIRST).limit(MAX_FEEDBACK_PAIRS).toList());
    }

    @Nullable
    private Integer shownAncestorOrSelf(int id, Set<Integer> keptIds) {
      Integer current = id;
      while (current != null && !keptIds.contains(current)) {
        current = parentById.get(current);
      }
      return current;
    }

    /**
     * Shown nodes with children that are not shown: the places worth another call.
     */
    private List<DrillDown> drillDowns(List<Integer> kept) {
      var keptIds = new HashSet<>(kept);
      return kept.stream()
        .filter(id -> childrenById.get(id).stream().anyMatch(child -> !keptIds.contains(child)))
        .map(id -> new DrillDown(language, keyById.get(id), descendantsById.get(id)))
        .toList();
    }

    private static <T> List<T> nullToEmpty(@Nullable List<T> list) {
      return list == null ? List.of() : list;
    }
  }

}
