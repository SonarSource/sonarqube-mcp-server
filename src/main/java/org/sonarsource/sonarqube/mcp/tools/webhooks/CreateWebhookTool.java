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

import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public class CreateWebhookTool extends Tool {

  public static final String TOOL_NAME = "create_webhook";
  public static final String NAME_PROPERTY = "name";
  public static final String URL_PROPERTY = "url";
  public static final String PROJECT_PROPERTY = "projectKey";
  public static final String SECRET_PROPERTY = "secret";

  private final ServerApiProvider serverApiProvider;

  public CreateWebhookTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(CreateWebhookToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Create SonarQube Webhook")
      .setDescription("Create a new webhook for the SonarQube organization or project. " +
        "Requires 'Administer' permission on the specified project, or global 'Administer' permission.")
      .addRequiredStringProperty(NAME_PROPERTY, "Name displayed in the administration console of webhooks (max 100 chars)")
      .addRequiredStringProperty(URL_PROPERTY, "Server endpoint that will receive the webhook payload (max 512 chars)")
      .addStringProperty(PROJECT_PROPERTY, "The key of the project that will own the webhook (max 400 chars)")
      .addStringProperty(SECRET_PROPERTY, "If provided, secret will be used as the key to generate the HMAC hex digest value " +
        "in the 'X-Sonar-Webhook-HMAC-SHA256' header (16-200 chars)")
      .build());
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Result execute(Arguments arguments) {
    var name = arguments.getStringOrThrow(NAME_PROPERTY);
    var url = arguments.getStringOrThrow(URL_PROPERTY);
    var project = arguments.getOptionalString(PROJECT_PROPERTY);
    var secret = arguments.getOptionalString(SECRET_PROPERTY);

    var apiResponse = serverApiProvider.get().webhooksApi().createWebhook(name, url, project, secret);
    var webhook = apiResponse.webhook();

    var response = new CreateWebhookToolResponse(webhook.key(), webhook.name(), webhook.url(), webhook.hasSecret());
    return Result.success(response);
  }

}
