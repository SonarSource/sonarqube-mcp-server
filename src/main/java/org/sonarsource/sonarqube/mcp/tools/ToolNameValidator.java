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
package org.sonarsource.sonarqube.mcp.tools;

import java.util.regex.Pattern;

/**
 * Validates tool names according to MCP SEP-986 specification.
 * @see <a href="https://modelcontextprotocol.io/community/seps/986-specify-format-for-tool-names">MCP SEP-986</a>
 */
public class ToolNameValidator {

  private static final int MAX_LENGTH = 64;
  
  // Allowed characters: a-z, A-Z, 0-9, _, -, ., /
  private static final Pattern VALID_TOOL_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-./]+$");

  private ToolNameValidator() {
    // Utility class
  }

  /**
   * Validates a tool name according to MCP SEP-986:
   * - Length: 1-64 characters
   * - Allowed characters: a-z, A-Z, 0-9, _, -, ., /
   * - Case-sensitive
   */
  public static void validate(String toolName) {
    if (toolName.isEmpty()) {
      throw new IllegalArgumentException("Tool name cannot be null or empty");
    }

    if (toolName.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
        "Tool name must be " + MAX_LENGTH + " characters maximum. " +
        "Found: '" + toolName + "' (" + toolName.length() + " characters)"
      );
    }

    if (!VALID_TOOL_NAME_PATTERN.matcher(toolName).matches()) {
      throw new IllegalArgumentException(
        "Tool name contains invalid characters. Only alphanumeric characters, underscores (_), " +
        "dashes (-), dots (.), and forward slashes (/) are allowed. Found: '" + toolName + "'"
      );
    }
  }

}
