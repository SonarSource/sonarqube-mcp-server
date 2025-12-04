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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.log.McpLogger;

/**
 * Utility class for reading and validating unified MCP configuration files.
 * 
 * Provides methods to:
 * - Read a JSON configuration file from a path
 * - Validate the configuration content for correctness
 */
public final class UnifiedConfigValidator {
  
  private static final McpLogger LOG = McpLogger.getInstance();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  
  private UnifiedConfigValidator() {
    // Utility class
  }
  
  /**
   * Read a JSON configuration file from the given path.
   * 
   * @param path The path to the JSON configuration file
   * @return List of configuration maps parsed from the JSON file
   * @throws IOException if the file cannot be read or is not valid JSON
   */
  public static List<Map<String, Object>> readConfigFromPath(Path path) throws IOException {
    if (path == null) {
      throw new IllegalArgumentException("Configuration path cannot be null");
    }
    
    if (!Files.exists(path)) {
      throw new IOException("Configuration file does not exist: " + path);
    }
    
    if (!Files.isRegularFile(path)) {
      throw new IOException("Configuration path is not a regular file: " + path);
    }
    
    LOG.info("Reading configuration from: " + path);
    var json = Files.readString(path);
    
    return OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
  }
  
  /**
   * Validate that the unified configuration content is valid.
   * 
   * Checks for:
   * - Required fields are present (name, command)
   * - No duplicate names or namespaces
   * - Restart configuration is valid if present
   * - Environment variables are properly formatted
   * 
   * @param configs The list of configuration maps to validate
   * @return ValidationResult containing success status and any error messages
   */
  public static ValidationResult validateConfig(List<Map<String, Object>> configs) {
    if (configs == null) {
      return ValidationResult.failure("Configuration cannot be null");
    }
    
    if (configs.isEmpty()) {
      return ValidationResult.success("Configuration is empty but valid");
    }
    
    var errors = new ArrayList<String>();
    var names = new HashSet<String>();
    var namespaces = new HashSet<String>();
    
    for (int i = 0; i < configs.size(); i++) {
      var config = configs.get(i);
      var prefix = "Config[" + i + "]: ";
      
      // Validate required fields
      var name = (String) config.get("name");
      if (name == null || name.isBlank()) {
        errors.add(prefix + "Missing required field 'name'");
      } else {
        if (!names.add(name)) {
          errors.add(prefix + "Duplicate name '" + name + "'");
        }
      }
      
      var command = (String) config.get("command");
      if (command == null || command.isBlank()) {
        errors.add(prefix + "Missing required field 'command'");
      }
      
      // Validate namespace uniqueness
      var namespace = (String) config.get("namespace");
      var effectiveNamespace = (namespace != null && !namespace.isBlank()) ? namespace : name;
      if (effectiveNamespace != null && !namespaces.add(effectiveNamespace.toLowerCase())) {
        errors.add(prefix + "Duplicate namespace '" + effectiveNamespace + "'");
      }
      
      // Validate args if present
      var args = config.get("args");
      if (args != null && !(args instanceof List)) {
        errors.add(prefix + "Field 'args' must be an array");
      }
      
      // Validate env if present
      var env = config.get("env");
      if (env != null && !(env instanceof Map)) {
        errors.add(prefix + "Field 'env' must be an object");
      }
      
      // Validate restart config if present
      validateRestartConfig(config.get("restart"), prefix, errors);
    }
    
    if (errors.isEmpty()) {
      return ValidationResult.success("Configuration is valid with " + configs.size() + " server(s)");
    } else {
      return ValidationResult.failure(errors);
    }
  }
  
  @SuppressWarnings("unchecked")
  private static void validateRestartConfig(Object restartObj, String prefix, List<String> errors) {
    if (restartObj == null) {
      return; // Optional field
    }
    
    if (!(restartObj instanceof Map)) {
      errors.add(prefix + "Field 'restart' must be an object");
      return;
    }
    
    var restart = (Map<String, Object>) restartObj;
    
    // Validate enabled field
    var enabled = restart.get("enabled");
    if (enabled != null && !(enabled instanceof Boolean)) {
      errors.add(prefix + "Field 'restart.enabled' must be a boolean");
    }
    
    // Validate maxAttempts field
    var maxAttempts = restart.get("maxAttempts");
    if (maxAttempts != null) {
      if (!(maxAttempts instanceof Number)) {
        errors.add(prefix + "Field 'restart.maxAttempts' must be a number");
      } else if (((Number) maxAttempts).intValue() < 0) {
        errors.add(prefix + "Field 'restart.maxAttempts' must be non-negative");
      }
    }
    
    // Validate backoffSeconds field
    var backoffSeconds = restart.get("backoffSeconds");
    if (backoffSeconds != null) {
      if (!(backoffSeconds instanceof List)) {
        errors.add(prefix + "Field 'restart.backoffSeconds' must be an array");
      } else {
        var backoffList = (List<?>) backoffSeconds;
        for (int j = 0; j < backoffList.size(); j++) {
          var item = backoffList.get(j);
          if (!(item instanceof Number)) {
            errors.add(prefix + "Field 'restart.backoffSeconds[" + j + "]' must be a number");
          } else if (((Number) item).intValue() < 0) {
            errors.add(prefix + "Field 'restart.backoffSeconds[" + j + "]' must be non-negative");
          }
        }
      }
    }
  }
  
  /**
   * Result of configuration validation.
   */
  public record ValidationResult(
    boolean isValid,
    String message,
    List<String> errors
  ) {
    public static ValidationResult success(String message) {
      return new ValidationResult(true, message, List.of());
    }
    
    public static ValidationResult failure(String error) {
      return new ValidationResult(false, error, List.of(error));
    }
    
    public static ValidationResult failure(List<String> errors) {
      var message = errors.size() == 1 
        ? errors.get(0) 
        : "Found " + errors.size() + " validation errors";
      return new ValidationResult(false, message, List.copyOf(errors));
    }
  }
}

