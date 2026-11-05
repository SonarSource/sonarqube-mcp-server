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
import java.util.UUID;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.IntendedArchitecture;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectDirective;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectFinding;
import org.sonarsource.sonarqube.mcp.tools.BranchPullRequestContext;
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
  public static final String DIRECTIVE_ID_PROPERTY = "directiveId";
  public static final String LANGUAGE_PROPERTY = "language";
  public static final String CURRENT = "current";
  public static final String INTENDED = "intended";
  public static final String DIRECTIVES = "directives";
  public static final String FINDINGS = "findings";
  private static final String TYPE_PROPERTY = "type";
  private static final String CATEGORY_PROPERTY = "category";

  static final String[] LANGUAGES = ArchitectureApi.LANGUAGES.toArray(String[]::new);
  private static final String[] CATEGORIES = ArchitectureApi.FINDING_CATEGORIES.toArray(String[]::new);
  private static final String[] TARGETS = {CURRENT, INTENDED, DIRECTIVES, FINDINGS};
  private static final int MAX_DIRECTIVE_PAGE_SIZE = 50;
  static final String FILE_MANAGED_MESSAGE = "A repository file manages this model: edit sonar-architecture.json, .yml or .yaml in the project base "
    + "directory (top-level perspectives, with qualifiers for type) and open a pull request. architecture_set refuses intended writes.";
  private final CurrentGraphReads currentReads;

  private final ServerApiProvider serverApiProvider;
  @Nullable
  private final String configuredProjectKey;

  public ArchitectureGetTool(ServerApiProvider serverApiProvider, boolean isSonarQubeCloud, @Nullable String configuredProjectKey) {
    this(serverApiProvider, isSonarQubeCloud, configuredProjectKey, new CurrentGraphReads());
  }

  ArchitectureGetTool(ServerApiProvider serverApiProvider, boolean isSonarQubeCloud, @Nullable String configuredProjectKey, CurrentGraphReads currentReads) {
    super(definition(isSonarQubeCloud, configuredProjectKey), ToolCategory.ARCHITECTURE);
    this.currentReads = currentReads;
    this.serverApiProvider = serverApiProvider;
    this.configuredProjectKey = configuredProjectKey;
  }

  private static McpSchema.Tool definition(boolean isSonarQubeCloud, @Nullable String configuredProjectKey) {
    var builder = (isSonarQubeCloud ? ToolDefinitionBuilder.cloudBuilder() : ToolDefinitionBuilder.builder())
      .setName(TOOL_NAME)
      .setTitle("Get Architecture")
      .setDescription("Read current graphs, intended architecture, project directives or findings.")
      .addRequiredEnumProperty(TARGET_PROPERTY, TARGETS,
        "current: dependencies; intended: model and its source (FILE or SERVER) for the branch; directives: project directives (directiveId); "
          + "findings: deviations, flaws and smells (category, type, language); pageIndex/pageSize apply to directives and findings.")
      .addProjectKeyProperty(ToolParameters.PROJECT_KEY, configuredProjectKey)
      .addStringProperty(COMPONENT_PROPERTY, "Exact key from this tool, not a wildcard (Java current: module:package). "
        + "Intended: group labels joined by /. Top level when omitted."
        + (isSonarQubeCloud ? " Current keys: Java/C# namespace_graph; JS/TS/Python file_graph; default_perspective." : ""))
      .addNumberProperty(DEPTH_PROPERTY, "Levels below the start to return. Default 1 for current, all for intended.")
      .addEnumProperty(LANGUAGE_PROPERTY, LANGUAGES, "Language to read. All languages when omitted.")
      .addBranchAndPullRequestProperties()
      .setReadOnlyHint();
    builder.addStringProperty(DIRECTIVE_ID_PROPERTY, "Full directive by UUID; pages omit descriptions.")
      .addEnumProperty(TYPE_PROPERTY, ArchitectureApi.FINDING_TYPES.toArray(String[]::new), "only findings of this type.")
      .addEnumProperty(CATEGORY_PROPERTY, CATEGORIES, "only findings of this category.")
      .addNumberProperty(ToolParameters.PAGE_INDEX, "page number, default 1.")
      .addNumberProperty(ToolParameters.PAGE_SIZE, "directives: default and maximum " + MAX_DIRECTIVE_PAGE_SIZE + "; findings: default 50, maximum 1000 (0 counts only).");
    return builder.build();
  }

  @Override
  public Result execute(Arguments arguments) {
    var target = arguments.getEnumOrThrow(TARGET_PROPERTY, TARGETS);
    var projectKey = arguments.getProjectKeyWithFallback(ToolParameters.PROJECT_KEY, configuredProjectKey);
    var component = arguments.getOptionalString(COMPONENT_PROPERTY);
    var depth = arguments.getOptionalInteger(DEPTH_PROPERTY);
    var language = arguments.getOptionalEnumValue(LANGUAGE_PROPERTY, LANGUAGES);
    if (depth != null && depth < 1) {
      throw new IllegalArgumentException("depth must be 1 or more.");
    }
    var branch = BranchPullRequestContext.from(arguments);
    var branchError = branch.validationError();
    if (branchError.isPresent()) {
      return branchError.get();
    }

    var serverApi = serverApiProvider.get(arguments.getOptionalString(ToolParameters.ORGANIZATION));
    var parameters = arguments.toMap();
    if (!DIRECTIVES.equals(target) && !FINDINGS.equals(target)
      && (parameters.get(ToolParameters.PAGE_INDEX) != null || parameters.get(ToolParameters.PAGE_SIZE) != null)) {
      throw new IllegalArgumentException("Pagination parameters are only used for directives and findings.");
    }
    if (!DIRECTIVES.equals(target) && parameters.get(DIRECTIVE_ID_PROPERTY) != null) {
      throw new IllegalArgumentException("directiveId is only used for directives.");
    }
    if (!FINDINGS.equals(target) && (parameters.get(TYPE_PROPERTY) != null || parameters.get(CATEGORY_PROPERTY) != null)) {
      throw new IllegalArgumentException("type and category are only used for findings.");
    }
    if (DIRECTIVES.equals(target) && (branch.branch() != null || branch.pullRequest() != null)) {
      throw new IllegalArgumentException("branch and pullRequest are not used for directives, which apply to the whole project.");
    }
    if (DIRECTIVES.equals(target)) {
      return directives(serverApi, projectKey, arguments, component, depth, language);
    }
    if (FINDINGS.equals(target)) {
      return findings(serverApi, projectKey, branch, arguments, component, depth, language);
    }
    if (CURRENT.equals(target)) {
      return readCurrent(serverApi, projectKey, branch, component, depth, language);
    }
    return intended(serverApi, projectKey, branch, component, depth, language);
  }

  private static Result directives(ServerApi serverApi, String projectKey, Arguments arguments, @Nullable String component, @Nullable Integer depth,
    @Nullable String language) {
    if (component != null || depth != null || language != null) {
      throw new IllegalArgumentException("Directives reads do not accept component, depth or language filters.");
    }
    var pageIndex = arguments.getOptionalPageIndex();
    var pageSize = arguments.getOptionalPageSize();
    validateDirectivePage(pageIndex, pageSize);
    var id = arguments.toMap().containsKey(DIRECTIVE_ID_PROPERTY) ? arguments.getStringOrThrow(DIRECTIVE_ID_PROPERTY) : null;
    if (id != null && (pageIndex != null || pageSize != null)) {
      throw new IllegalArgumentException("Use either directiveId or pagination parameters.");
    }
    if (id != null && !UUID.fromString(id).toString().equalsIgnoreCase(id)) {
      throw new IllegalArgumentException("Directive id must be a UUID.");
    }
    var projectId = ArchitectureProjectContext.projectId(serverApi, projectKey);
    if (id != null) {
      return Result.success(new DirectiveView(projectKey, serverApi.architectureApi().getDirective(projectKey, projectId, id)));
    }
    var page = serverApi.architectureApi().getDirectivePage(projectKey, projectId, arguments.getPageIndexOrDefault(1), arguments.getPageSizeOrDefault(MAX_DIRECTIVE_PAGE_SIZE));
    return Result.success(new DirectivesView(projectKey, "summary",
      page.projectDirectives().stream().map(DirectiveResponses::summary).toList(), page.page(), page.projectDirectives().isEmpty() && page.page().total() == 0
      ? "No directives are visible. Check project access and whether any directives are defined." : null));
  }

  private static Result findings(ServerApi serverApi, String projectKey, BranchPullRequestContext.Params branch, Arguments arguments, @Nullable String component, @Nullable Integer depth,
    @Nullable String language) {
    if (component != null || depth != null) {
      throw new IllegalArgumentException("Findings reads do not accept component or depth filters.");
    }
    if (arguments.toMap().containsKey("findingId")) {
      throw new IllegalArgumentException("Findings have no ids. Use category, type or language to filter the list.");
    }
    var pageIndex = arguments.getPageIndexOrDefault(1);
    var pageSize = arguments.getPageSizeOrDefault(50);
    ArchitectureApi.validateFindingPage(pageIndex, pageSize);
    var category = arguments.getOptionalEnumValue(CATEGORY_PROPERTY, CATEGORIES);
    var type = arguments.getOptionalEnumValue(TYPE_PROPERTY, ArchitectureApi.FINDING_TYPES.toArray(String[]::new));
    var organizationId = ArchitectureProjectContext.organizationId(serverApi);
    var branchId = ArchitectureProjectContext.branchId(serverApi, projectKey, branch);
    var page = serverApi.architectureApi().getFindingPage(projectKey, organizationId, branchId, new ArchitectureApi.FindingFilters(category, type, language), pageIndex, pageSize);
    return Result.success(new FindingsView(projectKey, page.findings(), page.page(), page.findings().isEmpty() && page.page().total() == 0
      ? "No findings are visible. Check project access and whether the project has been analyzed." : null));
  }

  private static void validateDirectivePage(@Nullable Integer pageIndex, @Nullable Integer pageSize) {
    if (pageIndex != null && pageIndex < 1) {
      throw new IllegalArgumentException("Directive page must be >= 1.");
    }
    if (pageSize != null && (pageSize < 1 || pageSize > MAX_DIRECTIVE_PAGE_SIZE)) {
      throw new IllegalArgumentException("Directive page size must be between 1 and " + MAX_DIRECTIVE_PAGE_SIZE
        + ". Restart at pageIndex=1 with pageSize<=" + MAX_DIRECTIVE_PAGE_SIZE + " and continue paging. Nothing was changed.");
    }
  }

  private record DirectiveView(String projectKey, ProjectDirective directive) {
  }

  private record DirectivesView(String projectKey, String view, List<DirectiveResponses.Summary> directives, ArchitectureApi.DirectivePageInfo page, @Nullable String message) {
  }

  private record FindingsView(String projectKey, List<ProjectFinding> findings, ArchitectureApi.DirectivePageInfo page, @Nullable String message) {
  }

  private Result readCurrent(ServerApi serverApi, String projectKey, BranchPullRequestContext.Params branch, @Nullable String component, @Nullable Integer depth,
    @Nullable String language) {
    return currentReads.execute(() -> {
      serverApi.requireOrganization();
      return current(serverApi.architectureApi(), projectKey, ArchitectureProjectContext.organizationId(serverApi),
        ArchitectureProjectContext.branchId(serverApi, projectKey, branch), component, depth == null ? 1 : depth, language);
    });
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

  private static Result intended(ServerApi serverApi, String projectKey, BranchPullRequestContext.Params branch, @Nullable String component,
    @Nullable Integer depth, @Nullable String language) {
    var projectId = ArchitectureProjectContext.projectId(serverApi, projectKey);
    var resolution = IntendedSource.resolve(serverApi, projectKey, branch);
    var view = new IntendedView(projectKey, resolution, branch, component, depth, language);
    if (resolution.isManagedByFile()) {
      String problem = null;
      if (resolution.fileModels().isEmpty()) {
        problem = resolution.problem() != null ? resolution.problem()
          : resolution.fileAnalysed() ? "The last analysis found no usable architecture file, so it ran without an intended model."
          : "No analysis has read the architecture file yet.";
      } else if (resolution.droppedConstraints() > 0) {
        problem = resolution.droppedConstraints() + " constraints other than allow constraints are not shown; read the file for them.";
      }
      var message = problem == null ? FILE_MANAGED_MESSAGE : (problem + " " + FILE_MANAGED_MESSAGE);
      return view.result(null, resolution.fileModels(), message, resolution.droppedConstraints() > 0);
    }
    var stored = serverApi.architectureApi().getIntended(projectKey, projectId);
    var message = resolution.isServerManaged() ? null : ("Whether a repository file manages this model is unknown. " + resolution.problem());
    return view.result(IntendedRevision.of(stored), stored, message, false);
  }

  private record IntendedView(String projectKey, IntendedSource.Resolution resolution, BranchPullRequestContext.Params branch, @Nullable String component,
    @Nullable Integer depth, @Nullable String language) {

    private Result result(@Nullable String revision, List<IntendedArchitecture> stored, @Nullable String message, boolean incomplete) {
      var partial = component != null || depth != null || language != null || incomplete;
      var models = stored.stream()
        .filter(model -> language == null || language.equals(model.language()))
        .toList();
      if (models.isEmpty()) {
        var none = "No intended architecture is visible for project '" + projectKey + "'" + (language == null ? "" : (" in " + language)) + ".";
        if (resolution.isServerManaged()) {
          none += " It may be undefined, inaccessible or in another organization.";
        }
        return response(revision, List.of(), partial, message == null ? none : (none + " " + message));
      }
      var views = models.stream()
        .map(model -> toView(model, component, depth, partial))
        .filter(Objects::nonNull)
        .toList();
      if (views.isEmpty()) {
        throw new IllegalArgumentException("No group '" + component + "' in the intended architecture. Call " + TOOL_NAME
          + " with target intended and without component to list the group keys.");
      }
      return response(revision, views, partial, message);
    }

    private Result response(@Nullable String revision, List<Model> models, boolean partial, @Nullable String message) {
      return Result.success(new ArchitectureGetIntendedToolResponse(projectKey, resolution.source(), resolution.setting(), branch.branch(), branch.pullRequest(),
        resolution.fromMainBranch() ? true : null, revision, component, depth, models, partial ? true : null, message));
    }
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
