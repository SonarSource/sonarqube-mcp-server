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
package org.sonarsource.sonarqube.mcp.tools.enterprises;

import io.modelcontextprotocol.spec.McpSchema;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.enterprises.response.ListResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public class ListEnterprisesTool extends Tool {

  public static final String TOOL_NAME = "list_enterprises";
  public static final String ENTERPRISE_KEY_PROPERTY = "enterpriseKey";

  private final ServerApi serverApi;

  public ListEnterprisesTool(ServerApi serverApi) {
    super(createToolDefinition());
    this.serverApi = serverApi;
  }

  private static McpSchema.Tool createToolDefinition() {
    return new SchemaToolBuilder()
      .setName(TOOL_NAME)
      .setDescription("List enterprises available in SonarQube Cloud. Available only for SonarQube Cloud instances.")
      .addStringProperty(ENTERPRISE_KEY_PROPERTY, "Optional enterprise key to filter results")
      .build();
  }

  @Override
  public Result execute(Arguments arguments) {
    try {
      var enterpriseKey = arguments.getOptionalString(ENTERPRISE_KEY_PROPERTY);

      var response = serverApi.enterprisesApi().listEnterprises(enterpriseKey);
      
      return Result.success(formatResponse(response));
    } catch (Exception e) {
      return Result.failure("An error occurred during the tool execution: " + e.getMessage());
    }
  }

  private static String formatResponse(ListResponse response) {
    if (response.enterprises().isEmpty()) {
      return "No enterprises were found.";
    }

    var builder = new StringBuilder("Available Enterprises:\n\n");
    
    for (var enterprise : response.enterprises()) {
      builder.append("Enterprise: ").append(enterprise.name())
        .append(" (").append(enterprise.key()).append(")")
        .append(" | ID: ").append(enterprise.id());
      
      if (enterprise.avatar() != null) {
        builder.append(" | Avatar: ").append(enterprise.avatar());
      }
      
      if (enterprise.defaultPortfolioPermissionTemplateId() != null) {
        builder.append(" | Default Portfolio Template: ").append(enterprise.defaultPortfolioPermissionTemplateId());
      }
      
      builder.append("\n");
    }
    
    return builder.toString().trim();
  }

}
