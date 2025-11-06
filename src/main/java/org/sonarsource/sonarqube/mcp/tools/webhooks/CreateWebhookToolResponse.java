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

import com.fasterxml.jackson.annotation.JsonInclude;
import org.sonarsource.sonarqube.mcp.tools.Description;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateWebhookToolResponse(
  @Description("The created webhook key") String key,
  @Description("The webhook name") String name,
  @Description("The webhook URL") String url,
  @Description("Whether the webhook has a secret") boolean hasSecret
) {}

