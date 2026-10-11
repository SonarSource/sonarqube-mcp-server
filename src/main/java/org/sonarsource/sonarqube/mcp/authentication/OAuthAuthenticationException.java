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

public final class OAuthAuthenticationException extends RuntimeException {
  private final int status;

  private OAuthAuthenticationException(int status, String message) {
    super(message);
    this.status = status;
  }

  public static OAuthAuthenticationException unauthorized() {
    return new OAuthAuthenticationException(401, "A valid MCP OAuth access token is required");
  }

  public static OAuthAuthenticationException insufficientScope() {
    return new OAuthAuthenticationException(403, "MCP read or write delegation is required");
  }

  public static OAuthAuthenticationException unavailable() {
    return new OAuthAuthenticationException(503, "OAuth authentication service is temporarily unavailable");
  }

  public static OAuthAuthenticationException invalidExchange() {
    return new OAuthAuthenticationException(502, "OAuth exchange did not return an acceptable Cloud access token");
  }

  public int status() {
    return status;
  }
}

