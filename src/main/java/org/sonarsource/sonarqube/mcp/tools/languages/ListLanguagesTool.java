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
package org.sonarsource.sonarqube.mcp.tools.languages;

import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.languages.response.ListResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public class ListLanguagesTool extends Tool {

  public static final String TOOL_NAME = "list_languages";
  public static final String QUERY_PROPERTY = "q";

  private final ServerApi serverApi;

  public ListLanguagesTool(ServerApi serverApi) {
    super(new SchemaToolBuilder()
      .setName(TOOL_NAME)
      .setDescription("List all programming languages supported in this instance")
      .addStringProperty(QUERY_PROPERTY, "Optional pattern to match language keys/names against")
      .build());
    this.serverApi = serverApi;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    if (!serverApi.isAuthenticationSet()) {
      return Tool.Result.failure("Not connected to SonarQube Cloud, please provide 'SONARQUBE_CLOUD_TOKEN' and 'SONARQUBE_CLOUD_ORG'");
    }

    var query = arguments.getOptionalString(QUERY_PROPERTY);
    var response = serverApi.languagesApi().list(query);
    return Tool.Result.success(buildResponseFromList(response));
  }

  private static String buildResponseFromList(ListResponse response) {
    var stringBuilder = new StringBuilder();
    stringBuilder.append("Supported Languages:\n\n");

    for (var language : response.languages()) {
      stringBuilder.append(language.name())
        .append(" (").append(language.key()).append(")")
        .append("\n");
    }

    return stringBuilder.toString().trim();
  }
} 
