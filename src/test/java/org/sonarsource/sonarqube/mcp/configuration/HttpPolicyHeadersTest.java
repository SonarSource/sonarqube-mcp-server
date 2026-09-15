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
package org.sonarsource.sonarqube.mcp.configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class HttpPolicyHeadersTest {

  @Test
  void should_return_null_when_read_only_header_is_missing() {
    assertThat(HttpPolicyHeaders.readOnlyValue(headers())).isNull();
  }

  @Test
  void should_return_null_when_toolsets_header_is_missing() {
    assertThat(HttpPolicyHeaders.toolsetsValue(headers())).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"true", "TRUE", "True", "false", "FALSE", "False"})
  void should_accept_underscore_read_only_any_case(String value) {
    assertThat(HttpPolicyHeaders.readOnlyValue(headers(HttpPolicyHeaders.READ_ONLY, value))).isEqualTo(value);
  }

  @ParameterizedTest
  @ValueSource(strings = {"true", "false"})
  void should_prefer_underscore_read_only_over_hyphen_and_x(String value) {
    var lookup = headers(
      HttpPolicyHeaders.READ_ONLY, value,
      HttpPolicyHeaders.READ_ONLY_HYPHEN, "false".equals(value) ? "true" : "false",
      HttpPolicyHeaders.READ_ONLY_X, "true");

    assertThat(HttpPolicyHeaders.readOnlyValue(lookup)).isEqualTo(value);
  }

  @Test
  void should_use_hyphen_read_only_when_underscore_is_blank() {
    var lookup = headers(
      HttpPolicyHeaders.READ_ONLY, "  ",
      HttpPolicyHeaders.READ_ONLY_HYPHEN, "true",
      HttpPolicyHeaders.READ_ONLY_X, "false");

    assertThat(HttpPolicyHeaders.readOnlyValue(lookup)).isEqualTo("true");
  }

  @Test
  void should_use_x_read_only_when_underscore_and_hyphen_are_absent() {
    assertThat(HttpPolicyHeaders.readOnlyValue(headers(HttpPolicyHeaders.READ_ONLY_X, "false"))).isEqualTo("false");
  }

  @Test
  void should_prefer_underscore_toolsets_then_hyphen_then_x() {
    assertThat(HttpPolicyHeaders.toolsetsValue(headers(
      HttpPolicyHeaders.TOOLSETS, "issues",
      HttpPolicyHeaders.TOOLSETS_HYPHEN, "measures",
      HttpPolicyHeaders.TOOLSETS_X, "rules"))).isEqualTo("issues");

    assertThat(HttpPolicyHeaders.toolsetsValue(headers(
      HttpPolicyHeaders.TOOLSETS_HYPHEN, "measures",
      HttpPolicyHeaders.TOOLSETS_X, "rules"))).isEqualTo("measures");

    assertThat(HttpPolicyHeaders.toolsetsValue(headers(HttpPolicyHeaders.TOOLSETS_X, "rules"))).isEqualTo("rules");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"  "})
  void should_treat_missing_or_blank_read_only_as_valid(String value) {
    assertThat(HttpPolicyHeaders.isValidReadOnlyValue(value)).isTrue();
    assertThat(HttpPolicyHeaders.parseStrictBoolean(value)).isNull();
  }

  @ParameterizedTest
  @MethodSource("trueFalseCases")
  void should_parse_true_false_any_case_and_not_use_boolean_parse_boolean(String raw, boolean expected) {
    assertThat(HttpPolicyHeaders.isValidReadOnlyValue(raw)).isTrue();
    assertThat(HttpPolicyHeaders.parseStrictBoolean(raw)).isEqualTo(expected);
  }

  static Stream<Arguments> trueFalseCases() {
    return Stream.of(
      Arguments.of("true", true),
      Arguments.of("TRUE", true),
      Arguments.of("True", true),
      Arguments.of(" false ", false),
      Arguments.of("FALSE", false),
      Arguments.of("False", false)
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {"yes", "no", "1", "0", "TRUEE"})
  void should_reject_invalid_read_only_values_unlike_boolean_parse_boolean(String raw) {
    assertThat(HttpPolicyHeaders.isValidReadOnlyValue(raw)).isFalse();
    assertThat(HttpPolicyHeaders.parseStrictBoolean(raw)).isNull();
    // Boolean.parseBoolean treats any non-"true" as false; this parser must not.
    assertThat(Boolean.parseBoolean(raw)).isFalse();
  }

  private static Function<String, String> headers(String... keyValues) {
    Map<String, String> map = new HashMap<>();
    for (int i = 0; i < keyValues.length; i += 2) {
      map.put(keyValues[i], keyValues[i + 1]);
    }
    return map::get;
  }

}
