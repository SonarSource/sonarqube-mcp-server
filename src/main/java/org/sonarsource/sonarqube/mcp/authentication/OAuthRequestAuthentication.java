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
package org.sonarsource.sonarqube.mcp.authentication;

import jakarta.annotation.Nullable;
import java.util.Set;

/**
 * Immutable Cloud credential and granted scopes for an authenticated request; the string representation redacts the credential.
 */
public record OAuthRequestAuthentication(String cloudToken, Set<String> scopes, @Nullable String oauthClientKey) {
  public OAuthRequestAuthentication {
    scopes = Set.copyOf(scopes);
  }

  public OAuthRequestAuthentication(String cloudToken, Set<String> scopes) {
    this(cloudToken, scopes, null);
  }

  @Override
  public String toString() {
    return "OAuthRequestAuthentication[cloudToken=redacted, scopes=" + scopes + "]";
  }
}
