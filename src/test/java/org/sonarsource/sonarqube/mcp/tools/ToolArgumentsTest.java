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
    var arguments = new Tool.Arguments(Map.of("stringArg", "testValue"));

    var result = arguments.getStringOrThrow("stringArg");

    assertThat(result).isEqualTo("testValue");
  }

  @Test
  void should_return_stringified_value_when_argument_is_integer() {
    var arguments = new Tool.Arguments(Map.of("intArg", 42));

    var result = arguments.getStringOrThrow("intArg");

    assertThat(result).isEqualTo("42");
  }

  @Test
  void should_return_stringified_value_when_argument_is_boolean() {
    var arguments = new Tool.Arguments(Map.of("boolArg", true));

    var result = arguments.getStringOrThrow("boolArg");

    assertThat(result).isEqualTo("true");
  }

  @Test
  void should_throw_exception_when_argument_is_null() {
    var argsMap = new HashMap<String, Object>();
    argsMap.put("nullArg", null);
    var arguments = new Tool.Arguments(argsMap);

    assertThatThrownBy(() -> arguments.getStringOrThrow("nullArg"))
      .isInstanceOf(MissingRequiredArgumentException.class)
      .hasMessage("Missing required argument: nullArg");
  }

  @Test
  void should_throw_exception_when_argument_is_missing() {
    var arguments = new Tool.Arguments(Map.of());

    assertThatThrownBy(() -> arguments.getStringOrThrow("missingArg"))
      .isInstanceOf(MissingRequiredArgumentException.class)
      .hasMessage("Missing required argument: missingArg");
  }

  @Test
  void should_return_integer_when_optional_integer_argument_is_integer() {
    var arguments = new Tool.Arguments(Map.of("intArg", 42));

    var result = arguments.getOptionalInteger("intArg");

    assertThat(result).isEqualTo(42);
  }

  @Test
  void should_parse_string_when_optional_integer_argument_is_string_integer() {
    var arguments = new Tool.Arguments(Map.of("stringIntArg", "123"));

    var result = arguments.getOptionalInteger("stringIntArg");

    assertThat(result).isEqualTo(123);
  }

  @Test
  void should_return_null_when_optional_integer_argument_is_null() {
    var argsMap = new HashMap<String, Object>();
    argsMap.put("nullArg", null);
    var arguments = new Tool.Arguments(argsMap);

    var result = arguments.getOptionalInteger("nullArg");

    assertThat(result).isNull();
  }

  @Test
  void should_return_null_when_optional_integer_argument_is_missing() {
    var arguments = new Tool.Arguments(Map.of());

    var result = arguments.getOptionalInteger("missingArg");

    assertThat(result).isNull();
  }

  @Test
  void should_return_boolean_when_optional_boolean_argument_is_boolean() {
    var arguments = new Tool.Arguments(Map.of("boolArg", true));

    var result = arguments.getOptionalBoolean("boolArg");

    assertThat(result).isTrue();
  }

  @Test
  void should_parse_string_when_optional_boolean_argument_is_string_boolean() {
    var arguments = new Tool.Arguments(Map.of("stringBoolArg", "false"));

    var result = arguments.getOptionalBoolean("stringBoolArg");

    assertThat(result).isFalse();
  }

  @Test
  void should_return_null_when_optional_boolean_argument_is_null() {
    var argsMap = new HashMap<String, Object>();
    argsMap.put("nullArg", null);
    var arguments = new Tool.Arguments(argsMap);

    var result = arguments.getOptionalBoolean("nullArg");

    assertThat(result).isNull();
  }

  @Test
  void should_return_null_when_optional_boolean_argument_is_missing() {
    var arguments = new Tool.Arguments(Map.of());

    var result = arguments.getOptionalBoolean("missingArg");

    assertThat(result).isNull();
  }

  @Test
  void should_return_string_when_optional_string_argument_is_string() {
    var arguments = new Tool.Arguments(Map.of("stringArg", "testValue"));

    var result = arguments.getOptionalString("stringArg");

    assertThat(result).isEqualTo("testValue");
  }

  @Test
  void should_return_null_when_optional_string_argument_is_not_string() {
    var arguments = new Tool.Arguments(Map.of("intArg", 42));

    var result = arguments.getOptionalString("intArg");

    assertThat(result).isNull();
  }

  @Test
  void should_return_null_when_optional_string_argument_is_null() {
    var argsMap = new HashMap<String, Object>();
    argsMap.put("nullArg", null);
    var arguments = new Tool.Arguments(argsMap);

    var result = arguments.getOptionalString("nullArg");

    assertThat(result).isNull();
  }

  @Test
  void should_return_value_when_int_or_default_argument_is_present() {
    var arguments = new Tool.Arguments(Map.of("intArg", 42));

    var result = arguments.getIntOrDefault("intArg", 100);

    assertThat(result).isEqualTo(42);
  }

  @Test
  void should_return_default_when_int_or_default_argument_is_missing() {
    var arguments = new Tool.Arguments(Map.of());

    var result = arguments.getIntOrDefault("missingArg", 100);

    assertThat(result).isEqualTo(100);
  }

  @Test
  void should_return_list_when_string_list_argument_is_string_list() {
    var expectedList = List.of("item1", "item2", "item3");
    var arguments = new Tool.Arguments(Map.of("listArg", expectedList));

    var result = arguments.getStringListOrThrow("listArg");

    assertThat(result).isEqualTo(expectedList);
  }

  @Test
  void should_throw_exception_when_string_list_argument_is_missing() {
    var arguments = new Tool.Arguments(Map.of());

    assertThatThrownBy(() -> arguments.getStringListOrThrow("missingListArg"))
      .isInstanceOf(MissingRequiredArgumentException.class)
      .hasMessage("Missing required argument: missingListArg");
  }

  @Test
  void should_return_list_when_optional_string_list_argument_is_string_list() {
    var expectedList = List.of("item1", "item2");
    var arguments = new Tool.Arguments(Map.of("listArg", expectedList));

    var result = arguments.getOptionalStringList("listArg");

    assertThat(result).isEqualTo(expectedList);
  }

  @Test
  void should_return_null_when_optional_string_list_argument_is_missing() {
    var arguments = new Tool.Arguments(Map.of());

    var result = arguments.getOptionalStringList("missingListArg");

    assertThat(result).isNull();
  }

  @Test
  void should_return_boolean_when_boolean_argument_is_boolean_true() {
    var arguments = new Tool.Arguments(Map.of("boolArg", true));

    var result = arguments.getBooleanOrThrow("boolArg");

    assertThat(result).isTrue();
  }

  @Test
  void should_return_boolean_when_boolean_argument_is_boolean_false() {
    var arguments = new Tool.Arguments(Map.of("boolArg", false));

    var result = arguments.getBooleanOrThrow("boolArg");

    assertThat(result).isFalse();
  }

  @Test
  void should_parse_string_when_boolean_argument_is_string_true() {
    var arguments = new Tool.Arguments(Map.of("stringBoolArg", "true"));

    var result = arguments.getBooleanOrThrow("stringBoolArg");

    assertThat(result).isTrue();
  }

  @Test
  void should_parse_string_when_boolean_argument_is_string_false() {
    var arguments = new Tool.Arguments(Map.of("stringBoolArg", "false"));

    var result = arguments.getBooleanOrThrow("stringBoolArg");

    assertThat(result).isFalse();
  }

  @Test
  void should_return_false_when_boolean_argument_is_invalid_string() {
    var arguments = new Tool.Arguments(Map.of("stringBoolArg", "invalid"));

    var result = arguments.getBooleanOrThrow("stringBoolArg");

    assertThat(result).isFalse();
  }

  @Test
  void should_throw_exception_when_boolean_argument_is_null() {
    var argsMap = new HashMap<String, Object>();
    argsMap.put("nullArg", null);
    var arguments = new Tool.Arguments(argsMap);

    assertThatThrownBy(() -> arguments.getBooleanOrThrow("nullArg"))
      .isInstanceOf(MissingRequiredArgumentException.class)
      .hasMessage("Missing required argument: nullArg");
  }

  @Test
  void should_throw_exception_when_boolean_argument_is_missing() {
    var arguments = new Tool.Arguments(Map.of());

    assertThatThrownBy(() -> arguments.getBooleanOrThrow("missingArg"))
      .isInstanceOf(MissingRequiredArgumentException.class)
      .hasMessage("Missing required argument: missingArg");
  }

  @Test
  void should_throw_exception_when_boolean_argument_is_wrong_type() {
    var arguments = new Tool.Arguments(Map.of("intArg", 42));

    assertThatThrownBy(() -> arguments.getBooleanOrThrow("intArg"))
      .isInstanceOf(MissingRequiredArgumentException.class)
      .hasMessage("Missing required argument: intArg");
  }

}
