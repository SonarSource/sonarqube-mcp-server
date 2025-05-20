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
package org.sonar.mcp.serverapi;

import javax.annotation.Nullable;
import org.sonar.mcp.serverapi.issues.IssuesApi;
import org.sonar.mcp.serverapi.projects.ProjectsApi;

public class ServerApi {

  private final ServerApiHelper helper;
  private final boolean isAuthenticationSet;

  public ServerApi(ServerApiHelper helper, @Nullable String token) {
    this.helper = helper;
    this.isAuthenticationSet = token != null && helper.getOrganization() != null;
  }

  public ProjectsApi projectsApi() {
    return new ProjectsApi(helper);
  }

  public IssuesApi issuesApi() {
    return new IssuesApi(helper, helper.getOrganization());
  }

  public boolean isAuthenticationSet() {
    return isAuthenticationSet;
  }

}
