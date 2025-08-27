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
package org.sonarsource.sonarqube.mcp.serverapi.portfolios.response;

import java.util.List;
import javax.annotation.Nullable;

public record ListResponse(
  // SonarQube Server fields
  @Nullable List<Component> components,
  @Nullable Paging paging,
  // SonarQube Cloud fields  
  @Nullable List<Portfolio> portfolios,
  @Nullable Page page) {

  // SonarQube Server portfolio representation (components from /api/views/search)
  public record Component(String key, String name, String qualifier, String visibility, @Nullable Boolean isFavorite) {
  }

  // SonarQube Server paging object
  public record Paging(Integer pageIndex, Integer pageSize, Integer total) {
  }

  // SonarQube Cloud portfolio representation
  public record Portfolio(
    String id,
    @Nullable String enterpriseId,
    String name,
    @Nullable String description,
    @Nullable String selection,
    @Nullable String favoriteId,
    @Nullable List<String> tags,
    @Nullable List<Project> projects,
    @Nullable Boolean isDraft,
    @Nullable Integer draftStage
  ) {
  }

  public record Project(String branchId, String id) {
  }

  public record Page(Integer pageIndex, Integer pageSize, Integer total) {
  }

}
