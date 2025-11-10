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
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record ListWebhooksToolResponse(
  @JsonPropertyDescription("List of configured webhooks") List<Webhook> webhooks
) {
  
  public record Webhook(
    @JsonPropertyDescription("Webhook unique key") String key,
    @JsonPropertyDescription("Webhook display name") String name,
    @JsonPropertyDescription("Target URL for the webhook") String url,
    @JsonPropertyDescription("Whether the webhook has a configured secret") boolean hasSecret
  ) {}
}


