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
package org.sonarsource.sonarqube.mcp.tools.branches;

import jakarta.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class ListBranchesTool extends Tool {

  public static final String TOOL_NAME = "list_branches";
  public static final String PROJECT_KEY_PROPERTY = "projectKey";

  private final ServerApiProvider serverApiProvider;
  @Nullable
  private final String configuredProjectKey;

  public ListBranchesTool(ServerApiProvider serverApiProvider, @Nullable String configuredProjectKey) {
    super(SchemaToolBuilder.forOutput(ListBranchesToolResponse.class)
        .setName(TOOL_NAME)
        .setTitle("List SonarQube Branches")
        .setDescription("List branches for a project. " +
          "Use returned branch names as the branch parameter on other tools (e.g. get_project_quality_gate_status, get_component_measures). " +
          "For pull requests and feature branches, use list_pull_requests instead.")
        .addProjectKeyProperty(PROJECT_KEY_PROPERTY, "Project key (e.g. my_project)", configuredProjectKey)
        .setReadOnlyHint()
        .build(),
      ToolCategory.PROJECTS);
    this.serverApiProvider = serverApiProvider;
    this.configuredProjectKey = configuredProjectKey;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var projectKey = arguments.getProjectKeyWithFallback(PROJECT_KEY_PROPERTY, configuredProjectKey);

    var response = serverApiProvider.get().projectBranchesApi().listBranches(projectKey);

    var branches = response.branches().stream()
      .map(branch -> new ListBranchesToolResponse.Branch(
        branch.name(),
        Boolean.TRUE.equals(branch.isMain()),
        branch.type(),
        branch.status() != null ? branch.status().qualityGateStatus() : null,
        branch.analysisDate(),
        branch.branchId()
      ))
      .toList();

    var toolResponse = new ListBranchesToolResponse(
      projectKey,
      branches.size(),
      branches
    );

    return Tool.Result.success(toolResponse);
  }

}
