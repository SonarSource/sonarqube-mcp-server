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
package org.sonarsource.sonarqube.mcp.tools.organizations;

import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.response.SearchMemberOrganizationsResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class ListOrganizationsTool extends Tool {

  public static final String TOOL_NAME = "list_sonarqube_organizations";
  public static final String PAGE_PROPERTY = "page";
  public static final String PAGE_SIZE_PROPERTY = "pageSize";
  public static final int DEFAULT_PAGE_SIZE = 100;
  public static final int MAX_PAGE_SIZE = 500;

  private final ServerApiProvider serverApiProvider;

  public ListOrganizationsTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(ListOrganizationsToolResponse.class)
        .setName(TOOL_NAME)
        .setTitle("List SonarQube Cloud Organizations")
        .setDescription("List SonarQube Cloud organizations the authenticated user is a member of. " +
          "Use this when SONARQUBE_ORG is not configured to discover available organization keys, then pass the chosen key " +
          "as the 'organization' parameter on subsequent tool calls.")
        .addNumberProperty(PAGE_PROPERTY, "An optional page number. Defaults to 1.")
        .addNumberProperty(PAGE_SIZE_PROPERTY, "An optional page size. Must be greater than 0 and less than or equal to 500. Defaults to 100.")
        .setReadOnlyHint()
        .build(),
      ToolCategory.PROJECTS);
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Result execute(Arguments arguments) {
    var page = arguments.getIntOrDefault(PAGE_PROPERTY, 1);
    var pageSize = arguments.getIntOrDefault(PAGE_SIZE_PROPERTY, DEFAULT_PAGE_SIZE);

    if (pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
      return Result.failure("Page size must be greater than 0 and less than or equal to " + MAX_PAGE_SIZE);
    }

    var response = serverApiProvider.get().organizationsApi().searchMemberOrganizations(page, pageSize);
    return Result.success(buildStructuredContent(response));
  }

  private static ListOrganizationsToolResponse buildStructuredContent(SearchMemberOrganizationsResponse response) {
    var organizations = response.organizations().stream()
      .map(o -> new ListOrganizationsToolResponse.Organization(o.key(), o.name(), o.description(), o.url()))
      .toList();

    var paging = response.paging();
    var hasNextPage = ((long) paging.pageIndex() * paging.pageSize()) < paging.total();
    var pagingResponse = new ListOrganizationsToolResponse.Paging(
      paging.pageIndex(),
      paging.pageSize(),
      paging.total(),
      hasNextPage);

    return new ListOrganizationsToolResponse(organizations, pagingResponse);
  }
}
