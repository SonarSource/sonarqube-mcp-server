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
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.sonar.mcp.serverapi.ServerApi;
import org.sonar.mcp.serverapi.exception.NotFoundException;
import org.sonar.mcp.serverapi.issues.IssuesApi;
import org.sonar.mcp.tools.SchemaToolBuilder;
import org.sonar.mcp.tools.Tool;

public class SearchIssuesTool extends Tool {

  public static final String TOOL_NAME = "search_sonar_issues_in_projects";
  public static final String PROJECTS_PROPERTY = "projects";

  private final ServerApi serverApi;

  public SearchIssuesTool(ServerApi serverApi) {
    super(new SchemaToolBuilder()
      .setName(TOOL_NAME)
      .setDescription("Search for Sonar issues in my organization's projects.")
      .addStringProperty(PROJECTS_PROPERTY, "A list of optional Sonar projects to look in, separated by commas. For example, \"project1,project2\".")
      .build());
    this.serverApi = serverApi;
  }

  @Override
  public McpSchema.CallToolResult execute(Map<String, Object> arguments) {
    String[] projects = null;
    if (arguments.containsKey(PROJECTS_PROPERTY)) {
      projects = ((String) arguments.get(PROJECTS_PROPERTY)).split(",");
    }

    var text = new StringBuilder();
    try {
      var response = serverApi.issuesApi().searchIssuesInProject(projects);
      text.append(buildResponseFromSearchResponse(response.issues()));
    } catch (Exception e) {
      String message;
      if (e instanceof NotFoundException) {
        message = "Make sure your token is valid.";
      } else {
        message = e instanceof ResponseErrorException responseErrorException ? responseErrorException.getResponseError().getMessage() : e.getMessage();
      }
      return McpSchema.CallToolResult.builder()
        .addTextContent("Failed to fetch all projects: " + message)
        .isError(true)
        .build();
    }

    return McpSchema.CallToolResult.builder()
      .addTextContent(text.toString())
      .isError(false)
      .build();
  }

  private static String buildResponseFromSearchResponse(List<IssuesApi.Issue> issues) {
    var stringBuilder = new StringBuilder();

    if (issues.isEmpty()) {
      stringBuilder.append("No issues were found.");
      return stringBuilder.toString();
    }

    stringBuilder.append("Found ").append(issues.size()).append(" issues.\n");

    issues.forEach(p -> {
      stringBuilder.append("Issue key: ").append(p.key()).append(" | Rule name: ").append(p.rule()).append(" | Project name: ").append(p.project());
      stringBuilder.append("\n");
    });

    return stringBuilder.toString();
  }

}
