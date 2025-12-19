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
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.log.McpLogger;

public class ExternalServerConfigParser {
  
  private static final McpLogger LOG = McpLogger.getInstance();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String DEFAULT_CONFIG_RESOURCE = "/external-tool-providers.json";
  
  private ExternalServerConfigParser() {
    // Utility class
  }

  /**
   * Parse external tool provider configuration from the bundled configuration file.
   * The configuration file must be located at /external-tool-providers.json in the classpath (bundled in JAR).
   */
  public static ParseResult parse() {
    try (InputStream inputStream = ExternalServerConfigParser.class.getResourceAsStream(DEFAULT_CONFIG_RESOURCE)) {
      if (inputStream == null) {
        LOG.info("No external tool providers configuration found at " + DEFAULT_CONFIG_RESOURCE);
        return ParseResult.success(Collections.emptyList());
      }
      
      LOG.info("Loading external tool providers configuration from bundled resource: " + DEFAULT_CONFIG_RESOURCE);
      var json = new String(inputStream.readAllBytes());
      return parseAndValidateJson(json, "classpath:" + DEFAULT_CONFIG_RESOURCE);
    } catch (IOException e) {
      LOG.error("Failed to load external tool providers configuration: " + e.getMessage(), e);
      return ParseResult.failure("Failed to load bundled configuration: " + e.getMessage());
    }
  }
  
  private static ParseResult parseAndValidateJson(String json, String source) {
    try {
      // Parse JSON into raw maps first
      var rawConfigs = OBJECT_MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
      
      // Validate the raw configuration
      var validationResult = UnifiedConfigValidator.validateConfig(rawConfigs);
      if (!validationResult.isValid()) {
        LOG.warn("External tool providers configuration from " + source + " failed validation:");
        for (String error : validationResult.errors()) {
          LOG.warn("  - " + error);
        }
        return ParseResult.failure("Configuration validation failed: " + validationResult.message());
      }
      
      // Parse into strongly-typed objects
      var configs = rawConfigs.stream()
        .map(ExternalServerConfigParser::parseServerConfig)
        .toList();
      
      LOG.info("Successfully loaded and validated " + configs.size() + " external tool provider(s) from " + source);
      return ParseResult.success(configs);
    } catch (IOException e) {
      var error = "Failed to parse JSON from " + source + ": " + e.getMessage();
      LOG.error(error, e);
      return ParseResult.failure(error);
    } catch (IllegalArgumentException e) {
      // Thrown by ExternalMcpServerConfig constructor if validation fails
      var error = "Invalid configuration in " + source + ": " + e.getMessage();
      LOG.error(error, e);
      return ParseResult.failure(error);
    }
  }
  
  @SuppressWarnings("unchecked")
  private static ExternalMcpServerConfig parseServerConfig(Map<String, Object> configMap) {
    var name = (String) configMap.get("name");
    var namespace = (String) configMap.get("namespace");
    var command = (String) configMap.get("command");
    var args = (List<String>) configMap.getOrDefault("args", Collections.emptyList());
    var env = (Map<String, String>) configMap.getOrDefault("env", Collections.emptyMap());
    
    // Use name as namespace if not specified in JSON (for backward compatibility)
    if (namespace == null || namespace.isBlank()) {
      namespace = name;
    }
    
    return new ExternalMcpServerConfig(name, namespace, command, args, env);
  }

  public record ParseResult(boolean success, List<ExternalMcpServerConfig> configs, @Nullable String error) {
    public static ParseResult success(List<ExternalMcpServerConfig> configs) {
      return new ParseResult(true, configs, null);
    }
    
    public static ParseResult failure(String error) {
      return new ParseResult(false, Collections.emptyList(), error);
    }
  }

}
