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
package org.sonar.mcp.tools.projects;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.sonar.mcp.serverapi.ServerApi;
import org.sonar.mcp.serverapi.components.ComponentsApi;
import org.sonar.mcp.serverapi.exception.NotFoundException;
import org.sonar.mcp.tools.Tool;

public class SearchMyProjectsTool extends Tool {

  public static final String TOOL_NAME = "search_my_sonarqube_cloud_projects";
  public static final String PAGE_PROPERTY = "page";

  private final ServerApi serverApi;

  public SearchMyProjectsTool(ServerApi serverApi) {
    super(new McpSchema.Tool(
      TOOL_NAME,
      """
        Find Sonar projects in my organization. The response is paginated.
        """,
      new McpSchema.JsonSchema(
        "object",
        Map.of(PAGE_PROPERTY, Map.of("type", "string", "description", """
            An optional page number. Defaults to 1.
            """)),
        List.of(),
        false
      )
    ));
    this.serverApi = serverApi;
  }

  @Override
  public McpSchema.CallToolResult execute(Map<String, Object> arguments) {
    if (!serverApi.isAuthenticationSet()) {
      return McpSchema.CallToolResult.builder()
        .addTextContent("Not connected to SonarQube Cloud, please provide 'SONARQUBE_CLOUD_TOKEN' and 'SONARQUBE_CLOUD_ORG'")
        .isError(true)
        .build();
    }

    int page = 1;
    if (arguments.containsKey(PAGE_PROPERTY)) {
      page = Integer.parseInt((String) arguments.get(PAGE_PROPERTY));
    }

    var text = new StringBuilder();
    try {
      var projects = serverApi.componentsApi().searchProjectsInMyOrg(page);
      text.append(buildResponseFromAllProjectsResponse(projects));
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

  private static String buildResponseFromAllProjectsResponse(ComponentsApi.SearchResponse response) {
    var stringBuilder = new StringBuilder();
    var projects = response.components();

    if (projects.isEmpty()) {
      stringBuilder.append("No projects were found.");
      return stringBuilder.toString();
    }

    stringBuilder.append("Found ").append(projects.size()).append(" Sonar projects in your organization.\n");
    stringBuilder.append("This response is paginated and this is the page ").append(response.paging().pageIndex())
      .append(" out of ").append(response.paging().total()).append(" total pages. There is a maximum of ")
      .append(response.paging().pageSize()).append(" projects per page.\n");

    projects.forEach(p -> {
      stringBuilder.append("Project key: ").append(p.key()).append(" | Project name: ").append(p.name());
      stringBuilder.append("\n");
    });

    return stringBuilder.toString();
  }

}
