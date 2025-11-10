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

import java.util.List;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.system.response.HealthResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public class SystemHealthTool extends Tool {

  public static final String TOOL_NAME = "get_system_health";

  private final ServerApiProvider serverApiProvider;

  public SystemHealthTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(SystemHealthToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Get SonarQube System Health")
      .setDescription("Get the health status of SonarQube Server instance. Returns GREEN (fully operational), YELLOW (usable but needs attention), or RED (not operational).")
      .build());
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var response = serverApiProvider.get().systemApi().getHealth();
    var toolResponse = buildStructuredContent(response);
    return Tool.Result.success(toolResponse);
  }

  private static SystemHealthToolResponse buildStructuredContent(HealthResponse response) {
    List<SystemHealthToolResponse.Cause> causes = null;
    if (response.causes() != null && !response.causes().isEmpty()) {
      causes = response.causes().stream()
        .map(c -> new SystemHealthToolResponse.Cause(c.message()))
        .toList();
    }
    
    List<SystemHealthToolResponse.Node> nodes = null;
    if (response.nodes() != null && !response.nodes().isEmpty()) {
      nodes = response.nodes().stream()
        .map(node -> {
          List<SystemHealthToolResponse.Cause> nodeCauses = null;
          if (node.causes() != null && !node.causes().isEmpty()) {
            nodeCauses = node.causes().stream()
              .map(c -> new SystemHealthToolResponse.Cause(c.message()))
              .toList();
          }
          return new SystemHealthToolResponse.Node(
            node.name(), node.type(), node.health(), node.host(), 
            node.port(), node.startedAt(), nodeCauses
          );
        })
        .toList();
    }

    return new SystemHealthToolResponse(response.health(), causes, nodes);
  }

}
