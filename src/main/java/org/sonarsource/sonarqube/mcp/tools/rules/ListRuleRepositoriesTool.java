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

import java.util.List;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.rules.response.RepositoriesResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public class ListRuleRepositoriesTool extends Tool {

  public static final String TOOL_NAME = "list_rule_repositories";
  public static final String LANGUAGE_PROPERTY = "language";
  public static final String QUERY_PROPERTY = "q";

  private final ServerApiProvider serverApiProvider;

  public ListRuleRepositoriesTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(ListRuleRepositoriesToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("List SonarQube Rule Repositories")
      .setDescription("List rule repositories available in SonarQube.")
      .addStringProperty(LANGUAGE_PROPERTY, "Optional language key to filter repositories (e.g. 'java')")
      .addStringProperty(QUERY_PROPERTY, "Optional search query to filter repositories by name or key")
      .build());
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var language = arguments.getOptionalString(LANGUAGE_PROPERTY);
    var query = arguments.getOptionalString(QUERY_PROPERTY);
    var response = serverApiProvider.get().rulesApi().getRepositories(language, query);
    var toolResponse = buildStructuredContent(response.repositories());
    return Tool.Result.success(toolResponse);
  }

  private static ListRuleRepositoriesToolResponse buildStructuredContent(List<RepositoriesResponse.Repository> repositories) {
    var repoList = repositories.stream()
      .map(repo -> new ListRuleRepositoriesToolResponse.Repository(repo.key(), repo.name(), repo.language()))
      .toList();

    return new ListRuleRepositoriesToolResponse(repoList);
  }

}
