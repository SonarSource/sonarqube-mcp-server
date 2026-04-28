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
package org.sonarsource.sonarqube.mcp.tools.issues;

import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.sonarsource.sonarqube.mcp.SonarQubeVersionChecker;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.features.Feature;
import org.sonarsource.sonarqube.mcp.serverapi.hotspots.HotspotsApi;
import org.sonarsource.sonarqube.mcp.serverapi.issues.IssuesApi;
import org.sonarsource.sonarqube.mcp.serverapi.sca.ScaApi;
import org.sonarsource.sonarqube.mcp.serverapi.sca.response.DependencyRisksResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

/**
 * Unified search tool that returns Sonar issues across three sources:
 * <ul>
 * <li>Classic code-quality issues (bugs, vulnerabilities, code smells) via
 * {@code /api/issues/search}</li>
 * <li>Security Hotspots via {@code /api/hotspots/search}</li>
 * <li>Software Composition Analysis dependency risks via the SCA API</li>
 * </ul>
 *
 * <p>
 * The {@code issueTypes} parameter selects which sources to query; all are
 * queried by default.
 * Each sub-search is best-effort: a failure or unavailability (e.g. SCA not
 * enabled) is recorded in
 * the response's {@code errors} array rather than failing the entire call.
 * </p>
 */
public class SearchIssuesTool extends Tool {

  public static final String TOOL_NAME = "search_sonar_issues";

  public static final String ISSUE_TYPES_PROPERTY = "issueTypes";
  public static final String PROJECTS_PROPERTY = "projects";
  public static final String FILES_PROPERTY = "files";
  public static final String PULL_REQUEST_ID_PROPERTY = "pullRequestId";
  public static final String BRANCH_PROPERTY = "branch";
  public static final String PAGE_PROPERTY = "page";
  public static final String PAGE_SIZE_PROPERTY = "pageSize";

  public static final String SEVERITIES_PROPERTY = "severities";
  public static final String IMPACT_SOFTWARE_QUALITIES_PROPERTY = "impactSoftwareQualities";
  public static final String KEYS_PROPERTY = "keys";
  public static final String STATUS_PROPERTY = "status";

  public static final String STATUS_OPEN = "OPEN";
  public static final String STATUS_RESOLVED = "RESOLVED";

  public static final String TYPE_ISSUE = "ISSUE";
  public static final String TYPE_SECURITY_HOTSPOT = "SECURITY_HOTSPOT";
  public static final String TYPE_DEPENDENCY_RISK = "DEPENDENCY_RISK";

  private static final String[] VALID_ISSUE_TYPES = { TYPE_ISSUE, TYPE_SECURITY_HOTSPOT, TYPE_DEPENDENCY_RISK };
  private static final String[] VALID_SEVERITIES = { "INFO", "LOW", "MEDIUM", "HIGH", "BLOCKER" };
  private static final String[] VALID_IMPACT_SOFTWARE_QUALITIES = { "MAINTAINABILITY", "RELIABILITY", "SECURITY" };
  private static final String[] VALID_STATUSES = { STATUS_OPEN, STATUS_RESOLVED };

  // Internal mappings for the unified OPEN/RESOLVED status
  private static final List<String> ISSUE_OPEN_STATUSES = List.of("OPEN", "CONFIRMED");
  private static final List<String> ISSUE_RESOLVED_STATUSES = List.of("FALSE_POSITIVE", "ACCEPTED", "FIXED");
  private static final List<String> DEPENDENCY_RISK_OPEN_STATUSES = List.of("OPEN", "CONFIRMED");

  private static final McpLogger LOG = McpLogger.getInstance();

  private final ServerApiProvider serverApiProvider;
  private final SonarQubeVersionChecker sonarQubeVersionChecker;
  @Nullable
  private final String configuredProjectKey;

  public SearchIssuesTool(ServerApiProvider serverApiProvider, SonarQubeVersionChecker sonarQubeVersionChecker,
      boolean isSonarQubeCloud, @Nullable String configuredProjectKey) {
    super(createToolDefinition(isSonarQubeCloud), ToolCategory.ISSUES);
    this.serverApiProvider = serverApiProvider;
    this.sonarQubeVersionChecker = sonarQubeVersionChecker;
    this.configuredProjectKey = configuredProjectKey;
  }

