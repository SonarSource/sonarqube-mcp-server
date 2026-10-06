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

public final class DeleteGroupTool extends Tool {
  public static final String TOOL_NAME = "delete_group";
  public static final String GROUP_ID_PROPERTY = "groupId";
  private final ServerApiProvider serverApiProvider;

  public DeleteGroupTool(ServerApiProvider serverApiProvider) {
    super(ToolDefinitionBuilder.builder().setName(TOOL_NAME).setTitle("Delete SonarQube Cloud Group")
      .setDescription("Delete an unmanaged custom group in SonarQube Cloud. Requires organization or enterprise administrator permission.")
      .addRequiredStringProperty(GROUP_ID_PROPERTY, "Group UUID.")
      .setDestructiveHint().setIdempotentHint().build(), ToolCategory.SYSTEM);
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Result execute(Arguments arguments) {
    var groupId = UUID.fromString(arguments.getStringOrThrow(GROUP_ID_PROPERTY));
    serverApiProvider.get().groupsApi().delete(groupId);
    return Result.success(new DeleteGroupToolResponse(groupId.toString(), true));
  }
}

