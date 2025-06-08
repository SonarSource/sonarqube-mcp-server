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
import org.sonarsource.sonarqube.mcp.serverapi.system.response.HealthResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public class SystemHealthTool extends Tool {

  public static final String TOOL_NAME = "get_system_health";

  private final ServerApi serverApi;

  public SystemHealthTool(ServerApi serverApi) {
    super(new SchemaToolBuilder()
      .setName(TOOL_NAME)
      .setDescription("Get the health status of SonarQube Server instance. Returns GREEN (fully operational), YELLOW (usable but needs attention), or RED (not operational).")
      .build());
    this.serverApi = serverApi;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    if (!serverApi.isAuthenticationSet()) {
      return Tool.Result.failure("Not connected to SonarQube Server, please provide valid credentials");
    }

    var response = serverApi.systemApi().getHealth();
    return Tool.Result.success(buildResponseFromHealth(response));
  }

  private static String buildResponseFromHealth(HealthResponse response) {
    var stringBuilder = new StringBuilder();
    stringBuilder.append("SonarQube Server Health Status: ").append(response.health()).append("\n");

    if (response.causes() != null && !response.causes().isEmpty()) {
      stringBuilder.append("\nCauses:\n");
      for (var cause : response.causes()) {
        stringBuilder.append("- ").append(cause.message()).append("\n");
      }
    }

    if (response.nodes() != null && !response.nodes().isEmpty()) {
      stringBuilder.append("\nNodes:\n");
      for (var node : response.nodes()) {
        stringBuilder.append("\n").append(node.name())
          .append(" (").append(node.type()).append(")")
          .append(" - ").append(node.health())
          .append("\n");
        stringBuilder.append("  Host: ").append(node.host())
          .append(":").append(node.port()).append("\n");
        stringBuilder.append("  Started: ").append(node.startedAt()).append("\n");

        if (node.causes() != null && !node.causes().isEmpty()) {
          stringBuilder.append("  Causes:\n");
          for (var cause : node.causes()) {
            stringBuilder.append("  - ").append(cause.message()).append("\n");
          }
        }
      }
    }

    return stringBuilder.toString().trim();
  }
}
