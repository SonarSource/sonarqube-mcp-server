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

  static List<String> inspect(ServerApi api, String projectKey, List<IntendedArchitecture> models) {
    var warnings = new ArrayList<String>();
    var languages = models.stream().filter(model -> !groups(model).isEmpty()).map(IntendedArchitecture::language).distinct().toList();
    for (var language : languages) {
      try {
        var graphs = api.architectureApi().getStructure(projectKey, ArchitectureProjectContext.organizationId(api),
          ArchitectureProjectContext.mainBranchId(api, projectKey), language);
        if (graphs.size() != 1 || graphs.getFirst().nodes() == null) {
          throw new IllegalStateException("No unique discovery graph");
        }
        warnings.addAll(inspect(models.stream().filter(model -> language.equals(model.language())).toList(), graphs.getFirst()));
      } catch (RuntimeException e) {
        warnings.add(language + ": pattern and physical hierarchy checks unavailable; no complete discovery graph was read. Structural validation still applies.");
      }
    }
    return warnings.stream().limit(MAX_WARNINGS).toList();
  }

  static List<String> inspect(List<IntendedArchitecture> models, StructureGraph graph) {
    var artifacts = new HashMap<String, Artifact>();
    index(graph.nodes() == null ? List.of() : graph.nodes(), "", "", artifacts);
    var warnings = new ArrayList<String>();
    for (var model : models) {
      inspectGroups(groups(model), artifacts, "", warnings);
    }
    return warnings;
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

  private static void inspectGroups(List<IntendedArchitecture.Group> groups, Map<String, Artifact> artifacts, String parent, List<String> warnings) {
    for (var group : groups) {
      if (group.patternId() != null || warnings.size() >= MAX_WARNINGS) {
        continue;
      }
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
      if (artifact != null && !parent.isEmpty() && !parent.equals(artifact.parent()) && !artifact.parent().isEmpty()) {
        warnings.add("Group '" + group.label() + "' is under a different parent from discovery ('" + artifact.parent()
          + "'). For a physical hierarchy, retain intermediate parents; logical groupings are allowed. No groups were moved.");
      }
      inspectGroups(group.groups() == null ? List.of() : group.groups(), artifacts, artifact == null ? "" : artifact.key(), warnings);
    }
  }
}
