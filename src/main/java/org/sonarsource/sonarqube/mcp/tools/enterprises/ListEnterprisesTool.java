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
package org.sonarsource.sonarqube.mcp.tools.enterprises;

import io.modelcontextprotocol.spec.McpSchema;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.enterprises.response.ListResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class ListEnterprisesTool extends Tool {

  public static final String TOOL_NAME = "list_enterprises";
  public static final String ENTERPRISE_KEY_PROPERTY = "enterpriseKey";

  private final ServerApiProvider serverApiProvider;

  public ListEnterprisesTool(ServerApiProvider serverApiProvider) {
    super(createToolDefinition(), ToolCategory.PORTFOLIOS);
    this.serverApiProvider = serverApiProvider;
  }

  private static McpSchema.Tool createToolDefinition() {
    return SchemaToolBuilder.forOutput(ListEnterprisesToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("List SonarQube Enterprises")
      .setDescription("List the enterprises available in SonarQube Cloud that you have access to. " +
        "Use this tool to discover enterprise IDs that can be used with other tools.")
      .addStringProperty(ENTERPRISE_KEY_PROPERTY, "Optional enterprise key to filter results")
      .setReadOnlyHint()
      .build();
  }

  @Override
  public Result execute(Arguments arguments) {
    try {
      var enterpriseKey = arguments.getOptionalString(ENTERPRISE_KEY_PROPERTY);

      var response = serverApiProvider.get().enterprisesApi().listEnterprises(enterpriseKey);
      var toolResponse = buildStructuredContent(response);
      
      return Result.success(toolResponse);
    } catch (Exception e) {
      return Result.failure("An error occurred during the tool execution: " + e.getMessage());
    }
  }

  private static ListEnterprisesToolResponse buildStructuredContent(ListResponse response) {
    var enterprises = response.enterprises().stream()
      .map(e -> new ListEnterprisesToolResponse.Enterprise(
        e.id(), e.key(), e.name(), e.avatar(), e.defaultPortfolioPermissionTemplateId()
      ))
      .toList();

    return new ListEnterprisesToolResponse(enterprises);
  }

}
