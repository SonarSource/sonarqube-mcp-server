/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
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

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Defines the authentication mode for the MCP HTTP server.
 * Note: Authentication is only used in HTTP mode. Stdio mode has no HTTP authentication.
 */
public enum AuthMode {

  /**
   * Bearer token authentication.
   * Client must provide a valid token in the SONARQUBE_TOKEN header.
   * These tokens are the client's SonarQube tokens and are passed through to SonarQube API.
   * This is the default mode.
   */
  TOKEN,

  OAUTH;
  
  /**
   * Parse authentication mode from string (case-insensitive).
   * Defaults to TOKEN if value is null or blank.
   */
  public static AuthMode fromString(@Nullable String value) {
    if (value == null || value.isBlank()) {
      return TOKEN;
    }
    try {
      return AuthMode.valueOf(value.toUpperCase(Locale.getDefault()));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
        "Invalid authentication mode: " + value + ". Valid values are: TOKEN, OAUTH"
      );
    }
  }

}


