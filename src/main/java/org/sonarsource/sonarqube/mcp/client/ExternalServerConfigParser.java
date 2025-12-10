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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import org.sonarsource.sonarqube.mcp.log.McpLogger;

/**
 * Parser for external MCP server configuration.
 * Supports both JSON file path and direct JSON string configuration.
 * 
 * Configuration format:
 * <pre>
 * [
 *   {
 *     "name": "internal-id",
 *     "namespace": "user-visible-prefix",
 *     "command": "/path/to/server",
 *     "args": ["arg1", "arg2"],
 *     "env": {"KEY": "VALUE"}
 *   }
 * ]
 * </pre>
 */
public class ExternalServerConfigParser {
  
  private static final McpLogger LOG = McpLogger.getInstance();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  
  private ExternalServerConfigParser() {
    // Utility class
  }
  
  /**
   * Parse external server configuration from either a file path or JSON string.
   * 
   * @param configValue The configuration value (file path or JSON string)
   * @return List of external server configurations, or empty list if none configured
   */
  public static List<ExternalMcpServerConfig> parse(@CheckForNull String configValue) {
    if (configValue == null || configValue.isBlank()) {
      return Collections.emptyList();
    }
    
    try {
      // Try to parse as file path first
      var path = Paths.get(configValue);
      if (Files.exists(path) && Files.isRegularFile(path)) {
        return parseFromFile(path);
      }
      
      // Otherwise, try to parse as JSON string directly
      return parseFromJson(configValue);
      
    } catch (Exception e) {
      LOG.error("Failed to parse configuration: " + e.getMessage(), e);
      return Collections.emptyList();
    }
  }
  
  private static List<ExternalMcpServerConfig> parseFromFile(Path path) throws IOException {
    LOG.info("Loading configuration from file: " + path);
    var json = Files.readString(path);
    return parseFromJson(json);
  }
  
  private static List<ExternalMcpServerConfig> parseFromJson(String json) throws IOException {
    var configs = OBJECT_MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
    return configs.stream()
      .map(ExternalServerConfigParser::parseServerConfig)
      .toList();
  }
  
  @SuppressWarnings("unchecked")
  private static ExternalMcpServerConfig parseServerConfig(Map<String, Object> configMap) {
    var name = (String) configMap.get("name");
    var namespace = (String) configMap.get("namespace");
    var command = (String) configMap.get("command");
    var args = (List<String>) configMap.getOrDefault("args", Collections.emptyList());
    var env = (Map<String, String>) configMap.getOrDefault("env", Collections.emptyMap());
    
    return new ExternalMcpServerConfig(name, namespace, command, args, env);
  }
}
