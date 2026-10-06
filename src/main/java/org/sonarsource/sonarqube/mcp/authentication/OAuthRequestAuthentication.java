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

import java.util.Set;

public final class OAuthRequestAuthentication {
  private final String cloudToken;
  private final Set<String> scopes;

  OAuthRequestAuthentication(String cloudToken, Set<String> scopes) {
    this.cloudToken = cloudToken;
    this.scopes = Set.copyOf(scopes);
  }

  public String cloudToken() {
    return cloudToken;
  }

  public Set<String> scopes() {
    return scopes;
  }

  @Override
  public String toString() {
    return "OAuthRequestAuthentication[cloudToken=redacted, scopes=" + scopes + "]";
  }
}

