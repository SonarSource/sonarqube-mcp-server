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

import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogLevel;
import org.sonarsource.sonarlint.core.rpc.protocol.client.log.LogParams;

public class McpLogger {
  private static final McpLogger INSTANCE = new McpLogger();

  public static McpLogger getInstance() {
    return INSTANCE;
  }

  public void log(LogParams params) {
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
    throwable.printStackTrace();
  }

  private static void log(String message, LogLevel level) {
    // will be properly implemented in SLCORE-1345
    System.out.println("[" + level.name() + "] " + message);
  }
}
