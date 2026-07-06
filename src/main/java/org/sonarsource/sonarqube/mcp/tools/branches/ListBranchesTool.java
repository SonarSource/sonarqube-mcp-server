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
import org.sonarsource.sonarqube.mcp.tools.ToolParameters;

import static org.sonarsource.sonarqube.mcp.tools.branches.BranchTypes.BRANCH_TYPES_FILTER_VALUES;
import static org.sonarsource.sonarqube.mcp.tools.branches.BranchTypes.matchesBranchTypesFilter;
import static org.sonarsource.sonarqube.mcp.tools.branches.BranchTypes.parseBranchType;
import static org.sonarsource.sonarqube.mcp.tools.branches.BranchTypes.parseQualityGateStatus;

public class ListBranchesTool extends Tool {

  public static final String TOOL_NAME = "list_branches";
  public static final String PROJECT_KEY_PROPERTY = ToolParameters.PROJECT_KEY;
  public static final String BRANCH_TYPES_PROPERTY = "branchTypes";

  private final ServerApiProvider serverApiProvider;
  @Nullable
  private final String configuredProjectKey;

  public ListBranchesTool(ServerApiProvider serverApiProvider, @Nullable String configuredProjectKey) {
    super(SchemaToolBuilder.forOutput(ListBranchesToolResponse.class)
        .setName(TOOL_NAME)
        .setTitle("List SonarQube Branches")
        .setDescription("List analyzed branches for a project (long-lived and short-lived). " +
          "Use returned branch names as the branch parameter on other tools (e.g. get_project_quality_gate_status, get_component_measures). " +
          "On SonarQube Cloud, check the type field: LONG for main/develop, SHORT for feature branches analyzed without pull requests. " +
          "For pull request analysis, use list_pull_requests instead.")
        .addProjectKeyProperty(PROJECT_KEY_PROPERTY, configuredProjectKey)
        .addEnumProperty(BRANCH_TYPES_PROPERTY, BRANCH_TYPES_FILTER_VALUES,
          "Filter branches by type. ALL (default) returns all analyzed branches; LONG returns long-lived branches only; SHORT returns short-lived branches only (SonarQube Cloud).")
        .setReadOnlyHint()
        .build(),
      ToolCategory.PROJECTS);
    this.serverApiProvider = serverApiProvider;
    this.configuredProjectKey = configuredProjectKey;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var projectKey = arguments.getProjectKeyWithFallback(PROJECT_KEY_PROPERTY, configuredProjectKey);
    var branchTypesFilter = arguments.getOptionalEnumValue(BRANCH_TYPES_PROPERTY, BRANCH_TYPES_FILTER_VALUES);

    var response = serverApiProvider.get().projectBranchesApi().listBranches(projectKey);

    var branches = response.branches().stream()
      .filter(branch -> matchesBranchTypesFilter(branch.type(), branchTypesFilter))
      .map(branch -> new ListBranchesToolResponse.Branch(
        branch.name(),
        branch.isMain(),
        parseBranchType(branch.type()),
        branch.status() != null ? parseQualityGateStatus(branch.status().qualityGateStatus()) : null,
        branch.analysisDate(),
        branch.branchId(),
        branch.mergeBranch()
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
