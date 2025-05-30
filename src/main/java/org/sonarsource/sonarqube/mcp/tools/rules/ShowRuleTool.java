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
package org.sonarsource.sonarqube.mcp.tools.rules;

import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.rules.response.ShowResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public class ShowRuleTool extends Tool {

  public static final String TOOL_NAME = "show_rule";
  public static final String KEY_PROPERTY = "key";

  private final ServerApi serverApi;

  public ShowRuleTool(ServerApi serverApi) {
    super(new SchemaToolBuilder()
      .setName(TOOL_NAME)
      .setDescription("Shows detailed information about a SonarQube rule")
      .addRequiredStringProperty(KEY_PROPERTY, "The rule key (e.g. javascript:EmptyBlock)")
      .build());
    this.serverApi = serverApi;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    if (!serverApi.isAuthenticationSet()) {
      return Tool.Result.failure("Not connected to SonarQube, please provide valid credentials");
    }

    var ruleKey = arguments.getStringOrThrow(KEY_PROPERTY);
    var response = serverApi.rulesApi().showRule(ruleKey);
    return Tool.Result.success(buildResponseFromShowResponse(response.rule()));
  }

  private static String buildResponseFromShowResponse(ShowResponse.Rule rule) {
    var responseBuilder = new StringBuilder();
    responseBuilder.append("Rule details:\n");
    responseBuilder.append("Key: ").append(rule.key()).append("\n");
    responseBuilder.append("Name: ").append(rule.name()).append("\n");
    responseBuilder.append("Severity: ").append(rule.severity()).append("\n");
    responseBuilder.append("Type: ").append(rule.type()).append("\n");
    responseBuilder.append("Language: ").append(rule.langName()).append(" (").append(rule.lang()).append(")\n");
    if (rule.impacts() != null && !rule.impacts().isEmpty()) {
      responseBuilder.append("Impacts:\n");
      rule.impacts().forEach(impact ->
        responseBuilder.append("- ").append(impact.softwareQuality()).append(": ").append(impact.severity()).append("\n")
      );
    }
    responseBuilder.append("\nDescription:\n").append(rule.htmlDesc());
    return responseBuilder.toString();
  }

}
