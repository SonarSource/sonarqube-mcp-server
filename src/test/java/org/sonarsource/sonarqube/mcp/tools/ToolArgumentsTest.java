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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.tools.exception.MissingRequiredArgumentException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolArgumentsTest {

  @Test
  void should_return_string_when_argument_is_string() {
    var arguments = new Tool.Arguments(Map.of("stringArg", "testValue"), null);

    var result = arguments.getStringOrThrow("stringArg");

    assertThat(result).isEqualTo("testValue");
  }

  @Test
  void should_return_stringified_value_when_argument_is_integer() {
    var arguments = new Tool.Arguments(Map.of("intArg", 42), null);

    var result = arguments.getStringOrThrow("intArg");

    assertThat(result).isEqualTo("42");
  }

  @Test
  void should_return_stringified_value_when_argument_is_boolean() {
    var arguments = new Tool.Arguments(Map.of("boolArg", true), null);

    var result = arguments.getStringOrThrow("boolArg");

    assertThat(result).isEqualTo("true");
  }

  @Test
  void should_throw_exception_when_argument_is_null() {
    var argsMap = new HashMap<String, Object>();
    argsMap.put("nullArg", null);
    var arguments = new Tool.Arguments(argsMap, null);

    assertThatThrownBy(() -> arguments.getStringOrThrow("nullArg"))
      .isInstanceOf(MissingRequiredArgumentException.class)
      .hasMessage("Missing required argument: nullArg");
  }

  @Test
  void should_throw_exception_when_argument_is_missing() {
    var arguments = new Tool.Arguments(Map.of(), null);

    assertThatThrownBy(() -> arguments.getStringOrThrow("missingArg"))
      .isInstanceOf(MissingRequiredArgumentException.class)
      .hasMessage("Missing required argument: missingArg");
  }

  @Test
  void should_return_integer_when_optional_integer_argument_is_integer() {
    var arguments = new Tool.Arguments(Map.of("intArg", 42), null);

    var result = arguments.getOptionalInteger("intArg");

    assertThat(result).isEqualTo(42);
  }

  @Test
  void should_parse_string_when_optional_integer_argument_is_string_integer() {
    var arguments = new Tool.Arguments(Map.of("stringIntArg", "123"), null);

    var result = arguments.getOptionalInteger("stringIntArg");

    assertThat(result).isEqualTo(123);
  }

  @Test
  void should_return_null_when_optional_integer_argument_is_null() {
    var argsMap = new HashMap<String, Object>();
    argsMap.put("nullArg", null);
    var arguments = new Tool.Arguments(argsMap, null);

    var result = arguments.getOptionalInteger("nullArg");

    assertThat(result).isNull();
  }

  @Test
  void should_return_null_when_optional_integer_argument_is_missing() {
    var arguments = new Tool.Arguments(Map.of(), null);

    var result = arguments.getOptionalInteger("missingArg");

    assertThat(result).isNull();
  }

  @Test
  void should_return_boolean_when_optional_boolean_argument_is_boolean() {
    var arguments = new Tool.Arguments(Map.of("boolArg", true), null);

    var result = arguments.getOptionalBoolean("boolArg");

    assertThat(result).isTrue();
  }

  @Test
  void should_parse_string_when_optional_boolean_argument_is_string_boolean() {
    var arguments = new Tool.Arguments(Map.of("stringBoolArg", "false"), null);

    var result = arguments.getOptionalBoolean("stringBoolArg");

    assertThat(result).isFalse();
  }

  @Test
  void should_return_null_when_optional_boolean_argument_is_null() {
    var argsMap = new HashMap<String, Object>();
    argsMap.put("nullArg", null);
    var arguments = new Tool.Arguments(argsMap, null);

    var result = arguments.getOptionalBoolean("nullArg");

    assertThat(result).isNull();
  }

  @Test
  void should_return_null_when_optional_boolean_argument_is_missing() {
    var arguments = new Tool.Arguments(Map.of(), null);

    var result = arguments.getOptionalBoolean("missingArg");

    assertThat(result).isNull();
  }

  @Test
  void should_return_string_when_optional_string_argument_is_string() {
    var arguments = new Tool.Arguments(Map.of("stringArg", "testValue"), null);

    var result = arguments.getOptionalString("stringArg");

    assertThat(result).isEqualTo("testValue");
  }

  @Test
  void should_return_null_when_optional_string_argument_is_not_string() {
    var arguments = new Tool.Arguments(Map.of("intArg", 42), null);

    var result = arguments.getOptionalString("intArg");

    assertThat(result).isNull();
  }

  @Test
  void should_return_null_when_optional_string_argument_is_null() {
    var argsMap = new HashMap<String, Object>();
    argsMap.put("nullArg", null);
    var arguments = new Tool.Arguments(argsMap, null);

    var result = arguments.getOptionalString("nullArg");

    assertThat(result).isNull();
  }

  @Test
  void should_return_null_when_optional_string_argument_is_blank() {
    var arguments = new Tool.Arguments(Map.of("blankArg", " ", "emptyArg", ""), null);

    assertThat(arguments.getOptionalString("blankArg")).isNull();
    assertThat(arguments.getOptionalString("emptyArg")).isNull();
  }

  @Test
  void should_throw_exception_when_required_string_argument_is_blank() {
    var arguments = new Tool.Arguments(Map.of("stringArg", ""), null);

    assertThatThrownBy(() -> arguments.getStringOrThrow("stringArg"))
      .isInstanceOf(MissingRequiredArgumentException.class)
      .hasMessage("Missing required argument: stringArg");
  }

  @Test
  void should_return_null_when_optional_integer_argument_is_blank_string() {
    var arguments = new Tool.Arguments(Map.of("intArg", ""), null);

    assertThat(arguments.getOptionalInteger("intArg")).isNull();
  }

  @Test
  void should_return_null_when_optional_boolean_argument_is_blank_string() {
    var arguments = new Tool.Arguments(Map.of("boolArg", "   "), null);

    assertThat(arguments.getOptionalBoolean("boolArg")).isNull();
  }

  @Test
  void should_return_null_when_optional_string_list_argument_is_empty() {
    var arguments = new Tool.Arguments(Map.of("listArg", List.of()), null);

    assertThat(arguments.getOptionalStringList("listArg")).isNull();
  }

  @Test
  void should_return_null_when_optional_string_list_argument_contains_only_blank_values() {
    var arguments = new Tool.Arguments(Map.of("listArg", List.of("", " ")), null);

    assertThat(arguments.getOptionalStringList("listArg")).isNull();
  }

  @Test
  void should_filter_blank_values_from_optional_string_list_argument() {
    var arguments = new Tool.Arguments(Map.of("listArg", List.of("", "item1", " ")), null);

    assertThat(arguments.getOptionalStringList("listArg")).containsExactly("item1");
  }

  @Test
  void should_filter_null_elements_from_optional_string_list_argument() {
    var listWithNull = new java.util.ArrayList<String>();
    listWithNull.add(null);
    listWithNull.add("item1");
    var arguments = new Tool.Arguments(Map.of("listArg", listWithNull), null);

    assertThat(arguments.getOptionalStringList("listArg")).containsExactly("item1");
  }

  @Test
  void should_return_null_when_optional_enum_argument_is_blank() {
    var arguments = new Tool.Arguments(Map.of("enumArg", ""), null);
    var validValues = new String[] {"OPEN", "CLOSED"};

    assertThat(arguments.getOptionalEnumValue("enumArg", validValues)).isNull();
  }

  @Test
  void should_throw_exception_when_required_enum_argument_is_blank() {
    var arguments = new Tool.Arguments(Map.of("enumArg", " "), null);
    var validValues = new String[] {"OPEN", "CLOSED"};

    assertThatThrownBy(() -> arguments.getEnumOrThrow("enumArg", validValues))
      .isInstanceOf(MissingRequiredArgumentException.class)
      .hasMessage("Missing required argument: enumArg");
  }

  @Test
  void should_return_value_when_int_or_default_argument_is_present() {
    var arguments = new Tool.Arguments(Map.of("intArg", 42), null);

    var result = arguments.getIntOrDefault("intArg", 100);

    assertThat(result).isEqualTo(42);
  }

  @Test
  void should_return_default_when_int_or_default_argument_is_missing() {
    var arguments = new Tool.Arguments(Map.of(), null);

    var result = arguments.getIntOrDefault("missingArg", 100);

    assertThat(result).isEqualTo(100);
  }

  @Test
  void should_return_list_when_string_list_argument_is_string_list() {
    var expectedList = List.of("item1", "item2", "item3");
    var arguments = new Tool.Arguments(Map.of("listArg", expectedList), null);

    var result = arguments.getStringListOrThrow("listArg");

    assertThat(result).isEqualTo(expectedList);
  }

  @Test
  void should_throw_exception_when_string_list_argument_is_missing() {
    var arguments = new Tool.Arguments(Map.of(), null);

    assertThatThrownBy(() -> arguments.getStringListOrThrow("missingListArg"))
      .isInstanceOf(MissingRequiredArgumentException.class)
      .hasMessage("Missing required argument: missingListArg");
  }

  @Test
  void should_return_list_when_optional_string_list_argument_is_string_list() {
    var expectedList = List.of("item1", "item2");
    var arguments = new Tool.Arguments(Map.of("listArg", expectedList), null);

    var result = arguments.getOptionalStringList("listArg");

    assertThat(result).isEqualTo(expectedList);
  }

  @Test
  void should_return_null_when_optional_string_list_argument_is_missing() {
    var arguments = new Tool.Arguments(Map.of(), null);

    var result = arguments.getOptionalStringList("missingListArg");

    assertThat(result).isNull();
  }

  @Test
  void should_return_boolean_when_boolean_argument_is_boolean_true() {
    var arguments = new Tool.Arguments(Map.of("boolArg", true), null);

    var result = arguments.getBooleanOrThrow("boolArg");

    assertThat(result).isTrue();
  }

  @Test
  void should_return_boolean_when_boolean_argument_is_boolean_false() {
    var arguments = new Tool.Arguments(Map.of("boolArg", false), null);

    var result = arguments.getBooleanOrThrow("boolArg");

    assertThat(result).isFalse();
  }

  @Test
  void should_parse_string_when_boolean_argument_is_string_true() {
    var arguments = new Tool.Arguments(Map.of("stringBoolArg", "true"), null);

    var result = arguments.getBooleanOrThrow("stringBoolArg");

    assertThat(result).isTrue();
  }

  @Test
  void should_parse_string_when_boolean_argument_is_string_false() {
    var arguments = new Tool.Arguments(Map.of("stringBoolArg", "false"), null);

    var result = arguments.getBooleanOrThrow("stringBoolArg");

    assertThat(result).isFalse();
  }

  @Test
  void should_return_false_when_boolean_argument_is_invalid_string() {
    var arguments = new Tool.Arguments(Map.of("stringBoolArg", "invalid"), null);

    var result = arguments.getBooleanOrThrow("stringBoolArg");

    assertThat(result).isFalse();
  }

  @Test
  void should_throw_exception_when_boolean_argument_is_null() {
    var argsMap = new HashMap<String, Object>();
    argsMap.put("nullArg", null);
    var arguments = new Tool.Arguments(argsMap, null);

    assertThatThrownBy(() -> arguments.getBooleanOrThrow("nullArg"))
      .isInstanceOf(MissingRequiredArgumentException.class)
      .hasMessage("Missing required argument: nullArg");
  }

  @Test
  void should_throw_exception_when_boolean_argument_is_missing() {
    var arguments = new Tool.Arguments(Map.of(), null);

    assertThatThrownBy(() -> arguments.getBooleanOrThrow("missingArg"))
      .isInstanceOf(MissingRequiredArgumentException.class)
      .hasMessage("Missing required argument: missingArg");
  }

  @Test
  void should_throw_exception_when_boolean_argument_is_wrong_type() {
    var arguments = new Tool.Arguments(Map.of("intArg", 42), null);

    assertThatThrownBy(() -> arguments.getBooleanOrThrow("intArg"))
      .isInstanceOf(MissingRequiredArgumentException.class)
      .hasMessage("Missing required argument: intArg");
  }

  @Test
  void should_prefer_argument_over_configured_project_key() {
    var arguments = new Tool.Arguments(Map.of("projectKey", "from-arg"), null);

    assertThat(arguments.getOptionalProjectKeyWithFallback("projectKey", "configured")).isEqualTo("from-arg");
  }

  @Test
  void should_use_configured_project_key_when_argument_is_missing() {
    var arguments = new Tool.Arguments(Map.of(), null);

    assertThat(arguments.getOptionalProjectKeyWithFallback("projectKey", "configured")).isEqualTo("configured");
  }

  @Test
  void should_return_null_when_neither_argument_nor_configured_project_key_is_available() {
    var arguments = new Tool.Arguments(Map.of(), null);

    assertThat(arguments.getOptionalProjectKeyWithFallback("projectKey", null)).isNull();
  }

  @Test
  void should_throw_when_required_project_key_is_missing() {
    var arguments = new Tool.Arguments(Map.of(), null);

    assertThatThrownBy(() -> arguments.getProjectKeyWithFallback("projectKey", null))
      .isInstanceOf(MissingRequiredArgumentException.class)
      .hasMessage("Missing required argument: projectKey");
  }

}
