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
package org.sonarsource.sonarqube.mcp.transport;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.sonarsource.sonarqube.mcp.tools.ToolAccessMode;

/** Verified grants and a denial marker owned by one HTTP request, never by the token cache. */
public final class OAuthScopeContext {
  public static final String CONTEXT_KEY = "oauth-scopes";

  private final Set<String> grantedScopes;
  private final AtomicReference<Set<OAuthScope>> requiredScopes = new AtomicReference<>(Set.of());

  public OAuthScopeContext(Set<String> grantedScopes) {
    this.grantedScopes = Set.copyOf(grantedScopes);
  }

  public boolean permits(ToolAccessMode accessMode) {
    return OAuthScope.requiredBy(accessMode).stream().allMatch(scope -> grantedScopes.contains(scope.getCloudScope()));
  }

  public void deny(ToolAccessMode accessMode) {
    requiredScopes.set(OAuthScope.requiredBy(accessMode));
  }

  public Set<OAuthScope> requiredScopes() {
    return requiredScopes.get();
  }
}
