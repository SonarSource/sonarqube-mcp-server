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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.client.McpClientManager;
import org.sonarsource.sonarqube.mcp.tools.proxied.ProxiedMcpTool;
import org.sonarsource.sonarqube.mcp.transport.McpJsonMappers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ToolSchemaTest {

  @Test
  void it_should_serialize_all_builtin_property_types_in_alphabetical_key_order() throws Exception {
    var definition = tool(ToolDefinitionBuilder.builder()
      .setName("builtin")
      .setDescription("Built-in tool")
      .addRequiredStringProperty("z", "String")
      .addRequiredEnumProperty("a", new String[]{"z", "a"}, "Enum")
      .addBooleanProperty("b", "Boolean")
      .addNumberProperty("n", "Number")
      .addArrayProperty("l", "number", "Array")
      .addEnumArrayProperty("e", new String[]{"z", "a"}, "Enum array")
      .build()).definition();

    assertThat(McpJsonMappers.DEFAULT.writeValueAsString(definition.inputSchema())).isEqualTo(
      "{\"additionalProperties\":false,\"properties\":{"
        + "\"a\":{\"description\":\"Enum\",\"enum\":[\"z\",\"a\"],\"type\":\"string\"},"
        + "\"b\":{\"description\":\"Boolean\",\"type\":\"boolean\"},"
        + "\"e\":{\"description\":\"Enum array\",\"items\":{\"enum\":[\"z\",\"a\"],\"type\":\"string\"},\"type\":\"array\"},"
        + "\"l\":{\"description\":\"Array\",\"items\":{\"type\":\"number\"},\"type\":\"array\"},"
        + "\"n\":{\"description\":\"Number\",\"type\":\"number\"},"
        + "\"z\":{\"description\":\"String\",\"type\":\"string\"}},\"required\":[\"z\",\"a\"],\"type\":\"object\"}");
  }

  @Test
  void it_should_serialize_equivalent_proxied_schemas_identically_without_mutating_the_originals() throws Exception {
    var originalJson = "{\"type\":\"object\",\"required\":[\"z\",\"a\"],\"properties\":{"
      + "\"z\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"enum\":[\"z\",\"a\"]}},"
      + "\"a\":{\"default\":null,\"anyOf\":[{\"type\":\"string\",\"description\":\"Nested\"},{\"type\":\"null\"}]}},"
      + "\"examples\":[[{\"z\":1,\"a\":true},null],{},[]]}";
    var expectedJson = "{\"examples\":[[{\"a\":true,\"z\":1},null],{},[]],\"properties\":{"
      + "\"a\":{\"anyOf\":[{\"description\":\"Nested\",\"type\":\"string\"},{\"type\":\"null\"}],\"default\":null},"
      + "\"z\":{\"items\":{\"enum\":[\"z\",\"a\"],\"type\":\"string\"},\"type\":\"array\"}},\"required\":[\"z\",\"a\"],\"type\":\"object\"}";
    var original = McpJsonMappers.DEFAULT.readValue("{\"name\":\"proxy\",\"inputSchema\":" + originalJson + "}", McpSchema.Tool.class);
    var equivalent = McpJsonMappers.DEFAULT.readValue("{\"name\":\"proxy\",\"inputSchema\":" + expectedJson + "}", McpSchema.Tool.class);

    for (var source : List.of(original, equivalent)) {
      var proxy = new ProxiedMcpTool("server", "proxy", source, mock(McpClientManager.class));
      assertThat(McpJsonMappers.DEFAULT.writeValueAsString(proxy.definition().inputSchema())).isEqualTo(expectedJson);
    }

    assertThat(McpJsonMappers.DEFAULT.writeValueAsString(original.inputSchema())).isEqualTo(originalJson);
    assertThat(McpJsonMappers.DEFAULT.writeValueAsString(equivalent.inputSchema())).isEqualTo(expectedJson);
  }

  @Test
  void it_should_preserve_every_other_tool_definition_field() throws Exception {
    var outputSchema = new LinkedHashMap<String, Object>();
    outputSchema.put("z", "last");
    outputSchema.put("a", "first");
    var annotations = new McpSchema.ToolAnnotations("Annotation title", true, false, true, false, true);
    var meta = Map.<String, Object>of("meta", "value");
    var icons = List.of(new McpSchema.Icon("https://example.org/icon.png", "image/png", List.of("48x48"), "dark"));
    var original = new McpSchema.Tool("tool", "Title", "Description", Map.of("type", "object"), outputSchema, annotations, meta, icons);

    var normalized = tool(original).definition();

    assertThat(normalized).usingRecursiveComparison().ignoringFields("inputSchema").isEqualTo(original);
    assertThat(normalized.outputSchema()).isSameAs(outputSchema);
    assertThat(McpJsonMappers.DEFAULT.writeValueAsString(normalized.outputSchema())).isEqualTo("{\"z\":\"last\",\"a\":\"first\"}");
    assertThat(normalized.annotations()).isSameAs(annotations);
    assertThat(normalized.meta()).isSameAs(meta);
    assertThat(normalized.icons()).isSameAs(icons);
  }

  @Test
  void it_should_preserve_schema_order_when_removing_a_required_organization() throws Exception {
    var original = tool(ToolDefinitionBuilder.cloudBuilder()
      .setName("cloud")
      .setDescription("Cloud tool")
      .addRequiredStringProperty("z", "Last")
      .addRequiredStringProperty(ToolParameters.ORGANIZATION, "Organization")
      .addRequiredStringProperty("a", "First")
      .build()).definition();
    var originalJson = McpJsonMappers.DEFAULT.writeValueAsString(original.inputSchema());

    var filtered = ToolDefinitionBuilder.withoutOrganization(original);

    assertThat(McpJsonMappers.DEFAULT.writeValueAsString(filtered.inputSchema())).isEqualTo(
      "{\"additionalProperties\":false,\"properties\":{\"a\":{\"description\":\"First\",\"type\":\"string\"},"
        + "\"z\":{\"description\":\"Last\",\"type\":\"string\"}},\"required\":[\"z\",\"a\"],\"type\":\"object\"}");
    assertThat(McpJsonMappers.DEFAULT.writeValueAsString(original.inputSchema())).isEqualTo(originalJson);
    assertThat(ToolDefinitionBuilder.withoutOrganization(filtered)).isSameAs(filtered);
  }

  private static Tool tool(McpSchema.Tool definition) {
    return new Tool(definition, ToolCategory.PROJECTS) {
      @Override
      public Result execute(Arguments arguments) {
        throw new UnsupportedOperationException();
      }
    };
  }
}
