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
package org.sonarsource.sonarqube.mcp.tools.groups;

import java.util.UUID;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;
import org.sonarsource.sonarqube.mcp.tools.ToolDefinitionBuilder;
import org.sonarsource.sonarqube.mcp.tools.ToolParameters;

public final class ListGroupsTool extends Tool {
  public static final String TOOL_NAME = "list_groups";
  public static final String ORGANIZATION_ID_PROPERTY = "organizationId";
  public static final String NAME_PROPERTY = "name";
  private final ServerApiProvider serverApiProvider;

  public ListGroupsTool(ServerApiProvider serverApiProvider) {
    super(ToolDefinitionBuilder.builder().setName(TOOL_NAME).setTitle("List SonarQube Cloud Groups")
      .setDescription("List groups in a SonarQube Cloud organization. Requires organization or enterprise administrator permission.")
      .addRequiredStringProperty(ORGANIZATION_ID_PROPERTY, "Organization UUID.")
      .addStringProperty(NAME_PROPERTY, "Exact group name filter.")
      .addNumberProperty(ToolParameters.PAGE_INDEX, "Optional 1-based page index.")
      .addNumberProperty(ToolParameters.PAGE_SIZE, "Optional page size, 1 to 500.")
      .setReadOnlyHint().build(), ToolCategory.SYSTEM);
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Result execute(Arguments arguments) {
    var organizationId = UUID.fromString(arguments.getStringOrThrow(ORGANIZATION_ID_PROPERTY));
    var name = arguments.getOptionalString(NAME_PROPERTY);
    var pageIndex = arguments.getOptionalPageIndex();
    var pageSize = arguments.getOptionalPageSize();
    if (pageIndex != null && pageIndex < 1 || pageSize != null && (pageSize < 1 || pageSize > 500)) {
      return Result.failure("Page index must be positive and page size must be between 1 and 500");
    }
    var response = serverApiProvider.get().groupsApi().list(organizationId, name, pageIndex, pageSize);
    return Result.success(new ListGroupsToolResponse(response.groups(), response.page()));
  }
}

