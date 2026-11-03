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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.StructureGraph;

final class DependencyQuery {
  static final int MAX_PAGE_SIZE = 20;
  private static final long MAX_SAFE_WEIGHT = 9007199254740991L;

  private DependencyQuery() {
  }

  record Selection(String from, String to, int pageIndex, int pageSize) {
    Selection {
      if (from.isEmpty() || to.isEmpty()) {
        throw new IllegalArgumentException("Use nonempty from and to component keys.");
      }
      if (pageIndex < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
        throw new IllegalArgumentException("Page must be >= 1; dependency page size must be between 1 and 20.");
      }
    }
  }

  record Dependency(String from, String to, long weight) {
  }

  record Page(int pageIndex, int pageSize, int total, boolean hasNextPage) {
  }

  record Response(String projectKey, String language, String from, String to, int depth, String revision,
    List<Dependency> dependencies, Page page) {
  }

  private record EdgeKey(int from, int to) {
  }

  private record Pair(String from, String to) {
  }

  static Response query(String projectKey, List<GraphSlicer.LanguageGraph> graphs, Selection selection, int depth) {
    var matches = graphs.stream().map(Index::new)
      .filter(index -> index.idByKey.containsKey(selection.from()) && index.idByKey.containsKey(selection.to())).toList();
    if (matches.size() != 1) {
      throw new IllegalArgumentException(matches.size() > 1 ? "Dependency endpoints match more than one graph. Specify language."
        : "Both dependency endpoints must exist in the same current graph. Check exact keys and language.");
    }
    var index = matches.getFirst();
    var sources = index.representatives(selection.from(), depth);
    var targets = index.representatives(selection.to(), depth);
    var all = index.dependencies(sources, targets);
    var revision = revision(index.language, selection, depth, all);
    var start = (long) (selection.pageIndex() - 1) * selection.pageSize();
    var end = Math.min(start + selection.pageSize(), all.size());
    var page = start >= all.size() ? List.<Dependency>of() : all.subList((int) start, (int) end);
    return new Response(projectKey, index.language, selection.from(), selection.to(), depth, revision, page,
      new Page(selection.pageIndex(), selection.pageSize(), all.size(), end < all.size()));
  }

  private static String revision(String language, Selection selection, int depth, List<Dependency> dependencies) {
    var values = new ArrayList<>(List.of("dependency-v1", language, selection.from(), selection.to(), String.valueOf(depth)));
    for (var dependency : dependencies) {
      values.addAll(List.of(dependency.from(), dependency.to(), String.valueOf(dependency.weight())));
    }
    var canonical = new StringBuilder();
    values.forEach(value -> canonical.append(value.length()).append(':').append(value));
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  private static final class Index {
    private final String language;
    private final Map<Integer, StructureGraph.Node> nodes = new HashMap<>();
    private final Map<Integer, String> keys = new HashMap<>();
    private final Map<String, Integer> idByKey = new HashMap<>();

    private Index(GraphSlicer.LanguageGraph graph) {
      language = graph.language();
      if (graph.graph().nodes() != null) {
        for (var root : graph.graph().nodes()) {
          add(root, root.name());
        }
      }
    }

    private void add(StructureGraph.Node node, String key) {
      if (nodes.putIfAbsent(node.id(), node) != null || idByKey.putIfAbsent(key, node.id()) != null) {
        throw new IllegalArgumentException("Current graph has ambiguous node keys or duplicate node ids.");
      }
      keys.put(node.id(), key);
      var children = children(node);
      if ("file".equals(node.kind()) && !children.isEmpty() && (node.separator() == null || node.separator().isEmpty())) {
        throw new IllegalArgumentException("Cannot derive keys for a file with children without a public separator.");
      }
      var separator = node.separator();
      if (separator == null || separator.isEmpty()) {
        separator = switch (node.kind()) {
          case "module" -> ":";
          case "directory", "folder" -> "/";
          default -> ".";
        };
      }
      for (var child : children) {
        add(child, key + separator + child.name());
      }
    }

    private record Step(int id, int level, int representative) {
    }

    private Map<Integer, String> representatives(String key, int depth) {
      var result = new HashMap<Integer, String>();
      var root = idByKey.get(key);
      var pending = new java.util.ArrayDeque<Step>();
      pending.push(new Step(root, 0, root));
      while (!pending.isEmpty()) {
        var step = pending.pop();
        result.put(step.id(), keys.get(step.representative()));
        for (var child : children(nodes.get(step.id()))) {
          pending.push(new Step(child.id(), step.level() + 1, step.level() < depth ? child.id() : step.representative()));
        }
      }
      return result;
    }

    private List<Dependency> dependencies(Map<Integer, String> sources, Map<Integer, String> targets) {
      var weights = new HashMap<Pair, Long>();
      var seen = new HashSet<EdgeKey>();
      for (var node : nodes.values()) {
        for (var edge : node.edges() == null ? List.<StructureGraph.Edge>of() : node.edges()) {
          if (!nodes.containsKey(edge.toId())) {
            throw new IllegalArgumentException("Current graph contains an unresolved dependency target.");
          }
          var from = sources.get(node.id());
          var to = targets.get(edge.toId());
          if (from == null || to == null) {
            continue;
          }
          addDependency(node, edge, from, to, seen, weights);
        }
      }
      return weights.entrySet().stream().map(entry -> new Dependency(entry.getKey().from(), entry.getKey().to(), entry.getValue()))
        .sorted(Comparator.comparingLong(Dependency::weight).reversed().thenComparing(Dependency::from).thenComparing(Dependency::to)).toList();
    }

    private void addDependency(StructureGraph.Node node, StructureGraph.Edge edge, String from, String to,
      java.util.Set<EdgeKey> seen, Map<Pair, Long> weights) {
      if (edge.weight() < 0 || !seen.add(new EdgeKey(node.id(), edge.toId()))) {
        throw new IllegalArgumentException("Current graph contains invalid or duplicate dependency edges.");
      }
      if (!children(node).isEmpty() || !children(nodes.get(edge.toId())).isEmpty()) {
        throw new IllegalArgumentException("Targeted dependencies require leaf-level edges; this graph has container-level relationships in the selected scope.");
      }
      if (from.equals(to)) {
        return;
      }
      var pair = new Pair(from, to);
      var weight = Math.addExact(weights.getOrDefault(pair, 0L), edge.weight());
      if (weight > MAX_SAFE_WEIGHT) {
        throw new IllegalArgumentException("Aggregated dependency weight exceeds the supported integer range.");
      }
      weights.put(pair, weight);
    }

    private static List<StructureGraph.Node> children(StructureGraph.Node node) {
      return node.nodes() == null ? List.of() : node.nodes();
    }
  }
}
