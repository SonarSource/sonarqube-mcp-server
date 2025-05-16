/*
 * Sonar MCP Server
 * Copyright (C) 2025 SonarSource
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.mcp.log;

import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.Nullable;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogLevel;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;

public class McpLogger {
  private static final McpLogger INSTANCE = new McpLogger();

  public static McpLogger getInstance() {
    return INSTANCE;
  }

  private McpSyncServer syncServer;

  public void setOutput(@Nullable McpSyncServer syncServer) {
    this.syncServer = syncServer;
  }

  public void log(LogParams params) {
    var message = params.getMessage();
    if (message != null) {
      log(message, params.getLevel());
    }
    var stackTrace = params.getStackTrace();
    if (stackTrace != null) {
      log(stackTrace, params.getLevel());
    }
  }

  public void info(String message) {
    log(message, LogLevel.INFO);
  }

  public void error(String message, Throwable throwable) {
    log(message, LogLevel.ERROR);
    log(stackTraceToString(throwable), LogLevel.ERROR);
  }

  private void log(String message, LogLevel level) {
    if (syncServer != null) {
      // We rely on a deprecated API for now, I opened a discussion in https://github.com/modelcontextprotocol/java-sdk/issues/131
      try {
        syncServer.loggingNotification(new McpSchema.LoggingMessageNotification(toMcpLevel(level), "sonar-mcp-server", message));
      } catch (Exception e) {
        // we can't do much
      }
    }
  }

  static McpSchema.LoggingLevel toMcpLevel(LogLevel level) {
    return switch (level) {
      case ERROR -> McpSchema.LoggingLevel.ERROR;
      case WARN -> McpSchema.LoggingLevel.WARNING;
      case INFO -> McpSchema.LoggingLevel.INFO;
      case DEBUG, TRACE -> McpSchema.LoggingLevel.DEBUG;
    };
  }

  static String stackTraceToString(Throwable t) {
    var stringWriter = new StringWriter();
    t.printStackTrace(new PrintWriter(stringWriter));
    return stringWriter.toString();
  }
}
