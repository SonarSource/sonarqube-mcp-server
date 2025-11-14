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
package org.sonarsource.sonarqube.mcp.tools.languages;

import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.languages.response.ListResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public class ListLanguagesTool extends Tool {

  public static final String TOOL_NAME = "list_languages";
  public static final String QUERY_PROPERTY = "q";

  private final ServerApiProvider serverApiProvider;

  public ListLanguagesTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(ListLanguagesToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("List Supported Languages in SonarQube")
      .setDescription("List all programming languages supported in this SonarQube instance")
      .addStringProperty(QUERY_PROPERTY, "Optional pattern to match language keys/names against")
      .setReadOnlyHint()
      .build());
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var query = arguments.getOptionalString(QUERY_PROPERTY);
    var response = serverApiProvider.get().languagesApi().list(query);
    var toolResponse = buildStructuredContent(response);
    return Tool.Result.success(toolResponse);
  }

  private static ListLanguagesToolResponse buildStructuredContent(ListResponse response) {
    var languages = response.languages().stream()
      .map(language -> new ListLanguagesToolResponse.Language(language.key(), language.name()))
      .toList();

    return new ListLanguagesToolResponse(languages);
  }

} 