  private static McpSchema.Tool createToolDefinition(boolean isSonarQubeCloud) {
    var scope = isSonarQubeCloud ? "my organization's projects" : "my projects";
    var description = "Search for Sonar issues in " + scope + " across all issue types: "
        + "code issues (bugs, vulnerabilities, code smells), Security Hotspots, and dependency risks. "
        + "Use the 'issueTypes' filter to narrow down; all types are queried by default. "
        + "When searching Security Hotspots or Dependency Risks with multiple projects, they are omitted from results unless exactly one project is provided.";

    return SchemaToolBuilder.forOutput(SearchIssuesToolResponse.class)
        .setName(TOOL_NAME)
        .setTitle("Search Sonar Issues")
        .setDescription(description)
        .addEnumProperty(ISSUE_TYPES_PROPERTY, VALID_ISSUE_TYPES,
            "Types of issues to include. Defaults to all: ISSUE (bugs/vulnerabilities/code smells), SECURITY_HOTSPOT, DEPENDENCY_RISK.")
        .addArrayProperty(PROJECTS_PROPERTY, "string",
            "Projects to search in. For ISSUE, accepts multiple entries. For SECURITY_HOTSPOT and DEPENDENCY_RISK, "
                + "exactly one project key is required.")
        .addStringProperty(BRANCH_PROPERTY, "Optional branch name to filter by.")
        .addArrayProperty(FILES_PROPERTY, "string",
            "Optional list of file/component keys to filter results (applies to ISSUE and SECURITY_HOTSPOT).")
        .addStringProperty(PULL_REQUEST_ID_PROPERTY, "Identifier of the Pull Request to search in.")
        .addNumberProperty(PAGE_PROPERTY,
            "Page number for paged results. Defaults to 1.")
        .addNumberProperty(PAGE_SIZE_PROPERTY,
            "Page size for paged results. Must be > 0 and <= 500. Defaults to 100.")
        .addEnumProperty(STATUS_PROPERTY, VALID_STATUSES,
            "Status filter applied to all issue types. OPEN returns actionable issues (open/confirmed/to-review); "
                + "RESOLVED returns closed ones (fixed/accepted/false-positive/reviewed). Defaults to OPEN.")
        .addEnumProperty(SEVERITIES_PROPERTY, VALID_SEVERITIES,
            "Severities filter (applies only to issues and dependency risks). Use ['HIGH','BLOCKER'] for critical issues.")
        .addEnumProperty(IMPACT_SOFTWARE_QUALITIES_PROPERTY, VALID_IMPACT_SOFTWARE_QUALITIES,
            "Software qualities filter (applies only to issues and dependency risks). Use ['SECURITY'] for security issues.")
        .addArrayProperty(KEYS_PROPERTY, "string",
            "Finding keys to fetch specific items (applies to issues and hotspots).")
        .setReadOnlyHint()
        .build();
  }

  private static boolean isResolved(Tool.Arguments arguments) {
    var status = arguments.getOptionalEnumValue(STATUS_PROPERTY, VALID_STATUSES);
    return STATUS_RESOLVED.equals(status);
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var selectedTypes = resolveSelectedTypes(arguments);
    var projects = arguments.getOptionalStringList(PROJECTS_PROPERTY);
    var resolved = isResolved(arguments);
    var errors = Collections.synchronizedList(new ArrayList<String>());

    CompletableFuture<SearchIssuesToolResponse.IssuesGroup> issuesFuture = CompletableFuture.completedFuture(null);
    CompletableFuture<SearchIssuesToolResponse.HotspotsGroup> hotspotsFuture = CompletableFuture.completedFuture(null);
    CompletableFuture<SearchIssuesToolResponse.DependencyRisksGroup> dependencyRisksFuture = CompletableFuture
        .completedFuture(null);

    if (selectedTypes.contains(TYPE_ISSUE)) {
      issuesFuture = CompletableFuture.supplyAsync(() -> {
        try {
          return searchIssues(arguments, projects, resolved);
        } catch (Exception e) {
          LOG.error("Failed to search issues", e);
          errors.add("Failed to search issues: " + errorMessage(e));
          return null;
        }
      });
    }

    if (selectedTypes.contains(TYPE_SECURITY_HOTSPOT)) {
      var projectKey = resolveSingleProjectKey(projects, errors, "SECURITY_HOTSPOT");
      if (projectKey != null) {
        hotspotsFuture = CompletableFuture.supplyAsync(() -> {
          try {
            return searchHotspots(arguments, projectKey, resolved);
          } catch (Exception e) {
            LOG.error("Failed to search security hotspots", e);
            errors.add("Failed to search security hotspots: " + errorMessage(e));
            return null;
          }
        });
      }
    }

    if (selectedTypes.contains(TYPE_DEPENDENCY_RISK)) {
      var projectKey = resolveSingleProjectKey(projects, errors, "DEPENDENCY_RISK");
      if (projectKey != null) {
        dependencyRisksFuture = CompletableFuture.supplyAsync(() -> {
          try {
            return searchDependencyRisks(arguments, projectKey, resolved, errors);
          } catch (Exception e) {
            LOG.error("Failed to search dependency risks", e);
            errors.add("Failed to search dependency risks: " + errorMessage(e));
            return null;
          }
        });
      }
    }

    CompletableFuture.allOf(issuesFuture, hotspotsFuture, dependencyRisksFuture).join();

    var response = new SearchIssuesToolResponse(issuesFuture.join(), hotspotsFuture.join(),
        dependencyRisksFuture.join(), errors.isEmpty() ? null : errors);
    return Tool.Result.success(response);
  }

