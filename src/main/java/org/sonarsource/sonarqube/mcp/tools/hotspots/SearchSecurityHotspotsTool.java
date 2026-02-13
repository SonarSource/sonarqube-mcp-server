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
package org.sonarsource.sonarqube.mcp.tools.hotspots;

import javax.annotation.CheckForNull;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.hotspots.HotspotsApi;
import org.sonarsource.sonarqube.mcp.serverapi.hotspots.response.SearchResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class SearchSecurityHotspotsTool extends Tool {

  public static final String TOOL_NAME = "search_security_hotspots";

  public static final String PROJECT_KEY_PROPERTY = "projectKey";
  public static final String HOTSPOT_KEYS_PROPERTY = "hotspotKeys";
  public static final String BRANCH_PROPERTY = "branch";
  public static final String PULL_REQUEST_PROPERTY = "pullRequest";
  public static final String FILES_PROPERTY = "files";
  public static final String STATUS_PROPERTY = "status";
  public static final String RESOLUTION_PROPERTY = "resolution";
  public static final String SINCE_LEAK_PERIOD_PROPERTY = "sinceLeakPeriod";
  public static final String ONLY_MINE_PROPERTY = "onlyMine";
  public static final String PAGE_PROPERTY = "p";
  public static final String PAGE_SIZE_PROPERTY = "ps";
  
  private final ServerApiProvider serverApiProvider;

  public SearchSecurityHotspotsTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(SearchSecurityHotspotsToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Search Security Hotspots")
      .setDescription("Search for Security Hotspots in a SonarQube project.")
      .addStringProperty(PROJECT_KEY_PROPERTY, "The key of the project or application to search in. Required unless hotspotKeys is provided.")
      .addArrayProperty(HOTSPOT_KEYS_PROPERTY, "string", "Comma-separated list of specific Security Hotspot keys to retrieve. Required unless projectKey is provided.")
      .addStringProperty(BRANCH_PROPERTY, "The branch name to search for Security Hotspots in")
      .addStringProperty(PULL_REQUEST_PROPERTY, "The identifier of the Pull Request to search in")
      .addArrayProperty(FILES_PROPERTY, "string", "An optional list of file paths to filter Security Hotspots")
      .addStringProperty(STATUS_PROPERTY, "Filter by review status. Possible values: TO_REVIEW, REVIEWED")
      .addStringProperty(RESOLUTION_PROPERTY, "Filter by resolution (when status is REVIEWED). Possible values: FIXED, SAFE, ACKNOWLEDGED")
      .addBooleanProperty(SINCE_LEAK_PERIOD_PROPERTY, "If true, only Security Hotspots created since the leak period (new code period) are returned")
      .addBooleanProperty(ONLY_MINE_PROPERTY, "If true, only Security Hotspots assigned to the current user are returned")
      .addNumberProperty(PAGE_PROPERTY, "An optional page number. Defaults to 1.")
      .addNumberProperty(PAGE_SIZE_PROPERTY, "An optional page size. Must be greater than 0 and less than or equal to 500. Defaults to 100.")
      .setReadOnlyHint()
      .build(),
      ToolCategory.SECURITY_HOTSPOTS);
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var validationError = validateArguments(arguments);
    if (validationError != null) {
      return Tool.Result.failure(validationError);
    }
    
    var searchParams = extractSearchParams(arguments);
    var response = serverApiProvider.get().hotspotsApi().search(searchParams);
    var toolResponse = buildStructuredContent(response);
    return Tool.Result.success(toolResponse);
  }

  @CheckForNull
  private static String validateArguments(Tool.Arguments arguments) {
    var projectKey = arguments.getOptionalString(PROJECT_KEY_PROPERTY);
    var hotspotKeys = arguments.getOptionalStringList(HOTSPOT_KEYS_PROPERTY);
    
    boolean hasProjectKey = projectKey != null && !projectKey.isEmpty();
    boolean hasHotspotKeys = hotspotKeys != null && !hotspotKeys.isEmpty();
    
    if (!hasProjectKey && !hasHotspotKeys) {
      return "Either 'projectKey' or 'hotspotKeys' must be provided";
    }
    
    return null;
  }

  private static HotspotsApi.SearchParams extractSearchParams(Tool.Arguments arguments) {
    return new HotspotsApi.SearchParams(
      arguments.getOptionalString(PROJECT_KEY_PROPERTY),
      arguments.getOptionalString(BRANCH_PROPERTY),
      arguments.getOptionalString(PULL_REQUEST_PROPERTY),
      arguments.getOptionalStringList(FILES_PROPERTY),
      arguments.getOptionalStringList(HOTSPOT_KEYS_PROPERTY),
      arguments.getOptionalString(STATUS_PROPERTY),
      arguments.getOptionalString(RESOLUTION_PROPERTY),
      arguments.getOptionalBoolean(SINCE_LEAK_PERIOD_PROPERTY),
      arguments.getOptionalBoolean(ONLY_MINE_PROPERTY),
      arguments.getOptionalInteger(PAGE_PROPERTY),
      arguments.getOptionalInteger(PAGE_SIZE_PROPERTY)
    );
  }

  private static SearchSecurityHotspotsToolResponse buildStructuredContent(SearchResponse response) {
    var hotspots = response.hotspots().stream()
      .map(hotspot -> {
        SearchSecurityHotspotsToolResponse.TextRange textRange = null;
        if (hotspot.textRange() != null) {
          textRange = new SearchSecurityHotspotsToolResponse.TextRange(
            hotspot.textRange().startLine(),
            hotspot.textRange().endLine(),
            hotspot.textRange().startOffset(),
            hotspot.textRange().endOffset()
          );
        }
        return new SearchSecurityHotspotsToolResponse.Hotspot(
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
          hotspot.ruleKey()
        );
      })
      .toList();

    var paging = response.paging();
    var pagingResponse = new SearchSecurityHotspotsToolResponse.Paging(
      paging.pageIndex(),
      paging.pageSize(),
      paging.total()
    );

    return new SearchSecurityHotspotsToolResponse(hotspots, pagingResponse);
  }

}
