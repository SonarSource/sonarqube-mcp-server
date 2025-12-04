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
import javax.annotation.Nullable;

/**
 * Configuration for an external MCP server to connect to as a client.
 * This allows the SonarQube MCP server to integrate tools from other MCP servers.
 */
public record ExternalMcpServerConfig(
  String name,
  String namespace,
  String command,
  List<String> args,
  Map<String, String> env,
  RestartConfig restart
) {
  
  /**
   * Default restart configuration: enabled with 3 attempts and exponential backoff.
   */
  public static final RestartConfig DEFAULT_RESTART_CONFIG = new RestartConfig(true, 3, List.of(2, 5, 10));
  
  public ExternalMcpServerConfig {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("External MCP server name cannot be null or blank");
    }
    if (command == null || command.isBlank()) {
      throw new IllegalArgumentException("External MCP server command cannot be null or blank");
    }
    // Use name as namespace if not specified
    if (namespace == null || namespace.isBlank()) {
      namespace = name;
    }
    args = args != null ? List.copyOf(args) : List.of();
    env = env != null ? Map.copyOf(env) : Map.of();
    restart = restart != null ? restart : DEFAULT_RESTART_CONFIG;
  }
  
  /**
   * Convenience constructor without namespace and restart config.
   */
  public ExternalMcpServerConfig(String name, String command, List<String> args, Map<String, String> env) {
    this(name, null, command, args, env, null);
  }
  
  /**
   * Get the internal server identifier (used for internal tracking).
   */
  public String getServerId() {
    return name.toLowerCase().replaceAll("[^a-z0-9_-]", "_");
  }
  
  /**
   * Get the namespace used for tool prefixing (user-visible).
   * This is the functional prefix that appears before tool names.
   */
  public String getToolNamespace() {
    return namespace.toLowerCase().replaceAll("[^a-z0-9_-]", "_");
  }
  
  @Override
  public String toString() {
    return "ExternalMcpServerConfig{" +
      "name='" + name + '\'' +
      ", namespace='" + namespace + '\'' +
      ", command='" + command + '\'' +
      ", args=" + args +
      '}';
  }
  
  /**
   * Configuration for automatic restart behavior.
   */
  public record RestartConfig(
    boolean enabled,
    int maxAttempts,
    List<Integer> backoffSeconds
  ) {
    public RestartConfig {
      if (maxAttempts < 0) {
        throw new IllegalArgumentException("maxAttempts must be non-negative");
      }
      backoffSeconds = backoffSeconds != null ? List.copyOf(backoffSeconds) : List.of(2, 5, 10);
    }
    
    /**
     * Get the backoff duration for a given attempt (0-indexed).
     */
    public int getBackoffForAttempt(int attempt) {
      if (backoffSeconds.isEmpty()) {
        return 2; // Default 2 seconds
      }
      if (attempt >= backoffSeconds.size()) {
        return backoffSeconds.get(backoffSeconds.size() - 1);
      }
      return backoffSeconds.get(attempt);
    }
  }
}
