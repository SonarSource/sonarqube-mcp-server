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
package org.sonarsource.sonarqube.mcp.tools.projects;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.sonarsource.sonarqube.mcp.tools.Description;

/**
 * Response object for SearchMyProjectsTool with structured output.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchMyProjectsToolResponse(
  @Description("List of projects found in the organization") List<Project> projects,
  @Description("Pagination information for the results") Paging paging
) {
  
  public record Project(
    @Description("Unique project key") String key,
    @Description("Project display name") String name
  ) {}
  
  public record Paging(
    @Description("Current page index (1-based)") int pageIndex,
    @Description("Number of items per page") int pageSize,
    @Description("Total number of items across all pages") int total
  ) {}
}

