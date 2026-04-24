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
package org.sonarsource.sonarqube.mcp.log;

import io.modelcontextprotocol.spec.McpSchema;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MCP-specific logger that outputs to:
 * - Log file: via SLF4J/Logback for persistence and debugging
 * - MCP client: as {@code notifications/message} (per the MCP logging spec) when a notifier is configured.
 *   Outside of a session (e.g. during startup, before the MCP server is built) the notifier is a no-op
 *   and only SLF4J file logging happens.
 */
public class McpLogger {

  private static final Logger LOG = LoggerFactory.getLogger(McpLogger.class);
  private static final McpLogger INSTANCE = new McpLogger();
  private static final String SONARQUBE_DEBUG_ENABLED = "SONARQUBE_DEBUG_ENABLED";
  private static final String LOGGER_NAME = "sonarqube-mcp-server";
  private static final Consumer<McpSchema.LoggingMessageNotification> NO_OP = notification -> {
    // no-op
  };

  private Consumer<McpSchema.LoggingMessageNotification> notifier = NO_OP;

  public static McpLogger getInstance() {
    return INSTANCE;
  }

  public static boolean isDebugEnabled() {
    return resolveDebugEnabled();
  }

  private static boolean resolveDebugEnabled() {
    var envValue = System.getenv(SONARQUBE_DEBUG_ENABLED);
    if (envValue != null) {
      return "true".equalsIgnoreCase(envValue);
    }
    return "true".equalsIgnoreCase(System.getProperty(SONARQUBE_DEBUG_ENABLED));
  }

  public void setNotifier(Consumer<McpSchema.LoggingMessageNotification> notifier) {
    this.notifier = Objects.requireNonNull(notifier);
  }

  public void clearNotifier() {
    this.notifier = NO_OP;
  }

  public void info(String message) {
    LOG.info(message);
    notify(McpSchema.LoggingLevel.INFO, message);
  }

  public void debug(String message) {
    if (isDebugEnabled()) {
      LOG.debug(message);
      notify(McpSchema.LoggingLevel.DEBUG, message);
    }
  }

  public void warn(String message) {
    LOG.warn(message);
    notify(McpSchema.LoggingLevel.WARNING, message);
  }

  public void error(String message, Throwable throwable) {
    LOG.error(message, throwable);
    notify(McpSchema.LoggingLevel.ERROR, message + System.lineSeparator() + stackTraceOf(throwable));
  }

  public void error(String message) {
    LOG.error(message);
    notify(McpSchema.LoggingLevel.ERROR, message);
  }

  private void notify(McpSchema.LoggingLevel level, String message) {
    try {
      notifier.accept(new McpSchema.LoggingMessageNotification(level, LOGGER_NAME, message, null));
    } catch (Exception e) {
      LOG.warn("Failed to dispatch MCP log notification", e);
    }
  }

  private static String stackTraceOf(Throwable throwable) {
    var writer = new StringWriter();
    throwable.printStackTrace(new PrintWriter(writer));
    return writer.toString();
  }

}
