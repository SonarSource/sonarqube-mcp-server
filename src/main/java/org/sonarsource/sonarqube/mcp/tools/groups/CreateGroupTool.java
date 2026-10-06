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

public final class CreateGroupTool extends Tool {
  public static final String TOOL_NAME = "create_group";
  public static final String ORGANIZATION_ID_PROPERTY = "organizationId";
  public static final String NAME_PROPERTY = "name";
  public static final String DESCRIPTION_PROPERTY = "description";
  private final ServerApiProvider serverApiProvider;

  public CreateGroupTool(ServerApiProvider serverApiProvider) {
    super(ToolDefinitionBuilder.builder().setName(TOOL_NAME).setTitle("Create SonarQube Cloud Group")
      .setDescription("Create an empty group in a SonarQube Cloud organization. Requires organization or enterprise administrator permission.")
      .addRequiredStringProperty(ORGANIZATION_ID_PROPERTY, "Organization UUID.")
      .addRequiredStringProperty(NAME_PROPERTY, "Group name, 1 to 255 characters.")
      .addStringProperty(DESCRIPTION_PROPERTY, "Optional description, up to 200 characters.")
      .build(), ToolCategory.SYSTEM);
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Result execute(Arguments arguments) {
    var organizationId = UUID.fromString(arguments.getStringOrThrow(ORGANIZATION_ID_PROPERTY));
    var name = arguments.getStringOrThrow(NAME_PROPERTY);
    var description = arguments.getOptionalString(DESCRIPTION_PROPERTY);
    if (name.isBlank() || name.length() > 255 || description != null && description.length() > 200) {
      return Result.failure("Group name must contain 1 to 255 characters and description must not exceed 200 characters");
    }
    var group = serverApiProvider.get().groupsApi().create(organizationId, name, description);
    return Result.success(new CreateGroupToolResponse(group));
  }
}

