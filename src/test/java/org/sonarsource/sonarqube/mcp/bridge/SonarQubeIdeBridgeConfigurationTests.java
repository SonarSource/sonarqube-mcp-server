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
package org.sonarsource.sonarqube.mcp.bridge;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SonarQubeIdeBridgeConfigurationTests {

  @Test
  void it_should_use_default_port_when_not_provided() {
    var configuration = new SonarQubeIdeBridgeConfiguration(Map.of());

    assertThat(configuration.getBaseUrl()).isEqualTo("http://localhost:64120");
  }

  @Test
  void it_should_use_custom_port_when_provided() {
    var configuration = new SonarQubeIdeBridgeConfiguration(Map.of("SONARLINT_PORT", "64125"));

    assertThat(configuration.getBaseUrl()).isEqualTo("http://localhost:64125");
  }

  @Test
  void it_should_accept_minimum_valid_port() {
    var configuration = new SonarQubeIdeBridgeConfiguration(Map.of("SONARLINT_PORT", "64120"));

    assertThat(configuration.getBaseUrl()).isEqualTo("http://localhost:64120");
  }

  @Test
  void it_should_accept_maximum_valid_port() {
    var configuration = new SonarQubeIdeBridgeConfiguration(Map.of("SONARLINT_PORT", "64130"));

    assertThat(configuration.getBaseUrl()).isEqualTo("http://localhost:64130");
  }

  @Test
  void it_should_throw_exception_when_port_is_below_minimum() {
    var configuration = Map.of("SONARLINT_PORT", "64119");
    assertThatThrownBy(() -> new SonarQubeIdeBridgeConfiguration(configuration))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("SonarQube for IDE port must be between 64120 and 64130, got: 64119");
  }

  @Test
  void it_should_throw_exception_when_port_is_above_maximum() {
    var configuration = Map.of("SONARLINT_PORT", "64131");
    assertThatThrownBy(() -> new SonarQubeIdeBridgeConfiguration(configuration))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("SonarQube for IDE port must be between 64120 and 64130, got: 64131");
  }

  @Test
  void it_should_throw_exception_when_port_is_not_a_number() {
    var configuration = Map.of("SONARLINT_PORT", "not-a-number");
    assertThatThrownBy(() -> new SonarQubeIdeBridgeConfiguration(configuration))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Invalid SonarQube for IDE port value: not-a-number")
      .hasCauseInstanceOf(NumberFormatException.class);
  }

  @Test
  void it_should_throw_exception_when_port_is_empty_string() {
    var configuration = Map.of("SONARLINT_PORT", "");
    assertThatThrownBy(() -> new SonarQubeIdeBridgeConfiguration(configuration))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Invalid SonarQube for IDE port value: ")
      .hasCauseInstanceOf(NumberFormatException.class);
  }

}
