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
package org.sonarsource.sonarqube.mcp.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransportModeTest {

  @Test
  void fromString_should_parse_stdio_case_insensitive() {
    assertThat(TransportMode.fromString("stdio")).isEqualTo(TransportMode.STDIO);
    assertThat(TransportMode.fromString("STDIO")).isEqualTo(TransportMode.STDIO);
    assertThat(TransportMode.fromString("StDiO")).isEqualTo(TransportMode.STDIO);
    assertThat(TransportMode.fromString("  stdio  ")).isEqualTo(TransportMode.STDIO);
  }

  @Test
  void fromString_should_parse_http_case_insensitive() {
    assertThat(TransportMode.fromString("http")).isEqualTo(TransportMode.HTTP);
    assertThat(TransportMode.fromString("HTTP")).isEqualTo(TransportMode.HTTP);
    assertThat(TransportMode.fromString("HtTp")).isEqualTo(TransportMode.HTTP);
    assertThat(TransportMode.fromString("  http  ")).isEqualTo(TransportMode.HTTP);
  }

  @Test
  void fromString_should_throw_for_invalid_value() {
    assertThatThrownBy(() -> TransportMode.fromString("invalid"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Invalid transport mode: 'invalid'")
      .hasMessageContaining("Valid values are: stdio, http");
  }

  @Test
  void fromString_should_throw_for_blank() {
    assertThatThrownBy(() -> TransportMode.fromString("  "))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Transport mode cannot be null or blank");
  }

  @Test
  void toConfigString_should_return_lowercase() {
    assertThat(TransportMode.STDIO.toConfigString()).isEqualTo("stdio");
    assertThat(TransportMode.HTTP.toConfigString()).isEqualTo("http");
  }

  @Test
  void enum_should_have_only_two_values() {
    var values = TransportMode.values();
    assertThat(values)
      .hasSize(2)
      .containsExactlyInAnyOrder(TransportMode.STDIO, TransportMode.HTTP);
  }

}
