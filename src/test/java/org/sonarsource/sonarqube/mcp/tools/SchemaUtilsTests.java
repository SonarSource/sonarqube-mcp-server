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

import jakarta.annotation.Nullable;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaUtilsTests {

  public record SimpleRecord(String name, int age, boolean active) {}

  public record RecordWithNullable(
    String requiredField,
    @Nullable String optionalField,
    int requiredNumber,
    @Nullable Integer optionalNumber
  ) {}

  public record NestedRecord(String parentName, SimpleRecord child) {}

  public record RecordWithList(List<String> names, List<Integer> numbers, List<SimpleRecord> records) {}

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
  void it_should_serialize_record_with_list_to_json_string() {
    var output = new RecordWithList(
      List.of("Alice", "Bob"),
      List.of(1, 2, 3),
      List.of(new SimpleRecord("Test", 25, true))
    );
    var json = SchemaUtils.toJsonString(output);

    assertThat(json)
      .contains("\"Alice\"")
      .contains("\"Bob\"")
      .contains("\"name\" : \"Test\"")
      .contains("\"age\" : 25")
      .contains("\"active\" : true");
  }

  @Test
  void it_should_serialize_empty_lists() {
    var output = new RecordWithList(List.of(), List.of(), List.of());
    var json = SchemaUtils.toJsonString(output);

    assertThat(json)
      .contains("\"names\" : [ ]")
      .contains("\"numbers\" : [ ]")
      .contains("\"records\" : [ ]");
  }

}
