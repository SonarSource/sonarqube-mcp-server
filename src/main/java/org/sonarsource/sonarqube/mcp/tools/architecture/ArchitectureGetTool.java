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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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
  public static final String DIRECTIVE_ID_PROPERTY = "directiveId";
  public static final String LANGUAGE_PROPERTY = "language";
  private static final String FROM_PROPERTY = "from";
  private static final String TO_PROPERTY = "to";
  public static final String CURRENT = "current";
  public static final String INTENDED = "intended";
  public static final String DIRECTIVES = "directives";
  public static final String FINDINGS = "findings";
  static final String CURSOR_PROPERTY = "cursor";
  private static final String TYPE_PROPERTY = "type";
  private static final String CATEGORY_PROPERTY = "category";

  static final String[] LANGUAGES = ArchitectureApi.LANGUAGES.toArray(String[]::new);
  private static final String[] CATEGORIES = ArchitectureApi.FINDING_CATEGORIES.toArray(String[]::new);
  private static final String[] TARGETS = {CURRENT, INTENDED, DIRECTIVES, FINDINGS};
  static final int MAX_DEPTH = 64;
  private static final int MAX_DIRECTIVE_PAGE_SIZE = 50;
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
        "current: dependencies; intended: model; directives: project directives (directiveId); "
          + "findings: deviations, flaws and smells (category, type, language); pageIndex/pageSize apply to directives and findings or current from/to queries.")
      .addStringProperty(FROM_PROPERTY, "Current: source including descendants. Findings: relationship source. Requires to; excludes component.")
      .addStringProperty(TO_PROPERTY, "Destination key; requires from. Current includes descendants.")
      .addProjectKeyProperty(ToolParameters.PROJECT_KEY, configuredProjectKey)
      .addStringProperty(COMPONENT_PROPERTY, "Exact key from this tool, not a wildcard (Java current: module:package). "
        + "Intended: group labels joined by /. Findings: node key filter on this page. Top level when omitted."
        + (isSonarQubeCloud ? " Current keys: Java/C# namespace_graph; JS/TS/Python file_graph; default_perspective." : ""))
      .addIntegerProperty(DEPTH_PROPERTY, "Levels below the start (1..64). Default 1 for current; all levels for intended when omitted.", 1, MAX_DEPTH)
      .addEnumProperty(LANGUAGE_PROPERTY, LANGUAGES, "Language to read. All languages when omitted.")
      .addIntegerProperty(ToolParameters.PAGE_INDEX, "Page from 1; directives, findings or current from/to queries.", 1, null)
      .addIntegerProperty(ToolParameters.PAGE_SIZE, "Current from/to: default/max " + DependencyQuery.MAX_PAGE_SIZE
        + "; directives: default and maximum " + MAX_DIRECTIVE_PAGE_SIZE + "; findings: default 50, maximum 50 (0 counts only).", 0, 50)
      .setReadOnlyHint();
    builder.addStringProperty(CURSOR_PROPERTY, "Finding nextCursor; use with target findings and projectKey, without filters or pagination.")
      .addStringProperty(DIRECTIVE_ID_PROPERTY, "Full directive by UUID; pages omit descriptions.")
      .addEnumProperty(TYPE_PROPERTY, ArchitectureApi.FINDING_TYPES.toArray(String[]::new), "only findings of this type.")
      .addEnumProperty(CATEGORY_PROPERTY, CATEGORIES, "only findings of this category.");
    return builder.build();
  }

  @Override
  public Result execute(Arguments arguments) {
    arguments = new ArchitectureArguments(arguments);
    var target = arguments.getEnumOrThrow(TARGET_PROPERTY, TARGETS);
    var projectKey = arguments.getProjectKeyWithFallback(ToolParameters.PROJECT_KEY, configuredProjectKey);
    var component = arguments.getOptionalString(COMPONENT_PROPERTY);
    var depth = arguments.getOptionalInteger(DEPTH_PROPERTY);
    var language = arguments.getOptionalEnumValue(LANGUAGE_PROPERTY, LANGUAGES);
    if (depth != null && (depth < 1 || depth > MAX_DEPTH)) {
      throw new IllegalArgumentException("depth must be between 1 and " + MAX_DEPTH + ".");
    }

    validateCursorArguments(arguments, target);
    var selection = FINDINGS.equals(target) ? null : dependencySelection(arguments, target, component);
    rejectParametersForOtherTargets(arguments, target, selection);
    var serverApi = serverApiProvider.get(arguments.getOptionalString(ToolParameters.ORGANIZATION));
    if (DIRECTIVES.equals(target)) {
      return directives(serverApi, projectKey, arguments, component, depth, language);
    }
    if (FINDINGS.equals(target)) {
      return findings(serverApi, projectKey, arguments, component, depth, language);
    }
    if (CURRENT.equals(target)) {
      return readCurrent(serverApi, projectKey, component, depth, language, selection);
    }
    return intended(serverApi.architectureApi(), projectKey, ArchitectureProjectContext.projectId(serverApi, projectKey), component, depth, language);
  }

  private static void rejectParametersForOtherTargets(Arguments arguments, String target, @Nullable DependencyQuery.Selection selection) {
    var parameters = arguments.toMap();
    if ((INTENDED.equals(target) || (CURRENT.equals(target) && selection == null))
      && (parameters.get(ToolParameters.PAGE_INDEX) != null || parameters.get(ToolParameters.PAGE_SIZE) != null)) {
      throw new IllegalArgumentException("Pagination parameters are only used for directives, findings or current from/to queries.");
    }
    if (!DIRECTIVES.equals(target) && parameters.get(DIRECTIVE_ID_PROPERTY) != null) {
      throw new IllegalArgumentException("directiveId is only used for directives.");
    }
    if (!FINDINGS.equals(target) && (parameters.get(TYPE_PROPERTY) != null || parameters.get(CATEGORY_PROPERTY) != null)) {
      throw new IllegalArgumentException("type and category are only used for findings.");
    }
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

  private static Result findings(ServerApi serverApi, String projectKey, Arguments arguments, @Nullable String component, @Nullable Integer depth,
    @Nullable String language) {
    if (arguments.toMap().containsKey(CURSOR_PROPERTY)) {
      return continueFinding(serverApi, projectKey, arguments.getStringOrThrow(CURSOR_PROPERTY));
    }
    if (depth != null) {
      throw new IllegalArgumentException("Findings reads do not accept a depth filter.");
    }
    if (arguments.toMap().containsKey("findingId")) {
      throw new IllegalArgumentException("Findings have no ids. Use category, type or language to filter the list.");
    }
    var from = exactFindingKey(arguments, FROM_PROPERTY);
    var to = exactFindingKey(arguments, TO_PROPERTY);
    if ((from == null) != (to == null) || (from != null && component != null)) {
      throw new IllegalArgumentException("Use either component or both from and to to filter finding previews.");
    }
    var pageIndex = arguments.getPageIndexOrDefault(1);
    var pageSize = arguments.getPageSizeOrDefault(ArchitectureApi.MAX_FINDING_PAGE_SIZE);
    ArchitectureApi.validateFindingPage(pageIndex, pageSize);
    var filters = new ArchitectureApi.FindingFilters(arguments.getOptionalEnumValue(CATEGORY_PROPERTY, CATEGORIES),
      arguments.getOptionalEnumValue(TYPE_PROPERTY, ArchitectureApi.FINDING_TYPES.toArray(String[]::new)), language);
    var organizationId = ArchitectureProjectContext.organizationId(serverApi);
    var branchId = ArchitectureProjectContext.analyzedMainBranchId(serverApi, projectKey);
    var page = serverApi.architectureApi().getFindingPage(projectKey, organizationId, branchId, filters, pageIndex, pageSize);
    var binding = FindingCursor.binding(projectKey, organizationId, branchId);
    // Count-only pages cannot produce findings or cursors.
    var query = pageSize == 0 ? null : new FindingCursor.Query(pageIndex, pageSize, filters.category(), filters.type(), filters.language(), component, from, to);
    var findings = page.findings().stream()
      .filter(finding -> (component == null && from == null) || FindingResponses.matches(finding, component, from, to))
      .map(finding -> FindingResponses.preview(finding, binding, Objects.requireNonNull(query))).toList();
    var truncated = findings.stream().anyMatch(FindingResponses::truncated);
    var message = findingPageMessage(page, filters);
    if (truncated) {
      message = join(message, "Use a finding's nextCursor with target findings and projectKey to continue its files and node keys.");
    }
    if (component != null || from != null) {
      message = join(message, "Selectors filter this page only; page.total is the backend total before selectors.");
    }
    return Result.success(new FindingsView(projectKey, findings, page.page(), truncated ? true : null, message, page.skippedFindings()));
  }

  private static Result continueFinding(ServerApi serverApi, String projectKey, String encoded) {
    var cursor = FindingCursor.decode(encoded);
    var organizationId = ArchitectureProjectContext.organizationId(serverApi);
    var branchId = ArchitectureProjectContext.analyzedMainBranchId(serverApi, projectKey);
    var binding = FindingCursor.binding(projectKey, organizationId, branchId);
    if (!binding.equals(cursor.binding())) {
      throw FindingCursor.invalid();
    }
    var query = cursor.query();
    var page = serverApi.architectureApi().getFindingPage(projectKey, organizationId, branchId, query.filters(), query.pageIndex(), query.pageSize());
    var finding = FindingResponses.continueFinding(page.findings().stream()
      .filter(candidate -> (query.component() == null && query.from() == null)
        || FindingResponses.matches(candidate, query.component(), query.from(), query.to()))
      .toList(), binding, cursor);
    return Result.success(new FindingsView(projectKey, List.of(finding), page.page(), FindingResponses.truncated(finding) ? true : null,
      findingPageMessage(page, query.filters()), page.skippedFindings()));
  }

  private static void validateCursorArguments(Arguments arguments, String target) {
    if (!arguments.toMap().containsKey(CURSOR_PROPERTY)) {
      return;
    }
    var allowed = Set.of(TARGET_PROPERTY, ToolParameters.PROJECT_KEY, ToolParameters.ORGANIZATION, CURSOR_PROPERTY);
    if (!FINDINGS.equals(target) || !allowed.containsAll(arguments.toMap().keySet())) {
      throw new IllegalArgumentException("Use cursor only with target findings, projectKey and organization when required. Omit filters and pagination.");
    }
    var value = arguments.toMap().get(CURSOR_PROPERTY);
    if (!(value instanceof String encoded)) {
      throw FindingCursor.invalid();
    }
    FindingCursor.decode(encoded);
  }

  @Nullable
  private static String exactFindingKey(Arguments arguments, String name) {
    var key = arguments.getOptionalString(name);
    if (key != null && !key.equals(key.strip())) {
      throw new IllegalArgumentException(name + " must be an exact component key without surrounding whitespace.");
    }
    return key;
  }

  private static String join(@Nullable String message, String addition) {
    return message == null ? addition : (message + " " + addition);
  }

  @Nullable
  private static String findingPageMessage(ArchitectureApi.FindingPage page, ArchitectureApi.FindingFilters filters) {
    var messages = new ArrayList<String>();
    if (page.skippedFindings() != null) {
      messages.add("Skipped %d finding(s) with unsupported type, language or reason; backend total is unchanged.".formatted(page.skippedFindings()));
    }
    if (page.page().total() == 0) {
      var activeFilters = activeFindingFilters(filters);
      messages.add(activeFilters.isEmpty() ? "No findings are visible. Check project access and whether the project has been analyzed."
        : ("No findings match " + String.join(", ", activeFilters) + "."));
    } else if (page.page().pageSize() > 0) {
      var last = (int) Math.ceil((double) page.page().total() / page.page().pageSize());
      if (page.page().pageIndex() > last) {
        messages.add("Requested page %d is past the last page (%d). Use pageIndex=%d.".formatted(page.page().pageIndex(), last, last));
      } else if (page.findings().isEmpty() && page.skippedFindings() == null) {
        messages.add("No findings on page %d; total is %d.".formatted(page.page().pageIndex(), page.page().total()));
      }
    }
    return messages.isEmpty() ? null : String.join(" ", messages);
  }

  private static List<String> activeFindingFilters(ArchitectureApi.FindingFilters filters) {
    var activeFilters = new ArrayList<String>();
    if (filters.category() != null) {
      activeFilters.add("category=" + filters.category());
    }
    if (filters.type() != null) {
      activeFilters.add("type=" + filters.type());
    }
    if (filters.language() != null) {
      activeFilters.add("language=" + filters.language());
    }
    return activeFilters;
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

  private record FindingsView(String projectKey, List<Object> findings, ArchitectureApi.DirectivePageInfo page, @Nullable Boolean truncated,
    @Nullable String message, @Nullable Integer skippedFindings) {
  }

  private static @Nullable DependencyQuery.Selection dependencySelection(Arguments arguments, String target, @Nullable String component) {
    var from = arguments.getOptionalString(FROM_PROPERTY);
    var to = arguments.getOptionalString(TO_PROPERTY);
    if (!arguments.toMap().containsKey(FROM_PROPERTY) && !arguments.toMap().containsKey(TO_PROPERTY)) {
      return null;
    }
    if (!CURRENT.equals(target) || from == null || to == null || component != null) {
      throw new IllegalArgumentException("Use both from and to with target current, without component.");
    }
    for (var field : List.of(ToolParameters.PAGE_INDEX, ToolParameters.PAGE_SIZE)) {
      var value = arguments.toMap().get(field);
      if (value != null && !(value instanceof Number) && !(value instanceof String string && !string.isBlank())) {
        throw new IllegalArgumentException(field + " must be an integer.");
      }
    }
    return new DependencyQuery.Selection(from, to, arguments.getPageIndexOrDefault(1), arguments.getPageSizeOrDefault(DependencyQuery.MAX_PAGE_SIZE));
  }

  private Result readCurrent(ServerApi serverApi, String projectKey, @Nullable String component, @Nullable Integer depth, @Nullable String language,
    @Nullable DependencyQuery.Selection selection) {
    return currentReads.execute(() -> {
      serverApi.requireOrganization();
      return current(serverApi, projectKey, component, depth == null ? 1 : depth, language, selection);
    });
  }

  private static Result current(ServerApi serverApi, String projectKey, @Nullable String component, int depth, @Nullable String language,
    @Nullable DependencyQuery.Selection selection) {
    var api = serverApi.architectureApi();
    var organizationId = ArchitectureProjectContext.organizationId(serverApi);
    var branchId = ArchitectureProjectContext.mainBranchId(serverApi, projectKey);
    var languages = language == null ? ArchitectureApi.LANGUAGES : List.of(language);
    var graphs = languages.stream()
      .flatMap(lang -> api.getStructure(projectKey, organizationId, branchId, lang).stream().map(graph -> new GraphSlicer.LanguageGraph(lang, graph)))
      .filter(languageGraph -> languageGraph.graph().nodes() != null && !languageGraph.graph().nodes().isEmpty())
      .toList();
    if (selection != null) {
      return Result.success(DependencyQuery.query(projectKey, graphs, selection, depth));
    }
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
    var warning = IntendedModels.invalidReason(stored) == null ? null
      : "Stored intended architecture contains values that must be corrected before saving.";
    return Result.success(new ArchitectureGetIntendedToolResponse(projectKey, revision, component, depth, views, partial ? true : null, warning));
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
