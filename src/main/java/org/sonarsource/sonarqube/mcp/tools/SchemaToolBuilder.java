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
package org.sonarsource.sonarqube.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SchemaToolBuilder {

  private static final String DESCRIPTION_KEY_NAME = "description";
  private static final String TYPE_PROPERTY_NAME = "type";
  private static final String ITEMS_PROPERTY_NAME = "items";
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

  public SchemaToolBuilder addIntegerProperty(String propertyName, String description) {
    var content = Map.of(TYPE_PROPERTY_NAME, "number", DESCRIPTION_KEY_NAME, description);
    properties.put(propertyName, content);
    return this;
  }

  public SchemaToolBuilder addStringProperty(String propertyName, String description) {
    var content = Map.of(TYPE_PROPERTY_NAME, "string", DESCRIPTION_KEY_NAME, description);
    properties.put(propertyName, content);
    return this;
  }

  public SchemaToolBuilder addRequiredStringProperty(String propertyName, String description) {
    addStringProperty(propertyName, description);
    requiredProperties.add(propertyName);
    return this;
  }

  public SchemaToolBuilder addArrayProperty(String propertyName, String itemsType, String description) {
    var content = Map.of(TYPE_PROPERTY_NAME, "array", DESCRIPTION_KEY_NAME, description, ITEMS_PROPERTY_NAME, Map.of(TYPE_PROPERTY_NAME, itemsType));
    properties.put(propertyName, content);
    return this;
  }

  public SchemaToolBuilder addEnumProperty(String propertyName, String[] items, String description) {
    var content = Map.of(TYPE_PROPERTY_NAME, "array", DESCRIPTION_KEY_NAME, description, ITEMS_PROPERTY_NAME, Map.of("enum", items));
    properties.put(propertyName, content);
    return this;
  }

  public SchemaToolBuilder addRequiredEnumProperty(String propertyName, String[] items, String description) {
    addEnumProperty(propertyName, items, description);
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
