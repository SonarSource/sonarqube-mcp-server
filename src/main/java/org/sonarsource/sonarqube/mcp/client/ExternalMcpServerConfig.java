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

import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

public record ExternalMcpServerConfig(
  String name,
  String namespace,
  String command,
  List<String> args,
  Map<String, String> env,
  Set<TransportMode> supportedTransports,
  @Nullable String instructions
) {

  public ExternalMcpServerConfig {
    if (name.isBlank()) {
      throw new IllegalArgumentException("External MCP server name cannot be null or blank");
    }
    if (namespace.isBlank()) {
      throw new IllegalArgumentException("External MCP server namespace cannot be null or blank");
    }
    if (command.isBlank()) {
      throw new IllegalArgumentException("External MCP server command cannot be null or blank");
    }
    args = List.copyOf(args);
    env = Map.copyOf(env);
    supportedTransports = Set.copyOf(supportedTransports);
    
    if (supportedTransports.isEmpty()) {
      throw new IllegalArgumentException("External MCP server must support at least one transport mode");
    }
  }

  public boolean supportsTransport(TransportMode transportMode) {
    return supportedTransports.contains(transportMode);
  }

}
