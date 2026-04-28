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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.annotation.Nullable;
import java.util.List;

/**
 * Response object for ListOrganizationsTool with structured output.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ListOrganizationsToolResponse(
  @JsonPropertyDescription("List of organizations the authenticated user is a member of") List<Organization> organizations,
  @JsonPropertyDescription("Pagination information for the results") Paging paging
) {

  public record Organization(
    @JsonPropertyDescription("Unique organization key") String key,
    @JsonPropertyDescription("Organization display name") String name,
    @JsonPropertyDescription("Optional organization description") @Nullable String description,
    @JsonPropertyDescription("Optional organization URL") @Nullable String url
  ) {
  }

  public record Paging(
    @JsonPropertyDescription("Current page index (1-based)") int pageIndex,
    @JsonPropertyDescription("Number of items per page") int pageSize,
    @JsonPropertyDescription("Total number of items across all pages") int total,
    @JsonPropertyDescription("Whether there are more pages available") boolean hasNextPage
  ) {
  }
}
