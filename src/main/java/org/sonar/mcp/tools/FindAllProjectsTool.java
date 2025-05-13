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
package org.sonar.mcp.tools;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.sonar.mcp.slcore.BackendService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.SonarProjectDto;

public class FindAllProjectsTool {

  private static final String TOOL_NAME = "find_all_sonarqube_cloud_projects_starting_by";
  private static final String PREFIX_PROPERTY = "prefix";
  private final BackendService backendService;

  public FindAllProjectsTool(BackendService backendService) {
    this.backendService = backendService;
  }

  public McpSchema.Tool definition() {
    return new McpSchema.Tool(
      TOOL_NAME,
      "Find all SonarQube Cloud projects under my organization starting by a given prefix..",
      new McpSchema.JsonSchema(
        "object",
        Map.of(PREFIX_PROPERTY, Map.of("type", "string", "description", "Prefix for the projects")),
        List.of(PREFIX_PROPERTY),
        false
      )
    );
  }

  public McpServerFeatures.SyncToolSpecification spec() {
    return new McpServerFeatures.SyncToolSpecification(
      definition(),
      (McpSyncServerExchange exchange, Map<String, Object> argMap) -> findAllSonarQubeCloudProjects(argMap)
    );
  }

  private McpSchema.CallToolResult findAllSonarQubeCloudProjects(Map<String, Object> args) {
    var text = new StringBuilder();

    if (!args.containsKey(PREFIX_PROPERTY)) {
      return McpSchema.CallToolResult.builder()
        .addTextContent("Missing required argument: " + PREFIX_PROPERTY)
        .isError(true)
        .build();
    }
    var prefix = ((String) args.get(PREFIX_PROPERTY));

    try {
      var projects = backendService.findAllProjects().get(20, TimeUnit.SECONDS);
      var filteredProjects = projects.getSonarProjects().stream().filter(p -> p.getName().startsWith(prefix)).toList();

      text.append(buildResponseFromAllProjectsResponse(filteredProjects, prefix));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      return McpSchema.CallToolResult.builder()
        .addTextContent("Failed to fetch all projects: " + e.getMessage())
        .isError(true)
        .build();
    }

    return McpSchema.CallToolResult.builder()
      .addTextContent(text.toString())
      .build();
  }

  private static String buildResponseFromAllProjectsResponse(List<SonarProjectDto> response, String prefix) {
    var stringBuilder = new StringBuilder();

    if (response.isEmpty()) {
      stringBuilder.append("No projects were found starting by prefix '").append(prefix).append("'.");
      return stringBuilder.toString();
    }

    stringBuilder.append("Found ").append(response.size()).append(" Sonar projects in your organization starting by prefix '").append(prefix).append("'.\n");

    response.forEach(p -> {
      stringBuilder.append("Project key: ").append(p.getKey()).append(" | Project name: ").append(p.getName());
      stringBuilder.append("\n");
    });

    return stringBuilder.toString();
  }

}
