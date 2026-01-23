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
package org.sonarsource.sonarqube.mcp.client;

import java.util.Locale;

public enum TransportMode {

  STDIO,
  HTTP;

  public static TransportMode fromString(String value) {
    if (value.isBlank()) {
      throw new IllegalArgumentException("Transport mode cannot be null or blank");
    }
    
    var normalized = value.trim().toLowerCase(Locale.getDefault());
    return switch (normalized) {
      case "stdio" -> STDIO;
      case "http" -> HTTP;
      default -> throw new IllegalArgumentException("Invalid transport mode: '" + value + "'. Valid values are: stdio, http");
    };
  }

  public String toConfigString() {
    return name().toLowerCase(Locale.getDefault());
  }

}
