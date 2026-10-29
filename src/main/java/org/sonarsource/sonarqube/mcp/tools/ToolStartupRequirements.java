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
package org.sonarsource.sonarqube.mcp.tools;

import java.util.List;
import jakarta.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.ResolvedOrganization;
import org.sonarsource.sonarqube.mcp.tools.ToolAvailabilityPolicy.StartupRequirement;

/** Resolves startup requirements using the fixed startup identity. Results are cached by {@link ToolRegistrar}. */
public final class ToolStartupRequirements {

  private static final McpLogger LOG = McpLogger.getInstance();

  private final ServerApi serverApi;
  private final List<ResolvedOrganization> organizations;

  public ToolStartupRequirements(ServerApi serverApi, @Nullable ResolvedOrganization selectedOrganization, List<ResolvedOrganization> organizations) {
    this.serverApi = serverApi;
    this.organizations = selectedOrganization != null ? List.of(selectedOrganization) : List.copyOf(organizations);
  }

  public boolean isSatisfied(StartupRequirement requirement) {
    return switch (requirement) {
      case SARA_ENABLED -> isSaraEnabled();
    };
  }

  private boolean isSaraEnabled() {
    for (var organization : organizations) {
      try {
        if (serverApi.orgFeatureEntitlements().isSaraEnabledForOrg(organization)) {
          return true;
        }
      } catch (Exception e) {
        LOG.warn("Could not check agentic readiness feature flag for organization '" + organization.key() + "': " + e.getMessage());
      }
    }
    return false;
  }
}
