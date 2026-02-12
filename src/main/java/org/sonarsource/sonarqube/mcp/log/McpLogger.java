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
package org.sonarsource.sonarqube.mcp.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MCP-specific logger that outputs to both:
 * - STDERR: for MCP clients (the MCP protocol uses STDERR for diagnostic logs, STDOUT is reserved for JSON-RPC messages)
 * - Log file: via SLF4J/Logback for persistence and debugging
 */
public class McpLogger {

  private static final Logger LOG = LoggerFactory.getLogger(McpLogger.class);
  private static final McpLogger INSTANCE = new McpLogger();

  public static McpLogger getInstance() {
    return INSTANCE;
  }

  public void info(String message) {
    LOG.info(message);
    logToStderr("INFO", message);
  }

  public void debug(String message) {
    LOG.debug(message);
    logToStderr("DEBUG", message);
  }

  public void warn(String message) {
    LOG.warn(message);
    logToStderr("WARN", message);
  }

  public void error(String message, Throwable throwable) {
    LOG.error(message, throwable);
    logToStderr("ERROR", message);
    throwable.printStackTrace(System.err);
  }
  
  public void error(String message) {
    LOG.error(message);
    logToStderr("ERROR", message);
  }

  private static void logToStderr(String level, String message) {
    System.err.println(level + " SonarQube MCP Server - " + message);
  }

}
