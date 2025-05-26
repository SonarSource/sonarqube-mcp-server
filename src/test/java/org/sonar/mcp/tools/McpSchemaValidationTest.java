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

import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sonar.mcp.SonarMcpServer;

import static org.assertj.core.api.Assertions.assertThat;

class McpSchemaValidationTest {

  private static Stream<Arguments> provideTools() {
    var testEnvironment = createTestEnvironment();
    var server = new SonarMcpServer(new StdioServerTransportProvider(), testEnvironment);
    var supportedTools = server.getSupportedTools();
    
    return supportedTools.stream()
      .map(Arguments::of);
  }

  private static Map<String, String> createTestEnvironment() {
    return Map.of(
      "SONARQUBE_CLOUD_URL", "https://foo.bar",
      "SONARQUBE_CLOUD_TOKEN", "test-token",
      "SONARQUBE_CLOUD_ORG", "test-org",
      "STORAGE_PATH", "fake"
    );
  }

  static List<Tool> getAllTools() {
    return provideTools()
      .map(args -> (Tool) args.get()[0])
      .toList();
  }

  @ParameterizedTest(name = "Tool ''{0}'' should have a valid MCP schema")
  @MethodSource("provideTools")
  void tool_schema_should_be_valid_according_to_mcp_spec(Tool tool) {
    var schema = tool.definition();
    validateMcpToolSchema(schema);
  }

  @ParameterizedTest(name = "Tool ''{0}'' should follow MCP naming convention")
  @MethodSource("provideTools")
  void tool_name_should_follow_mcp_naming_convention(Tool tool) {
    assertThat(tool.definition().name())
      .as("Tool name should not be null or empty")
      .isNotNull()
      .isNotEmpty()
      .as("Tool name should follow snake_case convention (lowercase letters, numbers, underscores)")
      .matches("^[a-z][a-z0-9_]*[a-z0-9]$")
      .as("Tool name should not start or end with underscore")
      .doesNotStartWith("_")
      .doesNotEndWith("_");

    assertThat(tool.definition().name().length())
      .as("Tool name should not be greater than 116 characters")
      .isLessThanOrEqualTo(116);
  }

  @ParameterizedTest(name = "Tool ''{0}'' should have meaningful description")
  @MethodSource("provideTools")
  void tool_description_should_be_meaningful(Tool tool) {
    var description = tool.definition().description();

    assertThat(description)
      .as("Tool description should not be null or empty")
      .isNotNull()
      .isNotEmpty();
    
    assertThat(description.length())
      .as("Tool description should be meaningful (at least 10 characters)")
      .isGreaterThanOrEqualTo(10);

    assertThat(description.toLowerCase())
      .as("Tool description should not just be the tool name")
      .isNotEqualTo(tool.definition().name().toLowerCase().replace("_", " "));
  }

  @ParameterizedTest(name = "Tool ''{0}'' should have valid JSON Schema structure")
  @MethodSource("provideTools")
  void tool_input_schema_should_be_valid_json_schema(Tool tool) {
    var schema = tool.definition();
    var inputSchema = schema.inputSchema();

    assertThat(inputSchema)
      .as("Input schema should not be null")
      .isNotNull();
    
    assertThat(inputSchema.type())
      .as("Input schema type should be 'object' for MCP tools")
      .isEqualTo("object");
    
    if (inputSchema.properties() != null) {
      validateJsonSchemaProperties(inputSchema.properties(), schema.name());
    }
    
    if (inputSchema.required() != null) {
      validateRequiredProperties(inputSchema.required(), inputSchema.properties(), schema.name());
    }
  }

  @ParameterizedTest(name = "Tool ''{0}'' required properties should exist in properties")
  @MethodSource("provideTools")
  void required_properties_should_exist_in_properties(Tool tool) {
    var schema = tool.definition();
    var inputSchema = schema.inputSchema();
    
    if (inputSchema.required() != null && inputSchema.properties() != null) {
      var propertyNames = inputSchema.properties().keySet();
      
      for (var requiredProperty : inputSchema.required()) {
        assertThat(propertyNames)
          .as("Required property '%s' should exist in properties for tool '%s'", requiredProperty, schema.name())
          .contains(requiredProperty);
      }
    }
  }

  @ParameterizedTest(name = "Tool ''{0}'' required properties should not contain duplicates")
  @MethodSource("provideTools")
  void required_properties_should_not_contain_duplicates(Tool tool) {
    var schema = tool.definition();
    var inputSchema = schema.inputSchema();
    if (inputSchema.required() != null) {
      var requiredProperties = inputSchema.required();
      var uniqueRequiredProperties = Set.copyOf(requiredProperties);

      assertThat(uniqueRequiredProperties)
        .as("Required properties should not contain duplicates for tool '%s'", schema.name())
        .hasSize(requiredProperties.size());
    }
  }

  @ParameterizedTest(name = "Tool ''{0}'' property descriptions should be meaningful")
  @MethodSource("provideTools")
  void property_descriptions_should_be_meaningful(Tool tool) {
    var schema = tool.definition();
    var inputSchema = schema.inputSchema();
    
    if (inputSchema.properties() != null) {
      for (var property : inputSchema.properties().entrySet()) {
        if (property.getValue() instanceof Map<?, ?> propertyDef) {
          var description = propertyDef.get("description");
          
          assertThat(description)
            .as("Property '%s' in tool '%s' should have a description", property.getKey(), schema.name())
            .isNotNull();
          
          if (description instanceof String descStr) {
            assertThat(descStr)
              .as("Property '%s' description in tool '%s' should not be empty", property.getKey(), schema.name())
              .isNotEmpty();
            assertThat(descStr.length())
              .as("Property '%s' description in tool '%s' should be meaningful (at least 5 characters)", property.getKey(), schema.name())
              .isGreaterThanOrEqualTo(5);
          }
        }
      }
    }
  }

