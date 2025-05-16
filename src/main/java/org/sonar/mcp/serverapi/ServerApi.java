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
