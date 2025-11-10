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
package org.sonarsource.sonarqube.mcp.tools.portfolios;

import jakarta.annotation.Nullable;
import io.modelcontextprotocol.spec.McpSchema;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.enterprises.response.PortfoliosResponse;
import org.sonarsource.sonarqube.mcp.serverapi.views.response.SearchResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public class ListPortfoliosTool extends Tool {

  public static final String TOOL_NAME = "list_portfolios";
  public static final String ENTERPRISE_ID_PROPERTY = "enterpriseId";
  public static final String QUERY_PROPERTY = "q";
  public static final String FAVORITE_PROPERTY = "favorite";
  public static final String DRAFT_PROPERTY = "draft";
  public static final String PAGE_INDEX_PROPERTY = "pageIndex";
  public static final String PAGE_SIZE_PROPERTY = "pageSize";

  private final ServerApiProvider serverApiProvider;
  private final boolean isSonarCloud;

  public ListPortfoliosTool(ServerApiProvider serverApiProvider, boolean isSonarCloud) {
    super(createToolDefinition(isSonarCloud));
    this.serverApiProvider = serverApiProvider;
    this.isSonarCloud = isSonarCloud;
  }

  private static McpSchema.Tool createToolDefinition(boolean isSonarCloud) {
    var builder = SchemaToolBuilder.forOutput(ListPortfoliosToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("List SonarQube Portfolios");
      
    if (isSonarCloud) {
      builder.setDescription("List enterprise portfolios available in SonarQube Cloud with filtering and pagination options.")
        .addStringProperty(ENTERPRISE_ID_PROPERTY, "Enterprise uuid. Can be omitted only if 'favorite' parameter is supplied with value true")
        .addStringProperty(QUERY_PROPERTY, "Search query to filter portfolios by name")
        .addBooleanProperty(FAVORITE_PROPERTY, "Required to be true if 'enterpriseId' parameter is omitted. " +
          "If true, only returns portfolios favorited by the logged-in user. Cannot be true when 'draft' is true")
        .addBooleanProperty(DRAFT_PROPERTY, "If true, only returns drafts created by the logged-in user. Cannot be true when 'favorite' is true")
        .addNumberProperty(PAGE_INDEX_PROPERTY, "Index of the page to fetch (default: 1)")
        .addNumberProperty(PAGE_SIZE_PROPERTY, "Size of the page to fetch (default: 50)");
    } else {
      builder.setDescription("List portfolios available in SonarQube Server with filtering and pagination options.")
        .addStringProperty(QUERY_PROPERTY, "Search query to filter portfolios by name or key")
        .addBooleanProperty(FAVORITE_PROPERTY, "If true, only returns favorite portfolios")
        .addNumberProperty(PAGE_INDEX_PROPERTY, "1-based page number (default: 1)")
        .addNumberProperty(PAGE_SIZE_PROPERTY, "Page size, max 500 (default: 100)");
    }
    
    return builder.build();
  }

  @Override
  public Result execute(Arguments arguments) {
    try {
      if (isSonarCloud) {
        return executeForCloud(arguments);
      } else {
        return executeForServer(arguments);
      }
    } catch (Exception e) {
      return Result.failure("An error occurred during the tool execution: " + e.getMessage());
    }
  }

  private Result executeForCloud(Arguments arguments) {
    var enterpriseId = arguments.getOptionalString(ENTERPRISE_ID_PROPERTY);
    var query = arguments.getOptionalString(QUERY_PROPERTY);
    var favorite = arguments.getOptionalBoolean(FAVORITE_PROPERTY);
    var draft = arguments.getOptionalBoolean(DRAFT_PROPERTY);
    var pageIndex = arguments.getOptionalInteger(PAGE_INDEX_PROPERTY);
    var pageSize = arguments.getOptionalInteger(PAGE_SIZE_PROPERTY);

    // Validate SonarQube Cloud parameter constraints
    var validationError = validateCloudParameters(enterpriseId, favorite, draft);
    if (validationError != null) {
      return Result.failure(validationError);
    }

    var apiResponse = serverApiProvider.get().enterprisesApi().listPortfolios(enterpriseId, query, favorite, draft, pageIndex, pageSize);
    var response = buildCloudResponse(apiResponse);
    return Result.success(response);
  }

  private Result executeForServer(Arguments arguments) {
    var query = arguments.getOptionalString(QUERY_PROPERTY);
    var favorite = arguments.getOptionalBoolean(FAVORITE_PROPERTY);
    var pageIndex = arguments.getOptionalInteger(PAGE_INDEX_PROPERTY);
    var pageSize = arguments.getOptionalInteger(PAGE_SIZE_PROPERTY);

    var apiResponse = serverApiProvider.get().viewsApi().search(query, favorite, pageIndex, pageSize);
    var response = buildServerResponse(apiResponse);
    return Result.success(response);
  }

  @Nullable
  private static String validateCloudParameters(@Nullable String enterpriseId, @Nullable Boolean favorite, @Nullable Boolean draft) {
    // Rule 1: Either enterpriseId must be provided OR favorite must be true
    if ((enterpriseId == null || enterpriseId.trim().isEmpty()) && (favorite == null || !favorite)) {
      return "Either 'enterpriseId' must be provided or 'favorite' must be true";
    }

    // Rule 2: favorite and draft cannot both be true
    if (Boolean.TRUE.equals(favorite) && Boolean.TRUE.equals(draft)) {
      return "Parameters 'favorite' and 'draft' cannot both be true at the same time";
    }

    return null;
  }

  private static ListPortfoliosToolResponse buildCloudResponse(PortfoliosResponse response) {
    var portfolios = response.portfolios().stream()
      .map(portfolio -> (ListPortfoliosToolResponse.Portfolio) new ListPortfoliosToolResponse.CloudPortfolio(
        portfolio.id(),
        portfolio.name(),
        portfolio.description(),
        portfolio.enterpriseId(),
        portfolio.selection(),
        portfolio.isDraft(),
        portfolio.draftStage(),
        portfolio.tags()
      ))
      .toList();

    ListPortfoliosToolResponse.Paging paging = null;
    if (response.page() != null) {
      var page = response.page();
      paging = new ListPortfoliosToolResponse.Paging(page.pageIndex(), page.pageSize(), page.total());
    }

    return new ListPortfoliosToolResponse(portfolios, paging);
  }

  private static ListPortfoliosToolResponse buildServerResponse(SearchResponse response) {
    var portfolios = response.components().stream()
      .map(component -> (ListPortfoliosToolResponse.Portfolio) new ListPortfoliosToolResponse.ServerPortfolio(
        component.key(),
        component.name(),
        component.qualifier(),
        component.visibility(),
        component.isFavorite()
      ))
      .toList();

    ListPortfoliosToolResponse.Paging paging = null;
    if (response.paging() != null) {
      var p = response.paging();
      paging = new ListPortfoliosToolResponse.Paging(p.pageIndex(), p.pageSize(), p.total());
    }

    return new ListPortfoliosToolResponse(portfolios, paging);
  }

}
