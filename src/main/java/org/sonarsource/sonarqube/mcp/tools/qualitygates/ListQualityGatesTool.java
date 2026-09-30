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
package org.sonarsource.sonarqube.mcp.tools.qualitygates;

import io.modelcontextprotocol.spec.McpSchema;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.qualitygates.response.ListResponse;
import org.sonarsource.sonarqube.mcp.tools.ToolDefinitionBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class ListQualityGatesTool extends Tool {

  public static final String TOOL_NAME = "list_quality_gates";

  private final ServerApiProvider serverApiProvider;

  public ListQualityGatesTool(ServerApiProvider serverApiProvider) {
    super(buildToolDefinition(),
      ToolCategory.QUALITY_GATES);
    this.serverApiProvider = serverApiProvider;
  }

  private static McpSchema.Tool buildToolDefinition() {
    return ToolDefinitionBuilder.builder()
      .setName(TOOL_NAME)
      .setTitle("List SonarQube Quality Gates")
      .setDescription("List all quality gates.")
      .setReadOnlyHint()
      .build();
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var response = serverApiProvider.get().qualityGatesApi().list();
    var toolResponse = buildStructuredContent(response);
    return Tool.Result.success(toolResponse);
  }

  private static ListQualityGatesToolResponse buildStructuredContent(ListResponse response) {
    var qualityGates = response.qualitygates().stream()
      .map(gate -> {
        var conditions = (gate.conditions() != null)
          ? gate.conditions().stream()
              .map(c -> new ListQualityGatesToolResponse.Condition(c.metric(), c.op(), c.error()))
              .toList()
          : null;
        
        return new ListQualityGatesToolResponse.QualityGate(
          gate.id(),
          gate.name(),
          gate.isDefault(),
          gate.isBuiltIn(),
          conditions,
          gate.caycStatus(),
          gate.hasStandardConditions(),
          gate.hasMQRConditions(),
          gate.isAiCodeSupported()
        );
      })
      .toList();

    return new ListQualityGatesToolResponse(qualityGates);
  }

}