  @ParameterizedTest(name = "Tool ''{0}'' enum properties should have valid items")
  @MethodSource("provideTools")
  void enum_properties_should_have_valid_items(Tool tool) {
    var schema = tool.definition();
    var inputSchema = schema.inputSchema();
    
    if (inputSchema.properties() != null) {
      for (var property : inputSchema.properties().entrySet()) {
        if (property.getValue() instanceof Map<?, ?> propertyDef) {
          var type = propertyDef.get("type");
          var items = propertyDef.get("items");
          
          if ("array".equals(type) && items instanceof Map<?, ?> itemsDef) {
            var enumValues = itemsDef.get("enum");
            if (enumValues != null) {
              assertThat(enumValues)
                .as("Enum property '%s' in tool '%s' should have enum values", property.getKey(), schema.name())
                .isInstanceOf(Object[].class);
              
              var enumArray = (Object[]) enumValues;
              assertThat(enumArray)
                .as("Enum property '%s' in tool '%s' should have at least one value", property.getKey(), schema.name())
                .isNotEmpty();
              
              // Validate enum values are strings (common case)
              for (var enumValue : enumArray) {
                assertThat(enumValue)
                  .as("Enum value in property '%s' for tool '%s' should not be null", property.getKey(), schema.name())
                  .isNotNull();
              }
            }
          }
        }
      }
    }
  }

  @ParameterizedTest(name = "Tool ''{0}'' should have valid property types")
  @MethodSource("provideTools")
  void tool_properties_should_have_valid_types(Tool tool) {
    var schema = tool.definition();
    var inputSchema = schema.inputSchema();
    
    if (inputSchema.properties() != null) {
      for (var property : inputSchema.properties().entrySet()) {
        if (property.getValue() instanceof Map<?, ?> propertyDef) {
          var type = propertyDef.get("type");
          
          assertThat(type)
            .as("Property '%s' in tool '%s' should have a type", property.getKey(), schema.name())
            .isNotNull();
          
          if (type instanceof String typeStr) {
            assertThat(typeStr)
              .as("Property '%s' type in tool '%s' should be a valid JSON Schema type", property.getKey(), schema.name())
              .isIn("string", "number", "integer", "boolean", "array", "object", "null");
          }
        }
      }
    }
  }

  @Test
  void all_tool_names_should_be_unique() {
    var allTools = getAllTools();
    var toolNames = allTools.stream()
      .map(tool -> tool.definition().name())
      .toList();
    
    var uniqueNames = Set.copyOf(toolNames);
    
    assertThat(uniqueNames)
      .as("All tool names should be unique across the MCP server")
      .hasSize(toolNames.size());
  }

  @Test
  void tool_names_should_be_descriptive() {
    var allTools = getAllTools();
    
    for (var tool : allTools) {
      var toolName = tool.definition().name();

      assertThat(toolName.length())
        .as("Tool name '%s' should be descriptive (at least 3 characters)", toolName)
        .isGreaterThanOrEqualTo(3);

      var hasActionWord = toolName.contains("get") || toolName.contains("search") || 
                         toolName.contains("change") || toolName.contains("list") ||
                         toolName.contains("create") || toolName.contains("update") ||
                         toolName.contains("delete") || toolName.contains("show");
      
      assertThat(hasActionWord)
        .as("Tool name '%s' should contain an action word (get, search, change, list, etc.)", toolName)
        .isTrue();
    }
  }

  @Test
  void should_automatically_discover_all_tools_from_server_configuration() {
    var discoveredTools = getAllTools();
    
    assertThat(discoveredTools)
      .as("Should automatically discover tools from SonarMcpServer configuration")
      .isNotEmpty();
  }

  private void validateMcpToolSchema(McpSchema.Tool schema) {
    assertThat(schema.name())
      .as("Tool name should not be null or empty (MCP spec requirement)")
      .isNotNull()
      .isNotEmpty();

    assertThat(schema.description())
      .as("Tool description should not be null or empty (MCP spec requirement)")
      .isNotNull()
      .isNotEmpty();
    
    assertThat(schema.inputSchema())
      .as("Tool input schema should not be null")
      .isNotNull();
  }

  private void validateJsonSchemaProperties(Map<String, Object> properties, String toolName) {
    assertThat(properties)
      .as("Properties should not be empty for tool '%s'", toolName)
      .isNotEmpty();
    
    for (var property : properties.entrySet()) {
      assertThat(property.getKey())
        .as("Property name should not be empty for tool '%s'", toolName)
        .isNotEmpty();
      
      assertThat(property.getValue())
        .as("Property definition should not be null for property '%s' in tool '%s'", property.getKey(), toolName)
        .isNotNull();
      
      if (property.getValue() instanceof Map<?, ?> propertyDef) {
        assertThat(propertyDef.get("type"))
          .as("Property '%s' should have a type in tool '%s'", property.getKey(), toolName)
          .isNotNull();

        assertThat(propertyDef.get("description"))
          .as("Property '%s' should have a description in tool '%s' (MCP best practice)", property.getKey(), toolName)
          .isNotNull();
      }
    }
  }

  private void validateRequiredProperties(List<String> required, @Nullable Map<String, Object> properties, String toolName) {
    if (properties == null) {
      assertThat(required)
        .as("Required properties should be empty when no properties are defined for tool '%s'", toolName)
        .isEmpty();
      return;
    }
    
    for (var requiredProperty : required) {
      assertThat(properties)
        .as("Required property '%s' should exist in properties for tool '%s'", requiredProperty, toolName)
        .containsKey(requiredProperty);
    }
  }

} 
