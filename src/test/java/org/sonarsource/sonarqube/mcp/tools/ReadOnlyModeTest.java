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
package org.sonarsource.sonarqube.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.tools.issues.ChangeIssueStatusTool;
import org.sonarsource.sonarqube.mcp.tools.issues.SearchIssuesTool;
import org.sonarsource.sonarqube.mcp.tools.webhooks.CreateWebhookTool;
import org.sonarsource.sonarqube.mcp.tools.webhooks.ListWebhooksTool;

import static org.assertj.core.api.Assertions.assertThat;

class ReadOnlyModeTest {

  @SonarQubeMcpServerTest
  void should_exclude_write_tools_when_read_only_mode_is_enabled(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient(Map.of(
      "SONARQUBE_ORG", "org",
      "SONARQUBE_READ_ONLY", "true"
    ));

    var toolNames = mcpClient.listTools().stream().map(McpSchema.Tool::name).toList();

    // Write tools should be excluded
    assertThat(toolNames).doesNotContain(
      ChangeIssueStatusTool.TOOL_NAME,
      CreateWebhookTool.TOOL_NAME
    );

    // Read-only tools should be included
    assertThat(toolNames).contains(
      SearchIssuesTool.TOOL_NAME,
      ListWebhooksTool.TOOL_NAME
    );
  }

  @SonarQubeMcpServerTest
  void should_include_all_tools_when_read_only_mode_is_disabled(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient(Map.of(
      "SONARQUBE_ORG", "org",
      "SONARQUBE_READ_ONLY", "false"
    ));

    var toolNames = mcpClient.listTools().stream().map(McpSchema.Tool::name).toList();

    // All tools should be included write tools
    assertThat(toolNames).contains(
      ChangeIssueStatusTool.TOOL_NAME,
      CreateWebhookTool.TOOL_NAME,
      SearchIssuesTool.TOOL_NAME,
      ListWebhooksTool.TOOL_NAME
    );
  }

  @SonarQubeMcpServerTest
  void should_include_all_tools_by_default(SonarQubeMcpServerTestHarness harness) {
    var mcpClient = harness.newClient(Map.of(
      "SONARQUBE_ORG", "org"
    ));

    var toolNames = mcpClient.listTools().stream().map(McpSchema.Tool::name).toList();

    // All tools should be included by default (read-only mode disabled)
    assertThat(toolNames).contains(
      ChangeIssueStatusTool.TOOL_NAME,
      CreateWebhookTool.TOOL_NAME,
      SearchIssuesTool.TOOL_NAME,
      ListWebhooksTool.TOOL_NAME
    );
  }

}

