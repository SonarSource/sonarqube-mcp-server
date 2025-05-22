/*
 * Sonar MCP Server
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
package org.sonar.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SchemaToolBuilder {

  private final Map<String, Object> properties;
  private final List<String> requiredProperties;
  private String name;
  private String description;
  private boolean additionalProperties;

  public SchemaToolBuilder() {
    this.properties = new HashMap<>();
    this.requiredProperties = new ArrayList<>();
  }

  public SchemaToolBuilder setName(String name) {
    this.name = name;
    return this;
  }

  public SchemaToolBuilder setDescription(String description) {
    this.description = description;
    return this;
  }

  public SchemaToolBuilder setAdditionalProperties(boolean additionalProperties) {
    this.additionalProperties = additionalProperties;
    return this;
  }

  public SchemaToolBuilder addStringProperty(String propertyName, String description) {
    var content = Map.of("type", "string", "description", description);
    properties.put(propertyName, content);
    requiredProperties.add(propertyName);
    return this;
  }

  public SchemaToolBuilder addRequiredStringProperty(String propertyName, String description) {
    addStringProperty(propertyName, description);
    requiredProperties.add(propertyName);
    return this;
  }

  public SchemaToolBuilder addArrayProperty(String propertyName, String itemsType, String[] items, String description) {
    var content = Map.of("type", "array", "description", description, "items", Map.of(itemsType, items));
    properties.put(propertyName, content);
    return this;
  }

  public SchemaToolBuilder addRequiredArrayProperty(String propertyName, String itemsType, String[] items, String description) {
    addArrayProperty(propertyName, itemsType, items, description);
    requiredProperties.add(propertyName);
    return this;
  }

  public McpSchema.Tool build() {
    if (name == null || description == null) {
      throw new IllegalStateException("Name and description must be set before building the tool.");
    }

    if (!properties.keySet().containsAll(requiredProperties)) {
      throw new IllegalStateException("Cannot set a required property that does not exist.");
    }

    return new McpSchema.Tool(name, description, new McpSchema.JsonSchema("object", properties, requiredProperties, additionalProperties));
  }
}
