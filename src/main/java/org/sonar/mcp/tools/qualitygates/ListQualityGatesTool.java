/*
 * Sonar MCP Server
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
package org.sonar.mcp.tools.qualitygates;

import org.sonar.mcp.serverapi.ServerApi;
import org.sonar.mcp.serverapi.qualitygates.response.ListResponse;
import org.sonar.mcp.tools.SchemaToolBuilder;
import org.sonar.mcp.tools.Tool;

public class ListQualityGatesTool extends Tool {

  public static final String TOOL_NAME = "list_quality_gates";

  private final ServerApi serverApi;

  public ListQualityGatesTool(ServerApi serverApi) {
    super(new SchemaToolBuilder()
      .setName(TOOL_NAME)
      .setDescription("List all quality gates in the organization")
      .build());
    this.serverApi = serverApi;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    if (!serverApi.isAuthenticationSet()) {
      return Tool.Result.failure("Not connected to SonarQube Cloud, please provide 'SONARQUBE_CLOUD_TOKEN' and 'SONARQUBE_CLOUD_ORG'");
    }

    var response = serverApi.qualityGatesApi().list();
    return Tool.Result.success(buildResponseFromList(response));
  }

  private static String buildResponseFromList(ListResponse response) {
    var stringBuilder = new StringBuilder();
    stringBuilder.append("Quality Gates:\n");

    for (var gate : response.qualitygates()) {
      stringBuilder.append("\n").append(gate.name())
        .append(" (ID: ").append(gate.id()).append(")")
        .append(gate.isDefault() ? " [Default]" : "")
        .append(gate.isBuiltIn() ? " [Built-in]" : "")
        .append("\n");

      if (gate.conditions() != null && !gate.conditions().isEmpty()) {
        stringBuilder.append("Conditions:\n");
        for (var condition : gate.conditions()) {
          stringBuilder.append("- ").append(condition.metric())
            .append(" ").append(condition.op())
            .append(" ").append(condition.error())
            .append("\n");
        }
      } else {
        stringBuilder.append("No conditions\n");
      }
    }

    return stringBuilder.toString().trim();
  }
}
