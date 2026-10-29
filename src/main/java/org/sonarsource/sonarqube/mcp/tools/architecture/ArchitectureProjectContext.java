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

import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.ResolvedOrganization;

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

  static String projectId(ServerApi serverApi, String projectKey, String resource) {
    if (serverApi.isSonarQubeCloud()) {
      return mainBranchId(serverApi, projectKey);
    }
    throw new IllegalStateException(resource + " on SonarQube Server requires a project UUID, but no supported public API resolves it from a project key. "
      + "Nothing was read or changed. Waiting for public project ID resolution.");
  }

  static String findingsProjectId(ServerApi serverApi, String projectKey) {
    if (serverApi.isSonarQubeCloud()) {
      return mainBranchId(serverApi, projectKey);
    }
    return serverApi.componentsApi().getProjectId(projectKey);
  }
}
