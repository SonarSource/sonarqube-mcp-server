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
import java.util.List;
import java.util.Objects;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.IntendedArchitecture;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;
import org.sonarsource.sonarqube.mcp.tools.ToolDefinitionBuilder;
import org.sonarsource.sonarqube.mcp.tools.ToolParameters;
import org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureGetIntendedToolResponse.Constraint;
import org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureGetIntendedToolResponse.Group;
import org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureGetIntendedToolResponse.Model;

public class ArchitectureGetTool extends Tool {

  public static final String TOOL_NAME = "architecture_get";
  public static final String TARGET_PROPERTY = "target";
  public static final String COMPONENT_PROPERTY = "component";
  public static final String DEPTH_PROPERTY = "depth";
  public static final String LANGUAGE_PROPERTY = "language";
  public static final String CURRENT = "current";
  public static final String INTENDED = "intended";

  static final String[] LANGUAGES = ArchitectureApi.LANGUAGES.toArray(String[]::new);
  private static final String[] TARGETS = {CURRENT, INTENDED};
  private static final String[] SERVER_TARGETS = {CURRENT};
  private final String[] availableTargets;

  private final ServerApiProvider serverApiProvider;
  @Nullable
  private final String configuredProjectKey;

  public ArchitectureGetTool(ServerApiProvider serverApiProvider, boolean isSonarQubeCloud, @Nullable String configuredProjectKey) {
    super((isSonarQubeCloud ? ToolDefinitionBuilder.cloudBuilder() : ToolDefinitionBuilder.builder())
      .setName(TOOL_NAME)
      .setTitle("Get Architecture")
      .setDescription(isSonarQubeCloud ? "Read current dependency graphs or intended architecture." : "Read current dependency graphs.")
      .addRequiredEnumProperty(TARGET_PROPERTY, isSonarQubeCloud ? TARGETS : SERVER_TARGETS,
        isSonarQubeCloud ? "current: analyzed dependencies; intended: desired groups and constraints." : "current: analyzed dependencies.")
      .addProjectKeyProperty(ToolParameters.PROJECT_KEY, configuredProjectKey)
      .addStringProperty(COMPONENT_PROPERTY, "Exact key from this tool, not a wildcard (Java current: module:package). "
        + (isSonarQubeCloud ? "Intended: group labels joined by /. " : "") + "Top level when omitted.")
      .addNumberProperty(DEPTH_PROPERTY, isSonarQubeCloud ? "Levels below the start to return. Default 1 for current, all for intended."
        : "Levels below the start to return. Default 1.")
      .addEnumProperty(LANGUAGE_PROPERTY, LANGUAGES, "Language to read. All languages when omitted.")
      .setReadOnlyHint()
      .build(),
      ToolCategory.ARCHITECTURE);
    this.availableTargets = isSonarQubeCloud ? TARGETS : SERVER_TARGETS;
    this.serverApiProvider = serverApiProvider;
    this.configuredProjectKey = configuredProjectKey;
  }

  @Override
  public Result execute(Arguments arguments) {
    var target = arguments.getEnumOrThrow(TARGET_PROPERTY, availableTargets);
    var projectKey = arguments.getProjectKeyWithFallback(ToolParameters.PROJECT_KEY, configuredProjectKey);
    var component = arguments.getOptionalString(COMPONENT_PROPERTY);
    var depth = arguments.getOptionalInteger(DEPTH_PROPERTY);
    var language = arguments.getOptionalEnumValue(LANGUAGE_PROPERTY, LANGUAGES);
    if (depth != null && depth < 1) {
      throw new IllegalArgumentException("depth must be 1 or more.");
    }

    var serverApi = serverApiProvider.get(arguments.getOptionalString(ToolParameters.ORGANIZATION));
    if (CURRENT.equals(target)) {
      serverApi.requireOrganization();
      return current(serverApi.architectureApi(), projectKey, ArchitectureProjectContext.organizationId(serverApi),
        ArchitectureProjectContext.mainBranchId(serverApi, projectKey), component, depth == null ? 1 : depth, language);
    }
    return intended(serverApi.architectureApi(), projectKey, ArchitectureProjectContext.intendedProjectId(serverApi, projectKey), component, depth, language);
  }

