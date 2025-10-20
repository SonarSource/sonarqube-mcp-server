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

import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.system.response.StatusResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public class SystemStatusTool extends Tool {

  public static final String TOOL_NAME = "get_system_status";

  private final ServerApi serverApi;

  public SystemStatusTool(ServerApi serverApi) {
    super(new SchemaToolBuilder()
      .setName(TOOL_NAME)
      .setTitle("Get SonarQube System Status")
      .setDescription("Get state information about SonarQube Server. Returns status (STARTING, UP, DOWN, RESTARTING, DB_MIGRATION_NEEDED, DB_MIGRATION_RUNNING), version, and id.")
      .build());
    this.serverApi = serverApi;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var response = serverApi.systemApi().getStatus();
    return Tool.Result.success(buildResponseFromStatus(response));
  }

  private static String buildResponseFromStatus(StatusResponse response) {
    return """
      SonarQube Server System Status
      =======================

      Status: %s
      Description: %s

      ID: %s
      Version: %s""".formatted(response.status(), getStatusDescription(response.status()), response.id(), response.version());
  }

  private static String getStatusDescription(String status) {
    return switch (status) {
      case "STARTING" -> "SonarQube Server Web Server is up and serving some Web Services but initialization is still ongoing";
      case "UP" -> "SonarQube Server instance is up and running";
      case "DOWN" -> "SonarQube Server instance is up but not running because migration has failed or some other reason";
      case "RESTARTING" -> "SonarQube Server instance is still up but a restart has been requested";
      case "DB_MIGRATION_NEEDED" -> "Database migration is required";
      case "DB_MIGRATION_RUNNING" -> "DB migration is running";
      default -> "Unknown status";
    };
  }

}
