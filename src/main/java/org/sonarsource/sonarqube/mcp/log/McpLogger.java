/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.sonarqube.mcp.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpLogger {
  private static final Logger LOG = LoggerFactory.getLogger(McpLogger.class);
  private static final McpLogger INSTANCE = new McpLogger();

  public static McpLogger getInstance() {
    return INSTANCE;
  }

  public void info(String message) {
    LOG.info(message);
  }

  public void error(String message, Throwable throwable) {
    LOG.error(message, throwable);
  }
}
