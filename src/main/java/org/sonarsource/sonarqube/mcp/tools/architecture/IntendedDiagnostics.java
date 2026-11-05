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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.IntendedArchitecture;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.StructureGraph;

final class IntendedDiagnostics {
  private static final int MAX_WARNINGS = 10;

  private IntendedDiagnostics() {
  }

  static List<String> inspect(ServerApi api, String projectKey, List<IntendedArchitecture> models, CurrentGraphReads currentReads) {
    var languages = models.stream().filter(model -> !groups(model).isEmpty()).map(IntendedArchitecture::language).distinct().toList();
    if (languages.isEmpty()) {
      return List.of();
    }
    try {
      return currentReads.execute(() -> inspectLanguages(api, projectKey, models, languages));
    } catch (IllegalStateException e) {
      if (Thread.currentThread().isInterrupted()) {
        throw e;
      }
      return languages.stream().map(language -> unavailable(language, e.getMessage())).toList();
    }
  }

  private static List<String> inspectLanguages(ServerApi api, String projectKey, List<IntendedArchitecture> models, List<String> languages) {
    var warnings = new Warnings();
    var unavailable = new ArrayList<String>();
    for (var language : languages) {
      try {
        var graphs = api.architectureApi().getStructure(projectKey, ArchitectureProjectContext.organizationId(api),
          ArchitectureProjectContext.mainBranchId(api, projectKey), language);
        if (graphs.size() != 1 || graphs.getFirst().nodes() == null) {
          throw new IllegalStateException("No unique discovery graph");
        }
        inspect(models.stream().filter(model -> language.equals(model.language())).toList(), graphs.getFirst(), warnings);
      } catch (RuntimeException e) {
        if (Thread.currentThread().isInterrupted()) {
          throw e;
        }
        unavailable.add(unavailable(language, e.getMessage()));
      }
    }
    return warnings.result(unavailable);
  }

  private static String unavailable(String language, @Nullable String reason) {
    return language + ": pattern and physical hierarchy checks unavailable; no complete discovery graph was read"
      + ((reason == null || reason.isBlank()) ? "" : " (%s)".formatted(reason)) + ". Structural validation still applies.";
  }

  static List<String> inspect(List<IntendedArchitecture> models, StructureGraph graph) {
    var warnings = new Warnings();
    inspect(models, graph, warnings);
    return warnings.result(List.of());
  }

  private static void inspect(List<IntendedArchitecture> models, StructureGraph graph, Warnings warnings) {
    var artifacts = new HashMap<String, Artifact>();
    index(graph.nodes() == null ? List.of() : graph.nodes(), "", "", artifacts);
    for (var model : models) {
      inspectGroups(groups(model), artifacts, "", warnings);
    }
  }

  private static final class Warnings {
    private final List<String> shown = new ArrayList<>();
    private int total;

    private void add(String message) {
      total++;
      if (shown.size() < MAX_WARNINGS) {
        shown.add(message);
      }
    }

    private List<String> result(List<String> unavailable) {
      var result = new ArrayList<>(unavailable);
      result.addAll(shown);
      if (total > shown.size()) {
        result.add((total - shown.size()) + " additional discovery warning(s) omitted.");
      }
      return List.copyOf(result);
    }
  }

  private static List<IntendedArchitecture.Group> groups(IntendedArchitecture model) {
    return model.groups() == null ? List.of() : model.groups();
  }

  private record Artifact(String key, String parent, boolean container) {
  }

  private static void index(List<StructureGraph.Node> nodes, String prefix, String parent, Map<String, Artifact> artifacts) {
    for (var node : nodes) {
      var key = prefix + node.name();
      var children = node.nodes() == null ? List.<StructureGraph.Node>of() : node.nodes();
      var artifact = new Artifact(key, parent, !children.isEmpty());
      if (artifacts.putIfAbsent(key, artifact) != null) {
        throw new IllegalStateException("Ambiguous discovery graph");
      }
      if (!children.isEmpty()) {
        var separator = GraphSlicer.childSeparator(node);
        artifacts.putIfAbsent(key + separator, artifact);
        if (artifacts.putIfAbsent(key + separator + "**", artifact) != null) {
          throw new IllegalStateException("Ambiguous discovery graph");
        }
        index(children, key + separator, key, artifacts);
      }
    }
  }

  private static void inspectGroups(List<IntendedArchitecture.Group> groups, Map<String, Artifact> artifacts, String parent, Warnings warnings) {
    for (var group : groups) {
      if (group.patternId() != null) {
        continue;
      }
      var artifact = inspectPatterns(group, artifacts, warnings);
      if (artifact != null) {
        inspectParent(group.label(), artifact, parent, warnings);
      }
      inspectGroups(group.groups() == null ? List.of() : group.groups(), artifacts, artifact == null ? "" : artifact.key(), warnings);
    }
  }

  @Nullable
  private static Artifact inspectPatterns(IntendedArchitecture.Group group, Map<String, Artifact> artifacts, Warnings warnings) {
    Artifact artifact = null;
    for (var pattern : group.patterns() == null ? List.<String>of() : group.patterns()) {
      var found = artifacts.get(pattern);
      if (found != null && found.container() && !pattern.endsWith("**")) {
        warnings.add("Group '" + group.label() + "': exact pattern '" + pattern
          + "' names a container, not its descendants. Use its discovery pattern with the child separator and ** if the subtree is intended; exact leaf patterns remain valid.");
      }
      if (artifact == null) {
        artifact = found;
      }
    }
    return artifact;
  }

  private static void inspectParent(String label, Artifact artifact, String parent, Warnings warnings) {
    if (!parent.isEmpty() && !parent.equals(artifact.parent()) && !artifact.parent().isEmpty()) {
      warnings.add("Group '" + label + "' is under a different parent from discovery ('" + artifact.parent()
        + "'). For a physical hierarchy, retain intermediate parents; logical groupings are allowed. No groups were moved.");
    }
  }
}
