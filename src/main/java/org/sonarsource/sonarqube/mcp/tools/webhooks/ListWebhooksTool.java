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
package org.sonarsource.sonarqube.mcp.tools.webhooks;

import java.util.List;
import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.webhooks.response.ListResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public class ListWebhooksTool extends Tool {

  public static final String TOOL_NAME = "list_webhooks";
  public static final String PROJECT_PROPERTY = "projectKey";

  private final ServerApi serverApi;

  public ListWebhooksTool(ServerApi serverApi) {
    super(new SchemaToolBuilder()
      .setName(TOOL_NAME)
      .setDescription("List all webhooks for the organization or project. Requires 'Administer' permission on the specified project, or global 'Administer' permission.")
      .addStringProperty(PROJECT_PROPERTY, "Optional project key to list project-specific webhooks")
      .build());
    this.serverApi = serverApi;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var project = arguments.getOptionalString(PROJECT_PROPERTY);
    var response = serverApi.webhooksApi().listWebhooks(project);
    return Tool.Result.success(buildResponseFromList(response.webhooks(), project));
  }

  private static String buildResponseFromList(List<ListResponse.Webhook> webhooks, @Nullable String project) {
    if (webhooks.isEmpty()) {
      return project != null 
        ? String.format("No webhooks found for project '%s'.", project)
        : "No webhooks found.";
    }

    var stringBuilder = new StringBuilder();
    if (project != null) {
      stringBuilder.append(String.format("Found %d webhook(s) for project '%s':%n%n", webhooks.size(), project));
    } else {
      stringBuilder.append(String.format("Found %d webhook(s):%n%n", webhooks.size()));
    }

    for (var webhook : webhooks) {
      stringBuilder.append("Key: ").append(webhook.key()).append("\n");
      stringBuilder.append("Name: ").append(webhook.name()).append("\n");
      stringBuilder.append("URL: ").append(webhook.url()).append("\n");
      stringBuilder.append("Has Secret: ").append(webhook.hasSecret() ? "Yes" : "No").append("\n\n");
    }

    return stringBuilder.toString().trim();
  }

}
