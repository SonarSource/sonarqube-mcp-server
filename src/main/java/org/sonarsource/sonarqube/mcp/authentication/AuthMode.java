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

import java.util.Locale;
import jakarta.annotation.Nullable;

/**
 * Defines the authentication mode for the MCP HTTP server.
 * Note: Authentication is only used in HTTP mode. Stdio mode has no HTTP authentication.
 */
public enum AuthMode {

  /** Automatically accept SonarQube tokens and configured OAuth authentication. */
  AUTO,

  /**
   * Bearer token authentication.
   * Client must provide a valid SonarQube token via the standard {@code Authorization: Bearer <token>} header.
   * These tokens are the client's SonarQube tokens and are passed through to SonarQube API.
   */
  TOKEN,

  OAUTH;
  
  /**
   * Parse authentication mode from string (case-insensitive).
   * Defaults to AUTO if value is null or blank.
   */
  public static AuthMode fromString(@Nullable String value) {
    if (value == null || value.isBlank()) {
      return AUTO;
    }
    try {
      return AuthMode.valueOf(value.toUpperCase(Locale.getDefault()));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
        "Invalid authentication mode: " + value + ". Valid values are: AUTO, TOKEN, OAUTH"
      );
    }
  }

}


