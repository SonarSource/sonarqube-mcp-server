/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
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

import io.modelcontextprotocol.spec.McpSchema;
import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
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

  private final ServerApi serverApi;

  public ListPortfoliosTool(ServerApi serverApi) {
    super(createToolDefinition(serverApi));
    this.serverApi = serverApi;
  }

  private static McpSchema.Tool createToolDefinition(ServerApi serverApi) {
    var builder = new SchemaToolBuilder()
      .setName(TOOL_NAME);
      
    if (serverApi.isSonarQubeCloud()) {
      builder.setDescription("List enterprise portfolios with filtering options.")
        .addStringProperty(ENTERPRISE_ID_PROPERTY, "Enterprise uuid. Can be omitted only if 'favorite' parameter is supplied with value true")
        .addStringProperty(QUERY_PROPERTY, "Search query to filter portfolios by name")
        .addBooleanProperty(FAVORITE_PROPERTY, "Required to be true if 'enterpriseId' parameter is omitted. " +
          "If true, only returns portfolios favorited by the logged-in user. Cannot be true when 'draft' is true")
        .addBooleanProperty(DRAFT_PROPERTY, "If true, only returns drafts created by the logged-in user. Cannot be true when 'favorite' is true")
        .addNumberProperty(PAGE_INDEX_PROPERTY, "Index of the page to fetch (default: 1)")
        .addNumberProperty(PAGE_SIZE_PROPERTY, "Size of the page to fetch (default: 50)");
    } else {
      builder.setDescription("List portfolios available in SonarQube Server with filtering options.")
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
      if (serverApi.isSonarQubeCloud()) {
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

    var response = serverApi.enterprisesApi().listPortfolios(enterpriseId, query, favorite, draft, pageIndex, pageSize);
    return Result.success(formatCloudResponse(response));
  }

  private Result executeForServer(Arguments arguments) {
    var query = arguments.getOptionalString(QUERY_PROPERTY);
    var favorite = arguments.getOptionalBoolean(FAVORITE_PROPERTY);
    var pageIndex = arguments.getOptionalInteger(PAGE_INDEX_PROPERTY);
    var pageSize = arguments.getOptionalInteger(PAGE_SIZE_PROPERTY);

    var response = serverApi.viewsApi().search(query, favorite, pageIndex, pageSize);
    return Result.success(formatServerResponse(response));
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

  private static String formatServerResponse(SearchResponse response) {
    if (response.components() == null || response.components().isEmpty()) {
      return "No portfolios were found.";
    }

    var builder = new StringBuilder("Available Portfolios:\n\n");

    for (var component : response.components()) {
      builder.append("Portfolio: ").append(component.name())
        .append(" (").append(component.key()).append(")")
        .append(" | Qualifier: ").append(component.qualifier())
        .append(" | Visibility: ").append(component.visibility());

      if (component.isFavorite() != null) {
        builder.append(" | Favorite: ").append(component.isFavorite());
      }

      builder.append("\n");
    }

    // Add paging information for SonarQube Server
    if (response.paging() != null) {
      builder.append("\nThis response is paginated and this is the page ")
        .append(response.paging().pageIndex()).append(" out of ")
        .append((int) Math.ceil((double) response.paging().total() / response.paging().pageSize()))
        .append(" total pages. There is a maximum of ")
        .append(response.paging().pageSize()).append(" portfolios per page.");
    }

    return builder.toString().trim();
  }

  private static String formatCloudResponse(PortfoliosResponse response) {
    if (response.portfolios() == null || response.portfolios().isEmpty()) {
      return "No portfolios were found.";
    }

    var builder = new StringBuilder("Available Portfolios:\n\n");

    for (var portfolio : response.portfolios()) {
      formatPortfolioDetails(builder, portfolio);
    }

    addPaginationInfo(builder, response.page());

    return builder.toString().trim();
  }

  private static void formatPortfolioDetails(StringBuilder builder, PortfoliosResponse.Portfolio portfolio) {
    builder.append("Portfolio: ").append(portfolio.name())
      .append(" (").append(portfolio.id()).append(")");

    addOptionalField(builder, portfolio.description(), " | Description: ");
    addOptionalField(builder, portfolio.enterpriseId(), " | Enterprise: ");
    addOptionalField(builder, portfolio.selection(), " | Selection: ");

    if (portfolio.isDraft() != null && portfolio.isDraft()) {
      builder.append(" | Draft (Stage: ").append(portfolio.draftStage()).append(")");
    }

    if (portfolio.tags() != null && !portfolio.tags().isEmpty()) {
      builder.append(" | Tags: ").append(String.join(", ", portfolio.tags()));
    }

    builder.append("\n");
  }

  private static void addOptionalField(StringBuilder builder, @Nullable String value, String prefix) {
    if (value != null) {
      builder.append(prefix).append(value);
    }
  }

  private static void addPaginationInfo(StringBuilder builder, @Nullable PortfoliosResponse.Page page) {
    if (page != null) {
      builder.append("\nThis response is paginated and this is the page ")
        .append(page.pageIndex()).append(" out of ")
        .append((int) Math.ceil((double) page.total() / page.pageSize()))
        .append(" total pages. There is a maximum of ")
        .append(page.pageSize()).append(" portfolios per page.");
    }
  }

}
