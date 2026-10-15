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

/** Verified grants and a denial marker owned by one HTTP request, never by the token cache. */
public final class OAuthScopeContext {
  public static final String CONTEXT_KEY = "oauth-scopes";

  private final Set<String> grantedScopes;
  private volatile Set<String> requiredScopes = Set.of();

  public OAuthScopeContext(Set<String> grantedScopes) {
    this.grantedScopes = Set.copyOf(grantedScopes);
  }

  public boolean permits(Set<String> scopes) {
    return grantedScopes.containsAll(scopes);
  }

  public void deny(Set<String> scopes) {
    requiredScopes = Set.copyOf(scopes);
  }

  public Set<String> requiredScopes() {
    return requiredScopes;
  }
}
