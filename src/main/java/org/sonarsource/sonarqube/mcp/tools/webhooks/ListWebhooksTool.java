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
package org.sonarsource.sonarqube.mcp.tools.webhooks;

import java.util.List;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.webhooks.response.ListResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class ListWebhooksTool extends Tool {

  public static final String TOOL_NAME = "list_webhooks";
  public static final String PROJECT_PROPERTY = "projectKey";

  private final ServerApiProvider serverApiProvider;

  public ListWebhooksTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(ListWebhooksToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("List SonarQube Webhooks")
      .setDescription("List all webhooks for the SonarQube organization or project. Requires 'Administer' permission on the specified project, or global 'Administer' permission.")
      .addStringProperty(PROJECT_PROPERTY, "Optional project key to list project-specific webhooks")
      .setReadOnlyHint()
      .build(),
      ToolCategory.WEBHOOKS);
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var project = arguments.getOptionalString(PROJECT_PROPERTY);
    var response = serverApiProvider.get().webhooksApi().listWebhooks(project);
    var toolResponse = buildStructuredContent(response.webhooks());
    return Tool.Result.success(toolResponse);
  }

  private static ListWebhooksToolResponse buildStructuredContent(List<ListResponse.Webhook> webhooks) {
    var webhooksList = webhooks.stream()
      .map(w -> new ListWebhooksToolResponse.Webhook(w.key(), w.name(), w.url(), w.hasSecret()))
      .toList();

    return new ListWebhooksToolResponse(webhooksList);
  }

}
