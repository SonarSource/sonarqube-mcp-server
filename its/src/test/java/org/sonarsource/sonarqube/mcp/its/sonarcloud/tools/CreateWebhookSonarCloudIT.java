/*
 * SonarQube MCP Server
 * Copyright (C) SonarSource
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
package org.sonarsource.sonarqube.mcp.its.sonarcloud.tools;

import java.util.Map;
import org.sonarsource.sonarqube.mcp.its.sonarcloud.AbstractSonarCloudStagingIT;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.tools.webhooks.CreateWebhookTool;

import static org.sonarsource.sonarqube.mcp.its.sonarcloud.harness.SonarQubeMcpTestClient.assertStructuredContentContains;

@Tag("SonarCloud")
class CreateWebhookSonarCloudIT extends AbstractSonarCloudStagingIT {

  private static final String WEBHOOK_NAME = "sonarqube-mcp-it-webhook";
  private static final String WEBHOOK_URL = "https://example.invalid/sonarqube-mcp-it";

  @Test
  void should_call_create_webhook_against_staging() {
    String webhookKey = null;
    try {
      var result = mcpClient.callTool(CreateWebhookTool.TOOL_NAME, Map.of(
        CreateWebhookTool.NAME_PROPERTY, WEBHOOK_NAME,
        CreateWebhookTool.URL_PROPERTY, WEBHOOK_URL,
        CreateWebhookTool.PROJECT_PROPERTY, fixture.projectKey()));

      assertStructuredContentContains(result, """
        {
          "name" : "%s",
          "url" : "%s",
          "hasSecret" : false
        }""".formatted(WEBHOOK_NAME, WEBHOOK_URL));
      webhookKey = (String) structuredContent(result).get("key");
    } finally {
      if (webhookKey != null) {
        fixture.deleteProjectWebhook(webhookKey);
      }
    }
  }
}
