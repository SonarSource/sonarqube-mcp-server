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

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
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
    @JsonPropertyDescription("A string field") String name,
    @JsonPropertyDescription("An integer field") int age,
    @JsonPropertyDescription("A boolean field") boolean active
  ) {}

  public record RecordWithNullable(
    @JsonPropertyDescription("Required string field") String requiredField,
    @JsonPropertyDescription("Optional string field") @Nullable String optionalField,
    @JsonPropertyDescription("Required number") int requiredNumber,
    @JsonPropertyDescription("Optional number") @Nullable Integer optionalNumber
  ) {}

  public record NestedRecord(
    @JsonPropertyDescription("Parent name") String parentName,
    @JsonPropertyDescription("Child record") SimpleRecord child
  ) {}

  public record RecordWithList(
    @JsonPropertyDescription("List of strings") List<String> names,
    @JsonPropertyDescription("List of numbers") List<Integer> numbers,
    @JsonPropertyDescription("List of nested records") List<SimpleRecord> records
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
            "type": "integer",
            "description": "An integer field"
          },
          "active": {
            "type": "boolean",
            "description": "A boolean field"
          }
        },
        "required": ["active", "age", "name"]
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
            "type": "integer",
            "description": "Required number"
          },
          "optionalNumber": {
            "type": "integer",
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
                "type": "integer",
                "description": "An integer field"
              },
              "active": {
                "type": "boolean",
                "description": "A boolean field"
              }
            },
            "required": ["active", "age", "name"]
          }
        },
        "required": ["child", "parentName"]
      }""");
  }

  @Test
  void it_should_generate_schema_for_lists() {
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
              "type": "integer"
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
                  "type": "integer",
                  "description": "An integer field"
                },
                "active": {
                  "type": "boolean",
                  "description": "A boolean field"
                }
              },
              "required": ["active", "age", "name"]
            }
          }
        },
        "required": ["names", "numbers", "records"]
      }""");
  }

  @Test
  void it_should_generate_schema_without_description_when_annotation_missing() {
    record NoDescriptionRecord(String userName, int userAge) {}

    var schema = SchemaUtils.generateOutputSchema(NoDescriptionRecord.class);

    // Without @JsonPropertyDescription, fields won't have descriptions
    assertSchemaEquals(schema, """
      {
        "type": "object",
        "properties": {
          "userName": {
            "type": "string"
          },
          "userAge": {
            "type": "integer"
          }
        },
        "required": ["userAge", "userName"]
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
      @JsonPropertyDescription("Top level field") String topLevel,
      @JsonPropertyDescription("Nested with list") RecordWithList nestedWithList,
      @JsonPropertyDescription("Simple list") List<String> simpleList
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

    var schema = SchemaUtils.generateOutputSchema(ComplexRecord.class);
    assertSchemaEquals(schema, """
      {
        "type": "object",
        "properties": {
          "topLevel": {
            "type": "string",
            "description": "Top level field"
          },
          "nestedWithList": {
            "type": "object",
            "description": "Nested with list",
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
                  "type": "integer"
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
                      "type": "integer",
                      "description": "An integer field"
                    },
                    "active": {
                      "type": "boolean",
                      "description": "A boolean field"
                    }
                  },
                  "required": ["active", "age", "name"]
                }
              }
            },
            "required": ["names", "numbers", "records"]
          },
          "simpleList": {
            "type": "array",
            "description": "Simple list",
            "items": {
              "type": "string"
            }
          }
        },
        "required": ["nestedWithList", "simpleList", "topLevel"]
      }""");

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
