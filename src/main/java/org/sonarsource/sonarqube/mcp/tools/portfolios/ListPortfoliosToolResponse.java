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

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import jakarta.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.tools.Description;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ListPortfoliosToolResponse(
  @Description("List of portfolios") List<Portfolio> portfolios,
  @Description("Pagination information") @Nullable Paging paging
) {
  
  /**
   * Portfolio for SonarCloud
   */
  public record CloudPortfolio(
    @Description("Portfolio unique identifier") String id,
    @Description("Portfolio name") String name,
    @Description("Portfolio description") @Nullable String description,
    @Description("Enterprise unique identifier") @Nullable String enterpriseId,
    @Description("Selection mode (manual, automatic, etc.)") @Nullable String selection,
    @Description("Whether this is a draft portfolio") @Nullable Boolean isDraft,
    @Description("Draft stage if portfolio is a draft") @Nullable Integer draftStage,
    @Description("Portfolio tags") @Nullable List<String> tags
  ) implements Portfolio {}
  
  /**
   * Portfolio for SonarQube Server
   */
  public record ServerPortfolio(
    @Description("Portfolio key") String key,
    @Description("Portfolio name") String name,
    @Description("Component qualifier") String qualifier,
    @Description("Portfolio visibility") String visibility,
    @Description("Whether this portfolio is marked as favorite") @Nullable Boolean isFavorite
  ) implements Portfolio {}
  
  /**
   * Marker interface for portfolios (Cloud or Server)
   */
  public sealed interface Portfolio permits CloudPortfolio, ServerPortfolio {}
  
  public record Paging(
    @Description("Current page index (1-based)") int pageIndex,
    @Description("Number of items per page") int pageSize,
    @Description("Total number of items across all pages") int total
  ) {}
}

