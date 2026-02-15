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
package org.sonarsource.sonarqube.mcp.tools.system;

import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class SystemLogsTool extends Tool {

  public static final String TOOL_NAME = "get_system_logs";
  public static final String NAME_PROPERTY = "name";

  private final ServerApiProvider serverApiProvider;

  public SystemLogsTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(SystemLogsToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Get SonarQube System Logs")
      .setDescription("Get SonarQube Server system logs in plain-text format. Requires system administration permission.")
      .addEnumProperty(NAME_PROPERTY, new String[] {"access", "app", "ce", "deprecation", "es", "web"}, 
        "Name of the logs to get. Default: app")
      .setReadOnlyHint()
      .build(),
      ToolCategory.SYSTEM);
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var name = arguments.getOptionalStringList("name");
    var logName = (name != null && !name.isEmpty()) ? name.getFirst() : null;

    var logs = serverApiProvider.get().systemApi().getLogs(logName);
    var logType = logName != null ? logName : "app";
    var content = logs != null && !logs.trim().isEmpty() ? logs : "No logs available.";
    var response = new SystemLogsToolResponse(logType, content);
    return Tool.Result.success(response);
  }

}
