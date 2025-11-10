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
package org.sonarsource.sonarqube.mcp.serverapi.enterprises.response;

import java.util.List;
import javax.annotation.Nullable;

public record PortfoliosResponse(List<Portfolio> portfolios, @Nullable Page page) {

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
