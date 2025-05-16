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
package org.sonar.mcp.tools.projects;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.sonar.mcp.serverapi.ServerApi;
import org.sonar.mcp.serverapi.exception.NotFoundException;
import org.sonar.mcp.serverapi.projects.ProjectsApi;
import org.sonar.mcp.tools.Tool;

public class SearchMyProjectsTool extends Tool {

  public static final String TOOL_NAME = "search_my_sonarqube_cloud_projects";

  private final ServerApi serverApi;

  public SearchMyProjectsTool(ServerApi serverApi) {
    super(new McpSchema.Tool(
      TOOL_NAME,
      "Find all my SonarQube Cloud projects.",
      new McpSchema.JsonSchema(
        "object",
        Map.of(),
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

    var text = new StringBuilder();
    try {
      var projects = serverApi.projectsApi().searchMyProjects();
      text.append(buildResponseFromAllProjectsResponse(projects.projects()));
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

  private static String buildResponseFromAllProjectsResponse(List<ProjectsApi.ProjectResponse> projects) {
    var stringBuilder = new StringBuilder();

    if (projects.isEmpty()) {
      stringBuilder.append("No projects were found.");
      return stringBuilder.toString();
    }

    stringBuilder.append("Found ").append(projects.size()).append(" Sonar projects in your organization.\n");

    projects.forEach(p -> {
      stringBuilder.append("Project key: ").append(p.key()).append(" | Project name: ").append(p.name());
      stringBuilder.append("\n");
    });

    return stringBuilder.toString();
  }
}
