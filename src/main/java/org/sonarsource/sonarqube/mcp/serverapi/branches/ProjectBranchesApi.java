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
package org.sonarsource.sonarqube.mcp.serverapi.branches;

import com.google.gson.Gson;
import java.util.Optional;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.UrlBuilder;
import org.sonarsource.sonarqube.mcp.serverapi.branches.response.BranchesListResponse;

public class ProjectBranchesApi {

  public static final String BRANCHES_LIST_PATH = "/api/project_branches/list";

  private final ServerApiHelper helper;

  public ProjectBranchesApi(ServerApiHelper helper) {
    this.helper = helper;
  }

  public BranchesListResponse listBranches(String projectKey) {
    var url = new UrlBuilder(BRANCHES_LIST_PATH)
      .addParam("project", projectKey)
      .build();

    try (var response = helper.get(url)) {
      var responseStr = response.bodyAsString();
      return new Gson().fromJson(responseStr, BranchesListResponse.class);
    }
  }

  /**
   * Resolves the project's internal id from its key. On SonarQube Cloud the {@code branchId} of a
   * project's main branch is the project id, so we read it from the main branch entry returned by
   * {@code api/project_branches/list}. Returns empty when no main branch can be found.
   */
  public Optional<String> getProjectId(String projectKey) {
    var branches = listBranches(projectKey);
    if (branches == null || branches.branches() == null) {
      return Optional.empty();
    }
    return branches.branches().stream()
      .filter(BranchesListResponse.Branch::isMain)
      .findFirst()
      .map(BranchesListResponse.Branch::branchId);
  }

}
