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
package org.sonarsource.sonarqube.mcp.tools.qualitygates;

import java.util.Objects;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.qualitygates.response.ListResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public class ListQualityGatesTool extends Tool {

  public static final String TOOL_NAME = "list_quality_gates";

  private final ServerApi serverApi;

  public ListQualityGatesTool(ServerApi serverApi) {
    super(new SchemaToolBuilder()
      .setName(TOOL_NAME)
      .setDescription("List all quality gates in my SonarQube.")
      .build());
    this.serverApi = serverApi;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var response = serverApi.qualityGatesApi().list();
    return Tool.Result.success(buildResponseFromList(response));
  }

  private static String buildResponseFromList(ListResponse response) {
    var stringBuilder = new StringBuilder();
    stringBuilder.append("Quality Gates:\n");

    for (var gate : response.qualitygates()) {
      stringBuilder.append("\n").append(Objects.requireNonNullElse(gate.name(), "Unnamed"));
      if (gate.isDefault()) {
        stringBuilder.append(" [Default]");
      }
      if (gate.isBuiltIn()) {
        stringBuilder.append(" [Built-in]");
      }
      if (gate.id() != null) {
        stringBuilder.append(" (ID: ").append(gate.id()).append(")");
      }
      stringBuilder.append("\n");

      appendConditions(stringBuilder, gate);
      appendCloudFields(stringBuilder, gate);
    }

    return stringBuilder.toString().trim();
  }

  private static void appendConditions(StringBuilder sb, ListResponse.QualityGate gate) {
    if (gate.conditions() == null) {
      return;
    }
    if (!gate.conditions().isEmpty()) {
      sb.append("Conditions:\n");
      for (var condition : gate.conditions()) {
        sb.append("- ").append(condition.metric())
          .append(" ").append(condition.op())
          .append(" ").append(condition.error())
          .append("\n");
      }
    } else {
      sb.append("No conditions\n");
    }
  }

  private static void appendCloudFields(StringBuilder sb, ListResponse.QualityGate gate) {
    if (gate.caycStatus() != null) {
      sb.append("Status: ").append(gate.caycStatus()).append("\n");
    }
    if (gate.hasStandardConditions() != null) {
      sb.append("Standard Conditions: ").append(gate.hasStandardConditions()).append("\n");
    }
    if (gate.hasMQRConditions() != null) {
      sb.append("MQR Conditions: ").append(gate.hasMQRConditions()).append("\n");
    }
    if (gate.isAiCodeSupported() != null) {
      sb.append("AI Code Supported: ").append(gate.isAiCodeSupported()).append("\n");
    }
  }
}
