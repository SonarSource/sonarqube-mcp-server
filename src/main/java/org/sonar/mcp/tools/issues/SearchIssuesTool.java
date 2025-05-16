/*
 * Sonar MCP Server
 * Copyright (C) 2025 SonarSource
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.mcp.tools.issues;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.sonar.mcp.serverapi.ServerApi;
import org.sonar.mcp.serverapi.exception.NotFoundException;
import org.sonar.mcp.serverapi.issues.IssuesApi;

public class SearchIssuesTool {

  public static final String TOOL_NAME = "search_sonar_issues_in_projects";
  public static final String PROJECTS_PROPERTY = "projects";

  private final ServerApi serverApi;

  public SearchIssuesTool(ServerApi serverApi) {
    this.serverApi = serverApi;
  }

  public McpSchema.Tool definition() {
    return new McpSchema.Tool(
      TOOL_NAME,
      "Search for Sonar issues in my organization's projects.",
      new McpSchema.JsonSchema(
        "object",
        Map.of(
          PROJECTS_PROPERTY, Map.of("type", "string", "description", """
            A list of optional Sonar projects to look in, separated by commas. For example, "project1,project2".
            """)
        ),
        List.of(),
        false
      )
    );
  }

  public McpServerFeatures.SyncToolSpecification spec() {
    return new McpServerFeatures.SyncToolSpecification(
      definition(),
      (McpSyncServerExchange exchange, Map<String, Object> argMap) -> searchSonarIssuesInProjects(argMap)
    );
  }

  private McpSchema.CallToolResult searchSonarIssuesInProjects(Map<String, Object> args) {
    String[] projects = null;
    if (args.containsKey(PROJECTS_PROPERTY)) {
      projects = ((String) args.get(PROJECTS_PROPERTY)).split(",");
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
