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
package org.sonarsource.sonarqube.mcp.bridge;

import java.util.Map;

public class SonarQubeIdeBridgeConfiguration {
  private static final String SONARLINT_PORT_ENV = "SONARLINT_PORT";
  private static final int DEFAULT_PORT = 64121;

  private final int port;

  public SonarQubeIdeBridgeConfiguration(Map<String, String> environment) {
    this.port = parsePortValue(environment.getOrDefault(SONARLINT_PORT_ENV, String.valueOf(DEFAULT_PORT)));
  }

  private static int parsePortValue(String portStr) {
    try {
      var port = Integer.parseInt(portStr);
      if (port < 64120 || port > 64130) {
        throw new IllegalArgumentException("SonarQube for IDE port must be between 64120 and 64130, got: " + port);
      }
      return port;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid SonarQube for IDE port value: " + portStr, e);
    }
  }

  public String getBaseUrl() {
    return "http://localhost:" + port;
  }
}

