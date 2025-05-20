/*
 * Sonar MCP Server
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
package org.sonar.mcp.tools.issues;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.sonar.mcp.serverapi.ServerApi;
import org.sonar.mcp.serverapi.issues.Transition;
import org.sonar.mcp.tools.Tool;

public class ChangeIssueStatusTool extends Tool {

  public static final String TOOL_NAME = "change_sonar_issue_status";
  public static final String KEY_PROPERTY = "key";
  public static final String STATUS_PROPERTY = "status";
  public static final String DESCRIPTION_PROPERTY = "description";

  private final ServerApi serverApi;

  public ChangeIssueStatusTool(ServerApi serverApi) {
    super(new McpSchema.Tool(
      TOOL_NAME,
      """
        Change the status of a Sonar issue. This tool can be used to change the status of an issue to "accept", "falsepositive" or to "reopen" an issue.
        An example request could be: I would like to accept the issue having the key "AX-HMISMFixnZED\"""",
      new McpSchema.JsonSchema(
        "object",
        Map.of(
          KEY_PROPERTY, Map.of(
            "type", "string",
            DESCRIPTION_PROPERTY, "The key of the issue which status should be changed"),
          STATUS_PROPERTY, Map.of(
            "type", "array",
            "items", Map.of("enum", new String[]{"accept", "falsepositive", "reopen"}),
            DESCRIPTION_PROPERTY, "The new status of the issue")
        ),
        List.of(KEY_PROPERTY, STATUS_PROPERTY),
        false
      )
    ));
    this.serverApi = serverApi;
  }

  @Override
  public McpSchema.CallToolResult execute(Map<String, Object> arguments) {
    if (!arguments.containsKey(KEY_PROPERTY)) {
      return McpSchema.CallToolResult.builder()
        .addTextContent("Missing required argument: " + KEY_PROPERTY)
        .isError(true)
        .build();
    }
    var key = (String) arguments.get(KEY_PROPERTY);

    if (!arguments.containsKey(STATUS_PROPERTY)) {
      return McpSchema.CallToolResult.builder()
        .addTextContent("Missing required argument: " + STATUS_PROPERTY)
        .isError(true)
        .build();
    }
    var statusString = ((List<String>) arguments.get(STATUS_PROPERTY)).get(0);
    var status = Transition.fromStatus(statusString);
    if (status.isEmpty()) {
      return McpSchema.CallToolResult.builder()
        .addTextContent("Status is unknown: " + statusString)
        .isError(true)
        .build();
    }

    var text = new StringBuilder();
    try {
      serverApi.issuesApi().doTransition(key, status.get());
      text.append("The issue status was successfully changed.");
    } catch (Exception e) {
      return McpSchema.CallToolResult.builder()
        .addTextContent("Failed to change the issue status: " + e.getMessage())
        .isError(true)
        .build();
    }

    return McpSchema.CallToolResult.builder()
      .addTextContent(text.toString())
      .isError(false)
      .build();
  }

}
