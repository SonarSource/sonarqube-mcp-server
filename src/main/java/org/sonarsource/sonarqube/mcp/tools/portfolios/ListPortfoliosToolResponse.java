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
package org.sonarsource.sonarqube.mcp.tools.portfolios;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import jakarta.annotation.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ListPortfoliosToolResponse(
  List<Portfolio> portfolios,
  @Nullable Paging paging
) {
  
  /**
   * Portfolio for SonarCloud
   */
  public record CloudPortfolio(
    String id,
    String name,
    @Nullable String description,
    @Nullable String enterpriseId,
    @Nullable String selection,
    @Nullable Boolean isDraft,
    @Nullable Integer draftStage,
    @Nullable List<String> tags
  ) implements Portfolio {}
  
  /**
   * Portfolio for SonarQube Server
   */
  public record ServerPortfolio(
    String key,
    String name,
    String qualifier,
    String visibility,
    @Nullable Boolean isFavorite
  ) implements Portfolio {}
  
  /**
   * Marker interface for portfolios (Cloud or Server)
   */
  public sealed interface Portfolio permits CloudPortfolio, ServerPortfolio {}
  
  public record Paging(
    int pageIndex,
    int pageSize,
    int total
  ) {}
}

