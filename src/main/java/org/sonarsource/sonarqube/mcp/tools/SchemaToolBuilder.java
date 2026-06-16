/*
 * SonarQube MCP Server
 * Copyright (C) SonarSource
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
package org.sonarsource.sonarqube.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.annotation.Nullable;

public class SchemaToolBuilder {

  private static final String DESCRIPTION_KEY_NAME = "description";
  private static final String TYPE_PROPERTY_NAME = "type";
  private static final String ITEMS_PROPERTY_NAME = "items";

  private static final String ARRAY_TYPE = "array";
  private static final String BOOLEAN_TYPE = "boolean";
  private static final String NUMBER_TYPE = "number";
  private static final String OBJECT_TYPE = "object";
  private static final String STRING_TYPE = "string";
  private static final String PROJECT_KEY_PROPERTY_DESCRIPTION =
    "The SonarQube project key (e.g. my_project). Use search_my_sonarqube_projects when the key is unknown.";
  private static final String OPTIONAL_PROJECT_KEY_WITH_DEFAULT_DESCRIPTION =
    "Optional SonarQube project key (e.g. my_project). Defaults to the configured project when omitted. "
      + "Use search_my_sonarqube_projects when the key is unknown.";
  private static final String OPTIONAL_PROJECT_KEY_WITHOUT_DEFAULT_DESCRIPTION =
    "Optional SonarQube project key (e.g. my_project). Uses organization default quality profiles when omitted. "
      + "Use search_my_sonarqube_projects when the key is unknown.";

  private final Map<String, Object> properties;
  private final List<String> requiredProperties;
  private final Map<String, Object> outputSchemaFromClass;
  private String name;
  private String title;
  private String description;
  private boolean isReadOnly;

  public SchemaToolBuilder(Map<String, Object> outputSchemaFromClass) {
    this.properties = new HashMap<>();
    this.requiredProperties = new ArrayList<>();
    this.outputSchemaFromClass = outputSchemaFromClass;
  }

  /**
   * Factory method to create a SchemaToolBuilder with automatic output schema generation from a class.
   * This is the recommended approach for defining structured output.
   */
  public static SchemaToolBuilder forOutput(Class<? extends Record> outputClass) {
    return new SchemaToolBuilder(SchemaUtils.generateOutputSchema(outputClass));
  }

  public SchemaToolBuilder setName(String name) {
    this.name = name;
    return this;
  }

  public SchemaToolBuilder setTitle(String title) {
    this.title = title;
    return this;
  }

  public SchemaToolBuilder setDescription(String description) {
    this.description = description;
    return this;
  }

  public SchemaToolBuilder addStringProperty(String propertyName, String description) {
    var content = Map.of(TYPE_PROPERTY_NAME, STRING_TYPE, DESCRIPTION_KEY_NAME, description);
    properties.put(propertyName, content);
    return this;
  }

  public SchemaToolBuilder addBranchAndPullRequestProperties() {
    return addStringProperty(BranchPullRequestContext.BRANCH_PROPERTY, BranchPullRequestContext.BRANCH_PROPERTY_DESCRIPTION)
      .addStringProperty(BranchPullRequestContext.PULL_REQUEST_PROPERTY, BranchPullRequestContext.PULL_REQUEST_PROPERTY_DESCRIPTION);
  }

  public SchemaToolBuilder addRequiredStringProperty(String propertyName, String description) {
    addStringProperty(propertyName, description);
    requiredProperties.add(propertyName);
    return this;
  }

  public SchemaToolBuilder addBooleanProperty(String propertyName, String description) {
    var content = Map.of(TYPE_PROPERTY_NAME, BOOLEAN_TYPE, DESCRIPTION_KEY_NAME, description);
    properties.put(propertyName, content);
    return this;
  }

  public SchemaToolBuilder addNumberProperty(String propertyName, String description) {
    var content = Map.of(TYPE_PROPERTY_NAME, NUMBER_TYPE, DESCRIPTION_KEY_NAME, description);
    properties.put(propertyName, content);
    return this;
  }

  public SchemaToolBuilder addArrayProperty(String propertyName, String itemsType, String description) {
    var content = Map.of(TYPE_PROPERTY_NAME, ARRAY_TYPE, DESCRIPTION_KEY_NAME, description, ITEMS_PROPERTY_NAME, Map.of(TYPE_PROPERTY_NAME, itemsType));
    properties.put(propertyName, content);
    return this;
  }

  public SchemaToolBuilder addEnumProperty(String propertyName, String[] items, String description) {
    var content = new HashMap<String, Object>();
    content.put(TYPE_PROPERTY_NAME, STRING_TYPE);
    content.put(DESCRIPTION_KEY_NAME, description);
    content.put("enum", List.of(items));
    properties.put(propertyName, content);
    return this;
  }

  public SchemaToolBuilder addEnumArrayProperty(String propertyName, String[] items, String description) {
    var content = new HashMap<String, Object>();
    content.put(TYPE_PROPERTY_NAME, ARRAY_TYPE);
    content.put(DESCRIPTION_KEY_NAME, description);
    content.put(ITEMS_PROPERTY_NAME, Map.of(TYPE_PROPERTY_NAME, STRING_TYPE, "enum", List.of(items)));
    properties.put(propertyName, content);
    return this;
  }

  public SchemaToolBuilder addRequiredEnumProperty(String propertyName, String[] items, String description) {
    addEnumProperty(propertyName, items, description);
    requiredProperties.add(propertyName);
    return this;
  }

  /**
   * Adds a project key property only when no default project key is configured.
   * When {@code configuredProjectKey} is non-null, the property is omitted from the schema
   * entirely — the configured default is used automatically at runtime.
   * When {@code null}, the property is added as a required parameter.
   */
  public SchemaToolBuilder addProjectKeyProperty(String propertyName, @Nullable String configuredProjectKey) {
    if (configuredProjectKey != null) {
      return this;
    }
    return addRequiredStringProperty(propertyName, PROJECT_KEY_PROPERTY_DESCRIPTION);
  }

  /**
   * Adds an optional project key property, always exposed in the schema.
   * When omitted at call time, a configured default may be applied at runtime; otherwise analysis
   * may proceed without a project-specific quality profile.
   */
  public SchemaToolBuilder addOptionalProjectKeyProperty(String propertyName, @Nullable String configuredProjectKey) {
    var projectKeyDescription = configuredProjectKey != null
      ? OPTIONAL_PROJECT_KEY_WITH_DEFAULT_DESCRIPTION
      : OPTIONAL_PROJECT_KEY_WITHOUT_DEFAULT_DESCRIPTION;
    return addStringProperty(propertyName, projectKeyDescription);
  }

  /**
   * Marks this tool as read-only, indicating it only reads data and doesn't modify any state.
   */
  public SchemaToolBuilder setReadOnlyHint() {
    this.isReadOnly = true;
    return this;
  }

  public McpSchema.Tool build() {
    if (name == null || description == null) {
      throw new IllegalStateException("Name and description must be set before building the tool.");
    }

    if (!properties.keySet().containsAll(requiredProperties)) {
      throw new IllegalStateException("Cannot set a required property that does not exist.");
    }

    var inputSchema = new HashMap<String, Object>();
    inputSchema.put(TYPE_PROPERTY_NAME, OBJECT_TYPE);
    inputSchema.put("properties", properties);
    inputSchema.put("required", List.copyOf(requiredProperties));
    inputSchema.put("additionalProperties", false);

    var toolAnnotations = new McpSchema.ToolAnnotations(
      null,
      isReadOnly,
      false,
      false,
      true,
      null);

    return McpSchema.Tool.builder(name, inputSchema)
      .title(title)
      .description(description)
      .outputSchema(outputSchemaFromClass)
      .annotations(toolAnnotations)
      .build();
  }
}
