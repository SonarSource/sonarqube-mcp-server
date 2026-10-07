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

import io.modelcontextprotocol.spec.McpSchema;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;
import org.sonarsource.sonarqube.mcp.tools.ToolDefinitionBuilder;

public class ListOrganizationsTool extends Tool {

  public static final String TOOL_NAME = "list_organizations";

  private final ServerApiProvider serverApiProvider;

  public ListOrganizationsTool(ServerApiProvider serverApiProvider) {
    super(buildToolDefinition(), ToolCategory.PROJECTS);
    this.serverApiProvider = serverApiProvider;
  }

  private static McpSchema.Tool buildToolDefinition() {
    return ToolDefinitionBuilder.builder()
      .setName(TOOL_NAME)
      .setTitle("List SonarQube Cloud Organizations")
      .setDescription("List the SonarQube Cloud organizations you are a member of.")
      .setReadOnlyHint()
      .build();
  }

  @Override
  public Result execute(Arguments arguments) {
    var organizations = serverApiProvider.get().organizationsApi().listOrganizations().stream()
      .map(o -> new ListOrganizationsToolResponse.Organization(o.key(), o.name()))
      .toList();
    return Result.success(new ListOrganizationsToolResponse(organizations));
  }

}
