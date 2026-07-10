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
package org.sonarsource.sonarqube.mcp.serverapi.organizations;

import jakarta.annotation.Nullable;

/**
 * SonarQube Cloud organization key and UUID v4 resolved for the current stdio session.
 * {@code uuidV4} is present when known from {@link OrganizationsApi#listOrganizations()}; otherwise it is fetched on demand.
 */
public record ResolvedOrganization(String key, @Nullable String uuidV4) {

  public static ResolvedOrganization fromKey(String key) {
    return new ResolvedOrganization(key, null);
  }

  public static ResolvedOrganization from(OrganizationsApi.Organization organization) {
    return new ResolvedOrganization(organization.key(), organization.uuidV4());
  }

}
