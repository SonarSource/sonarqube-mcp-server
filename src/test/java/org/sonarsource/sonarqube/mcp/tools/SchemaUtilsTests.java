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

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaUtilsTests {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private void assertSchemaEquals(Map<String, Object> actualSchema, String expectedJson) {
    try {
      var expected = MAPPER.readValue(expectedJson, Map.class);
      var actual = MAPPER.readValue(MAPPER.writeValueAsString(actualSchema), Map.class);
      assertThat(actual).isEqualTo(expected);
    } catch (Exception e) {
      throw new RuntimeException("Failed to compare JSON schemas", e);
    }
  }

  // Test records
  public record SimpleRecord(
    @Description("A string field") String name,
    @Description("An integer field") int age,
    @Description("A boolean field") boolean active
  ) {}

  public record RecordWithNullable(
    @Description("Required string field") String requiredField,
    @Description("Optional string field") @Nullable String optionalField,
    @Description("Required number") int requiredNumber,
    @Description("Optional number") @Nullable Integer optionalNumber
  ) {}

  public record NestedRecord(
    @Description("Parent name") String parentName,
    @Description("Child record") SimpleRecord child
  ) {}

  public record RecordWithList(
    @Description("List of strings") List<String> names,
    @Description("List of numbers") List<Integer> numbers,
    @Description("List of nested records") List<SimpleRecord> records
  ) {}

  public record RecordWithAllTypes(
    @Description("String type") String stringField,
    @Description("int primitive") int intField,
    @Description("Integer wrapper") Integer integerField,
    @Description("long primitive") long longField,
    @Description("Long wrapper") Long longWrapperField,
    @Description("double primitive") double doubleField,
    @Description("Double wrapper") Double doubleWrapperField,
    @Description("boolean primitive") boolean booleanField,
    @Description("Boolean wrapper") Boolean booleanWrapperField
  ) {}

  @Test
  void it_should_generate_schema_for_simple_record() {
    var schema = SchemaUtils.generateOutputSchema(SimpleRecord.class);

    assertSchemaEquals(schema, """
      {
        "type": "object",
        "properties": {
          "name": {
            "type": "string",
            "description": "A string field"
          },
          "age": {
            "type": "number",
            "description": "An integer field"
          },
          "active": {
            "type": "boolean",
            "description": "A boolean field"
          }
        },
        "required": ["name", "age", "active"]
      }""");
  }

  @Test
  void it_should_handle_nullable_fields() {
    var schema = SchemaUtils.generateOutputSchema(RecordWithNullable.class);

    assertSchemaEquals(schema, """
      {
        "type": "object",
        "properties": {
          "requiredField": {
            "type": "string",
            "description": "Required string field"
          },
          "optionalField": {
            "type": "string",
            "description": "Optional string field"
          },
          "requiredNumber": {
            "type": "number",
            "description": "Required number"
          },
          "optionalNumber": {
            "type": "number",
            "description": "Optional number"
          }
        },
        "required": ["requiredField", "requiredNumber"]
      }""");
  }

  @Test
  void it_should_generate_schema_for_nested_record() {
    var schema = SchemaUtils.generateOutputSchema(NestedRecord.class);

    assertSchemaEquals(schema, """
      {
        "type": "object",
        "properties": {
          "parentName": {
            "type": "string",
            "description": "Parent name"
          },
          "child": {
            "type": "object",
            "description": "Child record",
            "properties": {
              "name": {
                "type": "string",
                "description": "A string field"
              },
              "age": {
                "type": "number",
                "description": "An integer field"
              },
              "active": {
                "type": "boolean",
                "description": "A boolean field"
              }
            },
            "required": ["name", "age", "active"]
          }
        },
        "required": ["parentName", "child"]
      }""");
  }

  @Test
  void it_should_generate_schema_for_list_of_primitives() {
    var schema = SchemaUtils.generateOutputSchema(RecordWithList.class);

    assertSchemaEquals(schema, """
      {
        "type": "object",
        "properties": {
          "names": {
            "type": "array",
            "description": "List of strings",
            "items": {
              "type": "string"
            }
          },
          "numbers": {
            "type": "array",
            "description": "List of numbers",
            "items": {
              "type": "number"
            }
          },
          "records": {
            "type": "array",
            "description": "List of nested records",
            "items": {
              "type": "object",
              "properties": {
                "name": {
                  "type": "string",
                  "description": "A string field"
                },
                "age": {
                  "type": "number",
                  "description": "An integer field"
                },
                "active": {
                  "type": "boolean",
                  "description": "A boolean field"
                }
              },
              "required": ["name", "age", "active"]
            }
          }
        },
        "required": ["names", "numbers", "records"]
      }""");
  }

  @Test
  void it_should_handle_all_supported_types() {
    var schema = SchemaUtils.generateOutputSchema(RecordWithAllTypes.class);

    assertSchemaEquals(schema, """
      {
        "type": "object",
        "properties": {
          "stringField": {
            "type": "string",
            "description": "String type"
          },
          "intField": {
            "type": "number",
            "description": "int primitive"
          },
          "integerField": {
            "type": "number",
            "description": "Integer wrapper"
          },
          "longField": {
            "type": "number",
            "description": "long primitive"
          },
          "longWrapperField": {
            "type": "number",
            "description": "Long wrapper"
          },
          "doubleField": {
            "type": "number",
            "description": "double primitive"
          },
          "doubleWrapperField": {
            "type": "number",
            "description": "Double wrapper"
          },
          "booleanField": {
            "type": "boolean",
            "description": "boolean primitive"
          },
          "booleanWrapperField": {
            "type": "boolean",
            "description": "Boolean wrapper"
          }
        },
        "required": ["stringField", "intField", "integerField", "longField", "longWrapperField", "doubleField", "doubleWrapperField", "booleanField", "booleanWrapperField"]
      }""");
  }

  @Test
  void it_should_generate_description_from_field_name_when_annotation_missing() {
    record NoDescriptionRecord(String userName, int userAge) {}

    var schema = SchemaUtils.generateOutputSchema(NoDescriptionRecord.class);

    assertSchemaEquals(schema, """
      {
        "type": "object",
        "properties": {
          "userName": {
            "type": "string",
            "description": "user name"
          },
          "userAge": {
            "type": "number",
            "description": "user age"
          }
        },
        "required": ["userName", "userAge"]
      }""");
  }

  @Test
  void it_should_serialize_record_to_structured_content() {
    var output = new SimpleRecord("John Doe", 30, true);
    var content = SchemaUtils.toStructuredContent(output);

    assertThat(content)
      .containsEntry("name", "John Doe")
      .containsEntry("age", 30)
      .containsEntry("active", true);
  }

  @Test
  void it_should_exclude_null_fields_from_structured_content() {
    var output = new RecordWithNullable("required", null, 42, null);
    var content = SchemaUtils.toStructuredContent(output);

    assertThat(content)
      .containsEntry("requiredField", "required")
      .containsEntry("requiredNumber", 42)
      .doesNotContainKey("optionalField")
      .doesNotContainKey("optionalNumber");
  }

  @Test
  void it_should_serialize_nested_record_to_structured_content() {
    var child = new SimpleRecord("Child Name", 10, false);
    var parent = new NestedRecord("Parent Name", child);
    var content = SchemaUtils.toStructuredContent(parent);

    assertThat(content).containsEntry("parentName", "Parent Name");

    @SuppressWarnings("unchecked")
    var childContent = (Map<String, Object>) content.get("child");
    assertThat(childContent)
      .containsEntry("name", "Child Name")
      .containsEntry("age", 10)
      .containsEntry("active", false);
  }

  @Test
  void it_should_serialize_record_with_list_to_structured_content() {
    var output = new RecordWithList(
      List.of("Alice", "Bob"),
      List.of(1, 2, 3),
      List.of(new SimpleRecord("Test", 25, true))
    );
    var content = SchemaUtils.toStructuredContent(output);

    @SuppressWarnings("unchecked")
    var names = (List<String>) content.get("names");
    assertThat(names).containsExactly("Alice", "Bob");

    @SuppressWarnings("unchecked")
    var numbers = (List<Integer>) content.get("numbers");
    assertThat(numbers).containsExactly(1, 2, 3);

    @SuppressWarnings("unchecked")
    var records = (List<Map<String, Object>>) content.get("records");
    assertThat(records).hasSize(1);
    assertThat(records.getFirst())
      .containsEntry("name", "Test")
      .containsEntry("age", 25)
      .containsEntry("active", true);
  }

  @Test
  void it_should_serialize_record_to_json_string() {
    var output = new SimpleRecord("John Doe", 30, true);
    var json = SchemaUtils.toJsonString(output);

    assertThat(json)
      .contains("\"name\" : \"John Doe\"")
      .contains("\"age\" : 30")
      .contains("\"active\" : true");
  }

  @Test
  void it_should_serialize_record_to_pretty_json() {
    var output = new SimpleRecord("Test", 1, false);
    var json = SchemaUtils.toJsonString(output);

    // Pretty print should have newlines and indentation
    assertThat(json)
      .contains("\n")
      .matches("(?s)\\{\\s+\"name\".*");
  }

  @Test
  void it_should_exclude_null_fields_from_json_string() {
    var output = new RecordWithNullable("required", null, 42, null);
    var json = SchemaUtils.toJsonString(output);

    assertThat(json)
      .contains("\"requiredField\" : \"required\"")
      .contains("\"requiredNumber\" : 42")
      .doesNotContain("optionalField")
      .doesNotContain("optionalNumber");
  }

  @Test
  void it_should_serialize_nested_record_to_json_string() {
    var child = new SimpleRecord("Child", 5, true);
    var parent = new NestedRecord("Parent", child);
    var json = SchemaUtils.toJsonString(parent);

    assertThat(json)
      .contains("\"parentName\" : \"Parent\"")
      .contains("\"child\" : {")
      .contains("\"name\" : \"Child\"")
      .contains("\"age\" : 5")
      .contains("\"active\" : true");
  }

  @Test
  void it_should_handle_complex_nested_structure() {
    record ComplexRecord(
      @Description("Top level field") String topLevel,
      @Description("Nested with list") RecordWithList nestedWithList,
      @Description("Simple list") List<String> simpleList
    ) {}

    var complex = new ComplexRecord(
      "top",
      new RecordWithList(
        List.of("a", "b"),
        List.of(1, 2),
        List.of(new SimpleRecord("nested", 99, false))
      ),
      List.of("x", "y")
    );

    // Test schema generation
    var schema = SchemaUtils.generateOutputSchema(ComplexRecord.class);
    @SuppressWarnings("unchecked")
    var properties = (Map<String, Object>) schema.get("properties");
    assertThat(properties).hasSize(3);

    // Test serialization
    var content = SchemaUtils.toStructuredContent(complex);
    assertThat(content)
      .containsEntry("topLevel", "top")
      .containsKey("nestedWithList");

    // Test JSON serialization
    var json = SchemaUtils.toJsonString(complex);
    assertThat(json).contains("\"topLevel\" : \"top\"");
  }

  @Test
  void it_should_handle_empty_lists() {
    var output = new RecordWithList(List.of(), List.of(), List.of());
    var content = SchemaUtils.toStructuredContent(output);

    var names = (List<?>) content.get("names");
    assertThat(names).isEmpty();

    var numbers = (List<?>) content.get("numbers");
    assertThat(numbers).isEmpty();

    var records = (List<?>) content.get("records");
    assertThat(records).isEmpty();
  }

}
