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
package org.sonarsource.sonarqube.mcp.tools;

/**
 * Canonical MCP tool parameter names shared across all tools.
 */
public final class ToolParameters {

  public static final String PROJECT_KEY = "projectKey";
  public static final String PROJECT_KEYS = "projectKeys";
  public static final String PAGE_INDEX = "pageIndex";
  public static final String PAGE_SIZE = "pageSize";

  private ToolParameters() {
    // utility class
  }

}
