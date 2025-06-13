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
package org.sonarsource.sonarqube.mcp.tools.system;

import java.util.Locale;
import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public class SystemLogsTool extends Tool {

  public static final String TOOL_NAME = "get_system_logs";
  public static final String NAME_PROPERTY = "name";

  private final ServerApi serverApi;

  public SystemLogsTool(ServerApi serverApi) {
    super(new SchemaToolBuilder()
      .setName(TOOL_NAME)
      .setDescription("Get system logs in plain-text format. Requires system administration permission.")
      .addStringProperty(NAME_PROPERTY, "Name of the logs to get. Possible values: access, app, ce, deprecation, es, web. Default: app")
      .build());
    this.serverApi = serverApi;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var name = arguments.getOptionalString("name");

    if (name != null && !isValidLogName(name)) {
      return Tool.Result.failure("Invalid log name. Possible values: access, app, ce, deprecation, es, web");
    }

    var logs = serverApi.systemApi().getLogs(name);
    return Tool.Result.success(buildResponseFromLogs(logs, name));
  }

  private static boolean isValidLogName(String name) {
    return "access".equals(name) || "app".equals(name) || "ce".equals(name) || 
           "deprecation".equals(name) || "es".equals(name) || "web".equals(name);
  }

  private static String buildResponseFromLogs(@Nullable String logs, @Nullable String name) {
    var logType = name != null ? name : "app";
    var header = "SonarQube Server " + logType.toUpperCase(Locale.getDefault()) + " Logs\n" +
                 "=".repeat(("SonarQube Server " + logType.toUpperCase(Locale.getDefault()) + " Logs").length()) + "\n\n";
    
    if (logs == null || logs.trim().isEmpty()) {
      return header + "No logs available.";
    }
    
    return header + logs;
  }
}
