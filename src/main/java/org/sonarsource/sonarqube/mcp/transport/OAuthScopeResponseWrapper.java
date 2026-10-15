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

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.sonarsource.sonarqube.mcp.authentication.OAuthProtectedResourceMetadata;

/** Converts the stateless SDK's successful JSON-RPC transport status for a scope denial to HTTP 403. */
public final class OAuthScopeResponseWrapper extends HttpServletResponseWrapper {
  private final OAuthScopeContext authorization;
  private final OAuthProtectedResourceMetadata metadata;

  public OAuthScopeResponseWrapper(HttpServletResponse response, OAuthScopeContext authorization, OAuthProtectedResourceMetadata metadata) {
    super(response);
    this.authorization = authorization;
    this.metadata = metadata;
  }

  @Override
  public void setStatus(int status) {
    var required = authorization.requiredScopes();
    if (status == SC_OK && !required.isEmpty()) {
      setHeader("WWW-Authenticate", metadata.challenge(required) + ", error=\"insufficient_scope\"");
      setHeader("Cache-Control", "no-store");
      super.setStatus(SC_FORBIDDEN);
    } else {
      super.setStatus(status);
    }
  }
}
