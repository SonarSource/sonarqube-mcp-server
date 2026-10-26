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

import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.IntendedArchitecture;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectDirective;
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
  public static final String DIRECTIVES = "directives";
  private static final String DIRECTIVE_ID_PROPERTY = "directiveId";

  static final String[] LANGUAGES = ArchitectureApi.LANGUAGES.toArray(String[]::new);
  private static final String[] TARGETS = {CURRENT, INTENDED, DIRECTIVES};
  private static final String[] SERVER_TARGETS = {CURRENT};
  private static final int MAX_CONCURRENT_CURRENT_READS = 2;
  private final String[] availableTargets;
  private final Semaphore currentReads = new Semaphore(MAX_CONCURRENT_CURRENT_READS);

  private final ServerApiProvider serverApiProvider;
  @Nullable
  private final String configuredProjectKey;

  public ArchitectureGetTool(ServerApiProvider serverApiProvider, boolean isSonarQubeCloud, @Nullable String configuredProjectKey) {
    super(definition(isSonarQubeCloud, configuredProjectKey), ToolCategory.ARCHITECTURE);
    this.availableTargets = isSonarQubeCloud ? TARGETS : SERVER_TARGETS;
    this.serverApiProvider = serverApiProvider;
    this.configuredProjectKey = configuredProjectKey;
  }

  private static McpSchema.Tool definition(boolean isSonarQubeCloud, @Nullable String configuredProjectKey) {
    var builder = (isSonarQubeCloud ? ToolDefinitionBuilder.cloudBuilder() : ToolDefinitionBuilder.builder())
      .setName(TOOL_NAME)
      .setTitle("Get Architecture")
      .setDescription(isSonarQubeCloud ? "Read current graphs, intended architecture or project directives." : "Read current dependency graphs.")
      .addRequiredEnumProperty(TARGET_PROPERTY, isSonarQubeCloud ? TARGETS : SERVER_TARGETS,
        isSonarQubeCloud ? "current: dependencies; intended: model; "
          + "directives: project directives (directiveId/pageIndex/pageSize apply only to directives)." : "current: analyzed dependencies.")
      .addProjectKeyProperty(ToolParameters.PROJECT_KEY, configuredProjectKey)
      .addStringProperty(COMPONENT_PROPERTY, "Exact key from this tool, not a wildcard (Java current: module:package). "
        + (isSonarQubeCloud ? "Intended: group labels joined by /. " : "")
        + "Top level when omitted. Current keys: Java/C# namespace_graph; JS/TS/Python file_graph; default_perspective.")
      .addNumberProperty(DEPTH_PROPERTY, isSonarQubeCloud ? "Levels below the start to return. Default 1 for current, all for intended."
        : "Levels below the start to return. Default 1.")
      .addEnumProperty(LANGUAGE_PROPERTY, LANGUAGES, "Language to read. All languages when omitted.")
      .setReadOnlyHint();
    if (isSonarQubeCloud) {
      builder.addStringProperty(DIRECTIVE_ID_PROPERTY, "read one UUID instead of a page.")
        .addNumberProperty(ToolParameters.PAGE_INDEX, "page number, default 1.")
        .addNumberProperty(ToolParameters.PAGE_SIZE, "items per page, default 50, maximum 1000.");
    }
    return builder.build();
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
    if (!DIRECTIVES.equals(target) && (arguments.toMap().get(ToolParameters.PAGE_INDEX) != null
      || arguments.toMap().get(ToolParameters.PAGE_SIZE) != null || arguments.toMap().get(DIRECTIVE_ID_PROPERTY) != null)) {
      throw new IllegalArgumentException("Pagination parameters are only used for directives.");
    }
    if (DIRECTIVES.equals(target)) {
      return directives(serverApi, projectKey, arguments, component, depth, language);
    }
    if (CURRENT.equals(target)) {
      return readCurrent(serverApi, projectKey, component, depth, language);
    }
    return intended(serverApi.architectureApi(), projectKey, ArchitectureProjectContext.projectId(serverApi, projectKey, "Intended architecture"), component, depth, language);
  }

  private static Result directives(ServerApi serverApi, String projectKey, Arguments arguments, @Nullable String component, @Nullable Integer depth,
    @Nullable String language) {
    if (component != null || depth != null || language != null) {
      throw new IllegalArgumentException("Directives reads do not accept component, depth or language filters.");
    }
    var pageIndex = arguments.getOptionalPageIndex();
    var pageSize = arguments.getOptionalPageSize();
    validateDirectivePage(pageIndex, pageSize);
    var projectId = ArchitectureProjectContext.projectId(serverApi, projectKey, "Project directives");
    var id = arguments.toMap().containsKey(DIRECTIVE_ID_PROPERTY) ? arguments.getStringOrThrow(DIRECTIVE_ID_PROPERTY) : null;
    if (id != null) {
      if (pageIndex != null || pageSize != null) {
        throw new IllegalArgumentException("Use either directiveId or pagination parameters.");
      }
      return Result.success(new DirectiveView(projectKey, serverApi.architectureApi().getDirective(projectKey, projectId, id)));
    }
    var page = serverApi.architectureApi().getDirectivePage(projectKey, projectId, arguments.getPageIndexOrDefault(1), arguments.getPageSizeOrDefault(50));
    return Result.success(new DirectivesView(projectKey, page.projectDirectives(), page.page(), page.projectDirectives().isEmpty() && page.page().total() == 0
      ? "No directives are visible. Check project access and whether any directives are defined." : null));
  }

  private static void validateDirectivePage(@Nullable Integer pageIndex, @Nullable Integer pageSize) {
    if ((pageIndex != null && pageIndex < 1) || (pageSize != null && (pageSize < 1 || pageSize > 1000))) {
      throw new IllegalArgumentException("Directive page must be >= 1 and page size must be between 1 and 1000.");
    }
  }

  private record DirectiveView(String projectKey, ProjectDirective directive) {
  }

  private record DirectivesView(String projectKey, List<ProjectDirective> directives, ArchitectureApi.DirectivePageInfo page, @Nullable String message) {
  }

  private Result readCurrent(ServerApi serverApi, String projectKey, @Nullable String component, @Nullable Integer depth, @Nullable String language) {
    try {
      if (!currentReads.tryAcquire(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Architecture reads are busy. Try again shortly.");
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Architecture read interrupted while waiting for capacity.", interrupted);
    }
    try {
      serverApi.requireOrganization();
      return current(serverApi.architectureApi(), projectKey, ArchitectureProjectContext.organizationId(serverApi),
        ArchitectureProjectContext.mainBranchId(serverApi, projectKey), component, depth == null ? 1 : depth, language);
    } finally {
      currentReads.release();
    }
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
