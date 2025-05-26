/*
 * Sonar MCP Server
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
package org.sonar.mcp.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogLevel;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;

public class McpClientLogbackAppender extends AppenderBase<ILoggingEvent> {
  private static final McpLogger LOGGER = McpLogger.getInstance();

  @Override
  protected void append(ILoggingEvent eventObject) {
    LOGGER.log(new LogParams(adapt(eventObject.getLevel()), eventObject.getMessage(), null, null, eventObject.getInstant()));
  }

  private static LogLevel adapt(Level level) {
    if (level == Level.TRACE) {
      return LogLevel.TRACE;
    }
    if (level == Level.DEBUG) {
      return LogLevel.DEBUG;
    }
    if (level == Level.INFO) {
      return LogLevel.INFO;
    }
    if (level == Level.WARN) {
      return LogLevel.WARN;
    }
    if (level == Level.ERROR) {
      return LogLevel.ERROR;
    }
    return LogLevel.DEBUG;
  }
}
