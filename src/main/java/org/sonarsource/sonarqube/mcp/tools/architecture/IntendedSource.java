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

import com.google.gson.JsonParseException;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.IntendedArchitecture;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.IntendedModelGraph;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ServerApiException;
import org.sonarsource.sonarqube.mcp.tools.BranchPullRequestContext;

/**
 * Decides whether a repository file or the server manages the intended architecture, with the rule the web UI applies: the setting
 * {@code sonar.architecture.model.location}, then the {@code intended_models} graph of the branch's last analysis, or of the main branch's when the
 * branch has none. Replace with the backend's answer once the public API reports the source.
 */
final class IntendedSource {

  static final String MODEL_LOCATION_KEY = "sonar.architecture.model.location";
  static final String DYNAMIC = "DYNAMIC";
  static final String FILE = "FILE";
  static final String SERVER = "SERVER";
  static final String UNKNOWN = "UNKNOWN";
  private static final String ALLOW_RELATION = "allow";

  private IntendedSource() {
    // static only
  }

  /**
   * {@code fileModels} is the model the last analysis read from the file, empty when it had none or the source is not {@code FILE}.
   */
  record Resolution(String source, String setting, boolean fromMainBranch, List<IntendedArchitecture> fileModels, boolean fileAnalysed,
    int droppedConstraints, @Nullable String problem) {

    boolean isManagedByFile() {
      return FILE.equals(source);
    }

    boolean isServerManaged() {
      return SERVER.equals(source);
    }
  }

  static Resolution resolve(ServerApi serverApi, String projectKey, BranchPullRequestContext.Params params) {
    var setting = setting(serverApi, projectKey);
    if (SERVER.equals(setting)) {
      return new Resolution(SERVER, setting, false, List.of(), false, 0, null);
    }
    var requestedBranchId = params.branch() != null || params.pullRequest() != null
      ? ArchitectureProjectContext.branchId(serverApi, projectKey, params)
      : null;
    List<IntendedModelGraph> fileGraphs;
    var fromMainBranch = false;
    try {
      var api = serverApi.architectureApi();
      var organizationId = ArchitectureProjectContext.organizationId(serverApi);
      var ids = api.getIntendedModelGraphIds(projectKey, organizationId,
        requestedBranchId != null ? requestedBranchId : ArchitectureProjectContext.mainBranchId(serverApi, projectKey));
      if (ids.isEmpty() && requestedBranchId != null) {
        ids = api.getIntendedModelGraphIds(projectKey, organizationId, ArchitectureProjectContext.mainBranchId(serverApi, projectKey));
        fromMainBranch = !ids.isEmpty();
      }
      fileGraphs = ids.stream()
        .map(id -> api.getIntendedModelGraph(projectKey, id))
        .filter(graph -> FILE.equalsIgnoreCase(graph.source()))
        .toList();
    } catch (ServerApiException | IllegalStateException | IllegalArgumentException unreadable) {
      var problem = "The model of the last analysis could not be read: " + unreadable.getMessage();
      return new Resolution(FILE.equals(setting) ? FILE : UNKNOWN, setting, false, List.of(), false, 0, problem);
    }
    if (!FILE.equals(setting) && fileGraphs.isEmpty()) {
      return new Resolution(SERVER, setting, false, List.of(), false, 0, null);
    }
    var models = new ArrayList<IntendedArchitecture>();
    var dropped = 0;
    for (var graph : fileGraphs) {
      if (graph.model() == null || graph.model().perspectives() == null) {
        continue;
      }
      for (var perspective : graph.model().perspectives()) {
        if (perspective != null) {
          var constraints = new ArrayList<IntendedArchitecture.Constraint>();
          dropped += addConstraints(perspective.constraints(), constraints);
          models.add(new IntendedArchitecture(null, perspective.qualifiers(), perspective.language(), groups(perspective.groups()), constraints,
            perspective.label(), perspective.description()));
        }
      }
    }
    return new Resolution(FILE, setting, fromMainBranch && !fileGraphs.isEmpty(), models, !fileGraphs.isEmpty(), dropped, null);
  }

  private static String setting(ServerApi serverApi, String projectKey) {
    String value;
    try {
      value = serverApi.settingsApi().getValue(projectKey, MODEL_LOCATION_KEY).orElse(null);
    } catch (ServerApiException | IllegalStateException | JsonParseException unreadable) {
      return DYNAMIC;
    }
    var location = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    return FILE.equals(location) || SERVER.equals(location) ? location : DYNAMIC;
  }

  private static int addConstraints(@Nullable List<IntendedModelGraph.Constraint> constraints, List<IntendedArchitecture.Constraint> target) {
    var dropped = 0;
    for (var constraint : constraints == null ? List.<IntendedModelGraph.Constraint>of() : constraints) {
      if (constraint == null || (constraint.relation() != null && !ALLOW_RELATION.equalsIgnoreCase(constraint.relation()))) {
        dropped++;
        continue;
      }
      for (var from : constraint.from() == null ? List.<String>of() : constraint.from()) {
        for (var to : constraint.to() == null ? List.<String>of() : constraint.to()) {
          target.add(new IntendedArchitecture.Constraint(from, to));
        }
      }
    }
    return dropped;
  }

  @Nullable
  private static List<IntendedArchitecture.Group> groups(@Nullable List<IntendedModelGraph.Group> groups) {
    if (groups == null) {
      return null;
    }
    return groups.stream()
      .filter(Objects::nonNull)
      .map(group -> new IntendedArchitecture.Group(group.label(), group.patterns(), groups(group.groups()), group.isInterface(),
        group.interfaceAccess() != null ? group.interfaceAccess() : group.interfaceAccessSnakeCase(), group.description(), null))
      .toList();
  }

}
