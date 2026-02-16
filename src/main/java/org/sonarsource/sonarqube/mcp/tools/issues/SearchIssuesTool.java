/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
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
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.issues.IssuesApi;
import org.sonarsource.sonarqube.mcp.serverapi.issues.response.SearchResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class SearchIssuesTool extends Tool {

  public static final String TOOL_NAME = "search_sonar_issues_in_projects";

  public static final String PROJECTS_PROPERTY = "projects";
  public static final String FILES_PROPERTY = "files";
  public static final String PULL_REQUEST_ID_PROPERTY = "pullRequestId";
  public static final String SEVERITIES_PROPERTY = "severities";
  public static final String IMPACT_SOFTWARE_QUALITIES_PROPERTY = "impactSoftwareQualities";
  public static final String ISSUE_STATUSES_PROPERTY = "issueStatuses";
  public static final String ISSUE_KEY_PROPERTY = "issueKey";
  public static final String PAGE_PROPERTY = "p";
  public static final String PAGE_SIZE_PROPERTY = "ps";
  
  private static final String[] VALID_SEVERITIES = {"INFO", "LOW", "MEDIUM", "HIGH", "BLOCKER"};
  private static final String[] VALID_IMPACT_SOFTWARE_QUALITIES = {"MAINTAINABILITY", "RELIABILITY", "SECURITY"};
  private static final String[] VALID_ISSUE_STATUSES = {"OPEN", "CONFIRMED", "FALSE_POSITIVE", "ACCEPTED", "FIXED", "IN_SANDBOX"};
  
  private final ServerApiProvider serverApiProvider;

  public SearchIssuesTool(ServerApiProvider serverApiProvider, boolean isSonarCloud) {
    super(createToolDefinition(isSonarCloud),
      ToolCategory.ISSUES);
    this.serverApiProvider = serverApiProvider;
  }

  private static McpSchema.Tool createToolDefinition(boolean isSonarCloud) {
    var scope = isSonarCloud ? "my organization's projects" : "my projects";
    var description = "Search for issues (bugs, vulnerabilities, code smells) in " + scope + ". " +
      "Filter by severities=['HIGH','BLOCKER'] for critical issues, impactSoftwareQualities=['SECURITY'] for security, issueStatuses=['OPEN'] to exclude resolved.";
    
    return SchemaToolBuilder.forOutput(SearchIssuesToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Search SonarQube Issues")
      .setDescription(description)
      .addArrayProperty(PROJECTS_PROPERTY, "string", "An optional list of Sonar projects to look in")
      .addArrayProperty(FILES_PROPERTY, "string", "An optional list of component keys (files, directories, modules) to filter issues")
      .addStringProperty(PULL_REQUEST_ID_PROPERTY, "The identifier of the Pull Request to look in")
      .addEnumProperty(SEVERITIES_PROPERTY, VALID_SEVERITIES, "An optional list of severities to filter by")
      .addEnumProperty(IMPACT_SOFTWARE_QUALITIES_PROPERTY, VALID_IMPACT_SOFTWARE_QUALITIES, "An optional list of software qualities to filter by")
      .addEnumProperty(ISSUE_STATUSES_PROPERTY, VALID_ISSUE_STATUSES, "An optional list of issue statuses to filter by. Note: IN_SANDBOX is valid only for SonarQube Server")
      .addArrayProperty(ISSUE_KEY_PROPERTY, "string", "An optional list of issue keys to fetch specific issues")
      .addNumberProperty(PAGE_PROPERTY, "An optional page number. Defaults to 1.")
      .addNumberProperty(PAGE_SIZE_PROPERTY, "An optional page size. Must be greater than 0 and less than or equal to 500. Defaults to 100.")
      .setReadOnlyHint()
      .build();
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var searchParams = extractSearchParams(arguments);
    var response = serverApiProvider.get().issuesApi().search(searchParams);
    var toolResponse = buildStructuredContent(response);
    return Tool.Result.success(toolResponse);
  }

  private static IssuesApi.SearchParams extractSearchParams(Tool.Arguments arguments) {
    return new IssuesApi.SearchParams(
      arguments.getOptionalStringList(PROJECTS_PROPERTY),
      null,
      arguments.getOptionalStringList(FILES_PROPERTY),
      arguments.getOptionalString(PULL_REQUEST_ID_PROPERTY),
      arguments.getOptionalEnumList(SEVERITIES_PROPERTY, VALID_SEVERITIES),
      arguments.getOptionalEnumList(IMPACT_SOFTWARE_QUALITIES_PROPERTY, VALID_IMPACT_SOFTWARE_QUALITIES),
      arguments.getOptionalEnumList(ISSUE_STATUSES_PROPERTY, VALID_ISSUE_STATUSES),
      arguments.getOptionalStringList(ISSUE_KEY_PROPERTY),
      arguments.getOptionalInteger(PAGE_PROPERTY),
      arguments.getOptionalInteger(PAGE_SIZE_PROPERTY)
    );
  }

  private static SearchIssuesToolResponse buildStructuredContent(SearchResponse response) {
    var issues = response.issues().stream()
      .map(issue -> {
        SearchIssuesToolResponse.TextRange textRange = null;
        if (issue.textRange() != null) {
          textRange = new SearchIssuesToolResponse.TextRange(
            issue.textRange().startLine(),
            issue.textRange().endLine()
          );
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
          textRange
        );
      })
      .toList();

    var paging = response.paging();
    var pagingResponse = new SearchIssuesToolResponse.Paging(
      paging.pageIndex(),
      paging.pageSize(),
      paging.total()
    );

    return new SearchIssuesToolResponse(issues, pagingResponse);
  }

}
