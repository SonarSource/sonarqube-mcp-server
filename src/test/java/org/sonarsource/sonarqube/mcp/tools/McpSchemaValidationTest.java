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
package org.sonarsource.sonarqube.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class McpSchemaValidationTest {

  @SonarQubeMcpServerTest
  void tool_schema_should_be_valid_according_to_mcp_spec(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient();
    var tools = client.listTools();
    
    for (var schema : tools) {
      validateMcpToolSchema(schema);
    }
  }

  // there is no constraint in the spec, but some clients may filter out some tools when the name is too long
  // e.g. for Cursor when length(server name + tool name) > 60
  // we decide 40 characters is a reasonable threshold
  @SonarQubeMcpServerTest
  void tool_name_should_be_short(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient();
    var tools = client.listTools();
    
    for (var tool : tools) {
      var toolNameLength = tool.name().length();
      assertThat(toolNameLength).as("Tool '%s' name should be short", tool.name()).isLessThanOrEqualTo(40);
    }
  }

  @SonarQubeMcpServerTest
  void tool_name_should_follow_mcp_naming_convention(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient();
    var tools = client.listTools();
    
    for (var tool : tools) {
      assertThat(tool.name())
        .as("Tool '%s' name should not be null or empty", tool.name())
        .isNotNull()
        .isNotEmpty();
      
      // Proxied tools (containing /) follow SEP-986 and may use different conventions
      if (tool.name().contains("/")) {
        // Validate proxied tools according to SEP-986
        assertThatCode(() -> ToolNameValidator.validate(tool.name()))
          .as("Proxied tool '%s' should follow MCP SEP-986 naming convention", tool.name())
          .doesNotThrowAnyException();
      } else {
        // Internal tools follow snake_case convention
        assertThat(tool.name())
          .as("Internal tool '%s' name should follow snake_case convention (lowercase letters, numbers, underscores)", tool.name())
          .matches("^[a-z][a-z0-9_]*[a-z0-9]$")
          .as("Internal tool '%s' name should not start or end with underscore", tool.name())
          .doesNotStartWith("_")
          .doesNotEndWith("_");
      }
    }
  }

  @SonarQubeMcpServerTest
  void tool_name_should_follow_telemetry_requirements(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient();
    var tools = client.listTools();
    
    for (var tool : tools) {
      // Proxied tools (containing /) may not follow telemetry snake_case requirements
      // as they follow SEP-986 which allows more characters
      if (!tool.name().contains("/")) {
        assertThat(tool.name())
          .as("Internal tool '%s' name should match the telemetry regex pattern", tool.name())
          .matches("^[a-z_][a-z0-9_]{1,126}$");
      }
    }
  }

  @SonarQubeMcpServerTest
  void tool_description_should_be_meaningful(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient();
    var tools = client.listTools();
    
    for (var tool : tools) {
      var description = tool.description();

      assertThat(description)
        .as("Tool '%s' description should not be null or empty", tool.name())
        .isNotNull()
        .isNotEmpty();
      
      assertThat(description.length())
        .as("Tool '%s' description should be meaningful (at least 10 characters)", tool.name())
        .isGreaterThanOrEqualTo(10);

      assertThat(description.toLowerCase())
        .as("Tool '%s' description should not just be the tool name", tool.name())
        .isNotEqualTo(tool.name().toLowerCase().replace("_", " "));
    }
  }

  @SonarQubeMcpServerTest
  void tool_input_schema_should_be_valid_json_schema(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient();
    var tools = client.listTools();
    
    for (var schema : tools) {
      var inputSchema = schema.inputSchema();

      assertThat(inputSchema)
        .as("Input schema should not be null for tool '%s'", schema.name())
        .isNotNull();
      
      assertThat(inputSchema.type())
        .as("Input schema type should be 'object' for MCP tool '%s'", schema.name())
        .isEqualTo("object");
      
      if (inputSchema.properties() != null) {
        validateJsonSchemaProperties(inputSchema.properties(), schema.name());
      }
      
      if (inputSchema.required() != null) {
        validateRequiredProperties(inputSchema.required(), inputSchema.properties(), schema.name());
      }
    }
  }

  @SonarQubeMcpServerTest
  void required_properties_should_exist_in_properties(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient();
    var tools = client.listTools();
    
    for (var schema : tools) {
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
  }

  @SonarQubeMcpServerTest
  void required_properties_should_not_contain_duplicates(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient();
    var tools = client.listTools();
    
    for (var schema : tools) {
      var inputSchema = schema.inputSchema();
      if (inputSchema.required() != null) {
        var requiredProperties = inputSchema.required();
        var uniqueRequiredProperties = Set.copyOf(requiredProperties);

        assertThat(uniqueRequiredProperties)
          .as("Required properties should not contain duplicates for tool '%s'", schema.name())
          .hasSize(requiredProperties.size());
      }
    }
  }

  @SonarQubeMcpServerTest
  void property_descriptions_should_be_meaningful(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient();
    var tools = client.listTools();
    
    for (var schema : tools) {
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
  }

  @SonarQubeMcpServerTest
  void enum_properties_should_have_valid_items(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient();
    var tools = client.listTools();
    
    for (var schema : tools) {
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
                  .satisfiesAnyOf(
                    ev -> assertThat(ev).isInstanceOf(Object[].class),
                    ev -> assertThat(ev).isInstanceOf(List.class)
                  );
                
                var enumList = enumValues instanceof Object[] arr ? Arrays.asList(arr) :
                              enumValues instanceof List<?> list ? list : List.of();
                assertThat(enumList)
                  .as("Enum property '%s' in tool '%s' should have at least one value", property.getKey(), schema.name())
                  .isNotEmpty();

                for (var enumValue : enumList) {
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
  }

  @SonarQubeMcpServerTest
  void tool_properties_should_have_valid_types(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient();
    var tools = client.listTools();
    
    for (var schema : tools) {
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
  }

  @SonarQubeMcpServerTest
  void all_tool_names_should_be_unique(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient();
    var allTools = client.listTools();
    var toolNames = allTools.stream()
      .map(McpSchema.Tool::name)
      .toList();
    
    var uniqueNames = Set.copyOf(toolNames);
    
    assertThat(uniqueNames)
      .as("All tool names should be unique across the MCP server")
      .hasSize(toolNames.size());
  }

  @SonarQubeMcpServerTest
  void tool_names_should_be_descriptive(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient();
    var tools = client.listTools();
    
    for (var tool : tools) {
      var toolName = tool.name();

      // For proxied tools, check the part after the namespace separator
      var nameToCheck = toolName.contains("/") ? toolName.substring(toolName.indexOf("/") + 1) : toolName;
      
      var hasActionWord = nameToCheck.contains("get") || nameToCheck.contains("search") ||
        nameToCheck.contains("change") || nameToCheck.contains("list") ||
        nameToCheck.contains("create") || nameToCheck.contains("update") ||
        nameToCheck.contains("delete") || nameToCheck.contains("show") ||
        nameToCheck.contains("analyze") || nameToCheck.contains("ping") ||
        nameToCheck.contains("toggle");

      assertThat(hasActionWord)
        .as("Tool name '%s' should contain an action word (get, search, change, list, etc.)", toolName)
        .isTrue();
      assertThat(toolName.length())
        .as("Tool name '%s' should be descriptive (at least 3 characters)", toolName)
        .isGreaterThanOrEqualTo(3);
    }
  }

  @SonarQubeMcpServerTest
  void should_automatically_discover_all_tools_from_server_configuration(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient();
    var discoveredTools = client.listTools();
    
    assertThat(discoveredTools)
      .as("Should automatically discover tools from SonarQubeMcpServer configuration")
      .isNotEmpty();
  }

  private void validateMcpToolSchema(McpSchema.Tool schema) {
    assertThat(schema.name())
      .as("Tool name should not be null or empty (MCP spec requirement)")
      .isNotNull()
      .isNotEmpty();

    assertThat(schema.title())
      .as("Tool '%s' title should not be null or empty", schema.name())
      .isNotNull()
      .isNotEmpty();

    assertThat(schema.description())
      .as("Tool '%s' description should not be null or empty (MCP spec requirement)", schema.name())
      .isNotNull()
      .isNotEmpty();
    
    assertThat(schema.inputSchema())
      .as("Tool '%s' input schema should not be null", schema.name())
      .isNotNull();
  }

  private void validateJsonSchemaProperties(Map<String, Object> properties, String toolName) {
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
