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
package org.sonarsource.sonarqube.mcp.tools.architecture;

import java.util.List;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;
import org.sonarsource.sonarqube.mcp.serverapi.branches.response.BranchesListResponse;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.ResolvedOrganization;
import org.sonarsource.sonarqube.mcp.serverapi.pullrequests.response.PullRequestsListResponse;
import org.sonarsource.sonarqube.mcp.tools.BranchPullRequestContext;

final class ArchitectureProjectContext {
  private ArchitectureProjectContext() {
    // static only
  }

  static String organizationId(ServerApi serverApi) {
    if (!serverApi.isSonarQubeCloud()) {
      return ArchitectureApi.SERVER_ORGANIZATION_ID;
    }
    serverApi.requireOrganization();
    var organization = serverApi.getOrganization();
    var uuid = serverApi.resolveOrganizationUuid(ResolvedOrganization.fromKey(organization));
    if (uuid == null) {
      throw new IllegalStateException("Could not resolve the id of organization '" + organization
        + "'. Check the organization key with list_organizations, and the token.");
    }
    return uuid;
  }

  static String mainBranchId(ServerApi serverApi, String projectKey) {
    return serverApi.projectBranchesApi().getProjectId(projectKey)
      .orElseThrow(() -> new IllegalStateException("Project '" + projectKey
        + "' has no main branch. Use search_my_sonarqube_projects to check the project key."));
  }

  /**
   * Id of the named branch, of the pull request with the given key, or of the main branch when neither is given.
   */
  static String branchId(ServerApi serverApi, String projectKey, BranchPullRequestContext.Params params) {
    var pullRequest = params.pullRequest();
    if (pullRequest != null) {
      var response = serverApi.pullRequestsApi().listPullRequests(projectKey);
      var match = (response == null || response.pullRequests() == null ? List.<PullRequestsListResponse.PullRequest>of() : response.pullRequests()).stream()
        .filter(candidate -> candidate != null && pullRequest.equals(candidate.key()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Pull request '" + pullRequest + "' was not found in project '" + projectKey
          + "'. Use list_pull_requests to find its key."));
      if (match.pullRequestId() == null) {
        throw new IllegalStateException("SonarQube did not return the id of pull request '" + pullRequest + "', so its analysis cannot be read.");
      }
      return match.pullRequestId();
    }
    var branch = params.branch();
    if (branch != null) {
      var response = serverApi.projectBranchesApi().listBranches(projectKey);
      return (response == null || response.branches() == null ? List.<BranchesListResponse.Branch>of() : response.branches()).stream()
        .filter(candidate -> candidate != null && branch.equals(candidate.name()) && candidate.branchId() != null)
        .findFirst()
        .map(BranchesListResponse.Branch::branchId)
        .orElseThrow(() -> new IllegalArgumentException("Branch '" + branch + "' was not found in project '" + projectKey
          + "'. Use list_branches to find its name."));
    }
    return mainBranchId(serverApi, projectKey);
  }

  static String projectId(ServerApi serverApi, String projectKey) {
    if (serverApi.isSonarQubeCloud()) {
      return mainBranchId(serverApi, projectKey);
    }
    return serverApi.componentsApi().getProjectId(projectKey);
  }
}