  private static Result current(ArchitectureApi api, String projectKey, String organizationId, String branchId, @Nullable String component, int depth, @Nullable String language) {
    var languages = language == null ? ArchitectureApi.LANGUAGES : List.of(language);
    var graphs = languages.stream()
      .flatMap(lang -> api.getStructure(projectKey, organizationId, branchId, lang).stream().map(graph -> new GraphSlicer.LanguageGraph(lang, graph)))
      .filter(languageGraph -> languageGraph.graph().nodes() != null && !languageGraph.graph().nodes().isEmpty())
      .toList();
    if (graphs.isEmpty()) {
      return Result.success(new ArchitectureGetCurrentToolResponse(projectKey, component, 0, depth, false, List.of(), List.of(),
        "No current architecture is visible for project '" + projectKey + "'" + (language == null ? "" : (" in " + language))
          + ". Check project access, organization, selected language and latest analysis."));
    }
    return Result.success(GraphSlicer.slice(projectKey, graphs, component, depth));
  }

  private static Result intended(ArchitectureApi api, String projectKey, String projectId, @Nullable String component, @Nullable Integer depth, @Nullable String language) {
    var stored = api.getIntended(projectKey, projectId);
    var revision = IntendedRevision.of(stored);
    var partial = component != null || depth != null || language != null;
    var models = stored.stream()
      .filter(model -> language == null || language.equals(model.language()))
      .toList();
    if (models.isEmpty()) {
      return Result.success(new ArchitectureGetIntendedToolResponse(projectKey, revision, component, depth, List.of(), partial ? true : null,
        "No intended architecture is visible for project '" + projectKey + "'" + (language == null ? "" : (" in " + language))
          + ". It may be undefined, inaccessible or in another organization."));
    }

    var views = models.stream()
      .map(model -> toView(model, component, depth, partial))
      .filter(Objects::nonNull)
      .toList();
    if (views.isEmpty()) {
      throw new IllegalArgumentException("No group '" + component + "' in the intended architecture. Call " + TOOL_NAME
        + " with target intended and without component to list the group keys.");
    }
    return Result.success(new ArchitectureGetIntendedToolResponse(projectKey, revision, component, depth, views, partial ? true : null, null));
  }

  /**
   * Returns null when the model has no group with the component key.
   */
  @Nullable
  private static Model toView(IntendedArchitecture model, @Nullable String component, @Nullable Integer depth, boolean partial) {
    var groups = nullToEmpty(model.groups());
    var constraints = nullToEmpty(model.constraints());
    if (component != null) {
      var root = findGroup(groups, component, null);
      if (root == null) {
        return null;
      }
      groups = nullToEmpty(root.groups());
      var prefix = component + "/";
      constraints = constraints.stream()
        .filter(constraint -> component.equals(constraint.from()) || component.equals(constraint.to())
          || startsWith(constraint.from(), prefix) || startsWith(constraint.to(), prefix))
        .toList();
    }
    return new Model(model.language(), model.type(), toGroups(groups, depth == null ? Integer.MAX_VALUE : depth),
      constraints.stream().map(constraint -> new Constraint(constraint.from(), constraint.to())).toList(), partial ? true : null, model.label(), model.description());
  }

  @Nullable
  private static IntendedArchitecture.Group findGroup(List<IntendedArchitecture.Group> groups, String key, @Nullable String parentKey) {
    IntendedArchitecture.Group found = null;
    for (var group : groups) {
      var groupKey = parentKey == null ? group.label() : (parentKey + "/" + group.label());
      IntendedArchitecture.Group match = null;
      if (key.equals(groupKey)) {
        match = group;
      } else if (key.startsWith(groupKey + "/")) {
        match = findGroup(nullToEmpty(group.groups()), key, groupKey);
      }
      if (match != null) {
        if (found != null) {
          throw new IllegalArgumentException("Group key '" + key + "' matches multiple groups because labels contain '/'. "
            + "Read the complete intended model without component to inspect their hierarchy.");
        }
        found = match;
      }
    }
    return found;
  }

  private static List<Group> toGroups(List<IntendedArchitecture.Group> groups, int levels) {
    return groups.stream().map(group -> {
      var subgroups = nullToEmpty(group.groups());
      var shown = levels > 1 || subgroups.isEmpty();
      return new Group(group.label(), group.patterns(), group.isInterface(), group.interfaceAccess(),
        shown && !subgroups.isEmpty() ? toGroups(subgroups, levels - 1) : null,
        shown ? null : countGroups(subgroups), group.description(), group.patternId());
    }).toList();
  }

  private static int countGroups(List<IntendedArchitecture.Group> groups) {
    return groups.stream().mapToInt(group -> 1 + countGroups(nullToEmpty(group.groups()))).sum();
  }

  private static boolean startsWith(@Nullable String value, String prefix) {
    return value != null && value.startsWith(prefix);
  }

  private static <T> List<T> nullToEmpty(@Nullable List<T> list) {
    return list == null ? List.of() : list;
  }

}
