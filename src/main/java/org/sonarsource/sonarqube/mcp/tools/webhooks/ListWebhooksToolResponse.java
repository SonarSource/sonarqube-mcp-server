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
import org.sonarsource.sonarqube.mcp.tools.Description;

public record ListWebhooksToolResponse(
  @Description("List of configured webhooks") List<Webhook> webhooks
) {
  
  public record Webhook(
    @Description("Webhook unique key") String key,
    @Description("Webhook display name") String name,
    @Description("Target URL for the webhook") String url,
    @Description("Whether the webhook has a configured secret") boolean hasSecret
  ) {}
}


