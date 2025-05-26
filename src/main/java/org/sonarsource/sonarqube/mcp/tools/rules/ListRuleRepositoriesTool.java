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
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.rules.response.RepositoriesResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public class ListRuleRepositoriesTool extends Tool {

  public static final String TOOL_NAME = "list_rule_repositories";
  public static final String LANGUAGE_PROPERTY = "language";
  public static final String QUERY_PROPERTY = "q";

  private final ServerApi serverApi;

  public ListRuleRepositoriesTool(ServerApi serverApi) {
    super(new SchemaToolBuilder()
      .setName(TOOL_NAME)
      .setDescription("List rule repositories available in SonarQube")
      .addStringProperty(LANGUAGE_PROPERTY, "Optional language key to filter repositories (e.g. 'java')")
      .addStringProperty(QUERY_PROPERTY, "Optional search query to filter repositories by name or key")
      .build());
    this.serverApi = serverApi;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    if (!serverApi.isAuthenticationSet()) {
      return Tool.Result.failure("Not connected to SonarQube Cloud, please provide 'SONARQUBE_CLOUD_TOKEN' and 'SONARQUBE_CLOUD_ORG'");
    }
    var language = arguments.getOptionalString(LANGUAGE_PROPERTY);
    var query = arguments.getOptionalString(QUERY_PROPERTY);
    var response = serverApi.rulesApi().getRepositories(language, query);
    return Tool.Result.success(buildResponseFromRepositories(response.repositories()));
  }

  private static String buildResponseFromRepositories(List<RepositoriesResponse.Repository> repositories) {
    if (repositories.isEmpty()) {
      return "No rule repositories found.";
    }

    var responseBuilder = new StringBuilder();
    responseBuilder.append("Found ").append(repositories.size()).append(" rule repositories:\n\n");

    repositories.forEach(repo -> {
      responseBuilder.append("Key: ").append(repo.key()).append("\n");
      responseBuilder.append("Name: ").append(repo.name()).append("\n");
      responseBuilder.append("Language: ").append(repo.language()).append("\n\n");
    });

    return responseBuilder.toString().trim();
  }

}
