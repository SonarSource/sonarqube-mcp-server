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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Utility class for reading and validating unified MCP configuration files.
 * <p>
 * Provides methods to:
 * - Read a JSON configuration file from a path
 * - Validate the configuration content for correctness
 */
public final class UnifiedConfigValidator {
  
  private UnifiedConfigValidator() {
    // Utility class
  }
  
  /**
   * Checks for:
   * - Required fields are present (name, command)
   * - No duplicate names or namespaces
   * - Environment variables are properly formatted
   */
  public static ValidationResult validateConfig(List<Map<String, Object>> configs) {
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
      if (effectiveNamespace != null && !namespaces.add(effectiveNamespace.toLowerCase(Locale.getDefault()))) {
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
    }
    
    if (errors.isEmpty()) {
      return ValidationResult.success("Configuration is valid with " + configs.size() + " server(s)");
    } else {
      return ValidationResult.failure(errors);
    }
  }

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
        ? errors.getFirst()
        : ("Found " + errors.size() + " validation errors");
      return new ValidationResult(false, message, List.copyOf(errors));
    }
  }

}