  private static Set<String> resolveSelectedTypes(Tool.Arguments arguments) {
    var requested = arguments.getOptionalEnumList(ISSUE_TYPES_PROPERTY, VALID_ISSUE_TYPES);
    if (requested == null || requested.isEmpty()) {
      return Set.of(TYPE_ISSUE, TYPE_SECURITY_HOTSPOT, TYPE_DEPENDENCY_RISK);
    }
    return Set.copyOf(requested);
  }

  @Nullable
  private String resolveSingleProjectKey(@Nullable List<String> projects, List<String> errors, String typeName) {
    if (projects == null || projects.isEmpty()) {
      if (configuredProjectKey != null && !configuredProjectKey.isBlank()) {
        return configuredProjectKey;
      }
      errors.add("'projects' must contain exactly one project key when " + typeName + " is requested");
      return null;
    }
    if (projects.size() > 1) {
      errors.add("'projects' must contain exactly one project key when " + typeName + " is requested");
      return null;
    }
    return projects.getFirst();
  }

  private SearchIssuesToolResponse.IssuesGroup searchIssues(Tool.Arguments arguments, @Nullable List<String> projects,
      boolean resolved) {
    var searchParams = new IssuesApi.SearchParams(
        projects,
        arguments.getOptionalString(BRANCH_PROPERTY),
        arguments.getOptionalStringList(FILES_PROPERTY),
        arguments.getOptionalString(PULL_REQUEST_ID_PROPERTY),
        arguments.getOptionalEnumList(SEVERITIES_PROPERTY, VALID_SEVERITIES),
        arguments.getOptionalEnumList(IMPACT_SOFTWARE_QUALITIES_PROPERTY, VALID_IMPACT_SOFTWARE_QUALITIES),
        resolved ? ISSUE_RESOLVED_STATUSES : ISSUE_OPEN_STATUSES,
        arguments.getOptionalStringList(KEYS_PROPERTY),
        arguments.getOptionalInteger(PAGE_PROPERTY),
        arguments.getOptionalInteger(PAGE_SIZE_PROPERTY));
    var response = serverApiProvider.get().issuesApi().search(searchParams);

    var issues = response.issues().stream()
        .map(issue -> {
          SearchIssuesToolResponse.IssueTextRange textRange = null;
          if (issue.textRange() != null) {
            textRange = new SearchIssuesToolResponse.IssueTextRange(
                issue.textRange().startLine(),
                issue.textRange().endLine());
          }
          return new SearchIssuesToolResponse.Issue(
              issue.key(),
              issue.rule(),
              issue.project(),
              issue.component(),
              issue.severity(),
              issue.status(),
              issue.message(),
              issue.cleanCodeAttribute(),
              issue.cleanCodeAttributeCategory(),
              issue.author(),
              issue.creationDate(),
              textRange);
        })
        .toList();
    var paging = response.paging();
    return new SearchIssuesToolResponse.IssuesGroup(
        issues,
        new SearchIssuesToolResponse.Paging(paging.pageIndex(), paging.pageSize(), paging.total()));
  }

