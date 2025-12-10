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
import org.jetbrains.annotations.NotNull;

/**
 * Configuration for an external MCP server to connect to as a client.
 * This allows the SonarQube MCP server to integrate tools from other MCP servers.
 */
public record ExternalMcpServerConfig(
  String name,
  String namespace,
  String command,
  List<String> args,
  Map<String, String> env
) {
  
  public ExternalMcpServerConfig {
    if (name.isBlank()) {
      throw new IllegalArgumentException("External MCP server name cannot be null or blank");
    }
    if (command.isBlank()) {
      throw new IllegalArgumentException("External MCP server command cannot be null or blank");
    }
    // Use name as namespace if not specified
    if (namespace.isBlank()) {
      namespace = name;
    }
    args = List.copyOf(args);
    env = Map.copyOf(env);
  }
  
  /**
   * Convenience constructor without namespace.
   */
  public ExternalMcpServerConfig(String name, String command, List<String> args, Map<String, String> env) {
    this(name, "", command, args, env);
  }

  public String getServerId() {
    return name.toLowerCase().replaceAll("[^a-z0-9_-]", "_");
  }

  public String getToolNamespace() {
    return namespace.toLowerCase().replaceAll("[^a-z0-9_-]", "_");
  }
  
  @Override
  public @NotNull String toString() {
    return "ExternalMcpServerConfig{" +
      "name='" + name + '\'' +
      ", namespace='" + namespace + '\'' +
      ", command='" + command + '\'' +
      ", args=" + args +
      '}';
  }

}