  private SearchIssuesToolResponse.HotspotsGroup searchHotspots(Tool.Arguments arguments, String projectKey,
      boolean resolved) {
    var searchParams = new HotspotsApi.SearchParams(
        projectKey,
        arguments.getOptionalString(BRANCH_PROPERTY),
        arguments.getOptionalString(PULL_REQUEST_ID_PROPERTY),
        arguments.getOptionalStringList(FILES_PROPERTY),
        arguments.getOptionalStringList(KEYS_PROPERTY),
        resolved ? "REVIEWED" : "TO_REVIEW",
        null,
        null,
        null,
        arguments.getOptionalInteger(PAGE_PROPERTY),
        arguments.getOptionalInteger(PAGE_SIZE_PROPERTY));
    var response = serverApiProvider.get().hotspotsApi().search(searchParams);

    var hotspots = response.hotspots().stream()
        .map(hotspot -> {
          SearchIssuesToolResponse.HotspotTextRange textRange = null;
          if (hotspot.textRange() != null) {
            textRange = new SearchIssuesToolResponse.HotspotTextRange(
                hotspot.textRange().startLine(),
                hotspot.textRange().endLine(),
                hotspot.textRange().startOffset(),
                hotspot.textRange().endOffset());
          }
          return new SearchIssuesToolResponse.Hotspot(
              hotspot.key(),
              hotspot.component(),
              hotspot.project(),
              hotspot.securityCategory(),
              hotspot.vulnerabilityProbability(),
              hotspot.status(),
              hotspot.resolution(),
              hotspot.line(),
              hotspot.message(),
              hotspot.assignee(),
              hotspot.author(),
              hotspot.creationDate(),
              hotspot.updateDate(),
              textRange,
              hotspot.ruleKey());
        })
        .toList();
    var paging = response.paging();
    return new SearchIssuesToolResponse.HotspotsGroup(
        hotspots,
        new SearchIssuesToolResponse.Paging(paging.pageIndex(), paging.pageSize(), paging.total()));
  }

  @Nullable
  private SearchIssuesToolResponse.DependencyRisksGroup searchDependencyRisks(Tool.Arguments arguments,
      String projectKey, boolean resolved,
      List<String> errors) {
    var provider = serverApiProvider.get();
    var availabilityError = checkScaAvailability(provider);
    if (availabilityError != null) {
      errors.add(availabilityError);
      return null;
    }

    var searchParams = new ScaApi.SearchParams(
        projectKey,
        arguments.getOptionalString(BRANCH_PROPERTY),
        arguments.getOptionalString(PULL_REQUEST_ID_PROPERTY),
        arguments.getOptionalEnumList(SEVERITIES_PROPERTY, VALID_SEVERITIES),
        arguments.getOptionalEnumList(IMPACT_SOFTWARE_QUALITIES_PROPERTY, VALID_IMPACT_SOFTWARE_QUALITIES),
        resolved ? ISSUE_RESOLVED_STATUSES : DEPENDENCY_RISK_OPEN_STATUSES,
        arguments.getOptionalInteger(PAGE_PROPERTY),
        arguments.getOptionalInteger(PAGE_SIZE_PROPERTY));

    var response = provider.scaApi().getDependencyRisks(searchParams);

    var items = response.issuesReleases().stream()
        .map(SearchIssuesTool::mapIssueRelease)
        .toList();

    SearchIssuesToolResponse.Paging paging = null;
    if (response.page() != null) {
      var pg = response.page();
      paging = new SearchIssuesToolResponse.Paging(pg.pageIndex(), pg.pageSize(), pg.total());
    }
    return new SearchIssuesToolResponse.DependencyRisksGroup(Collections.unmodifiableList(items), paging);
  }

  @Nullable
  private String checkScaAvailability(ServerApi provider) {
    if (!provider.isSonarQubeCloud() && !sonarQubeVersionChecker.isSonarQubeServerVersionHigherOrEqualsThan("2025.4")) {
      return "Dependency risks search is not available because it requires SonarQube Server 2025.4 Enterprise or higher.";
    }
    if (provider.isSonarQubeCloud() && !provider.scaApi().isScaEnabled()) {
      return "Dependency risks search is not available in your SonarQube Cloud organization because Advanced Security is not enabled.";
    }
    if (!provider.isSonarQubeCloud() && !provider.featuresApi().listFeatures().contains(Feature.SCA)) {
      return "Dependency risks search is not available for SonarQube Server because Advanced Security is not enabled.";
    }
    return null;
  }

  private static SearchIssuesToolResponse.IssueRelease mapIssueRelease(DependencyRisksResponse.IssueRelease ir) {
    SearchIssuesToolResponse.Release release = null;
    if (ir.release() != null) {
      var r = ir.release();
      release = new SearchIssuesToolResponse.Release(
          r.packageName(), r.version(), r.packageManager(),
          r.newlyIntroduced(), r.directSummary());
    }

    SearchIssuesToolResponse.Assignee assignee = null;
    if (ir.assignee() != null) {
      assignee = new SearchIssuesToolResponse.Assignee(ir.assignee().name());
    }

    return new SearchIssuesToolResponse.IssueRelease(
        ir.key(), ir.severity(), ir.type(), ir.quality(), ir.status(), ir.createdAt(),
        ir.vulnerabilityId(), ir.cvssScore(), release, assignee);
  }

  private static String errorMessage(Exception e) {
    var message = e.getMessage();
    return message != null ? message : e.getClass().getSimpleName();
  }

}
