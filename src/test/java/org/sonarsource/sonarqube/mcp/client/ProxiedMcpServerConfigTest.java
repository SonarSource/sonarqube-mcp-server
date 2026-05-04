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
package org.sonarsource.sonarqube.mcp.client;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class ProxiedMcpServerConfigTest {

  @Test
  void constructor_should_throw_when_name_is_blank() {
    var emptyList = List.<String>of();
    var emptyMap = Map.<String, String>of();
    var defaultTransports = Set.of(TransportMode.STDIO);
    assertThatThrownBy(() -> new ProxiedMcpServerConfig("  ", "npx", emptyList, emptyMap, emptyList, defaultTransports))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Proxied MCP server name cannot be null or blank");
  }

  @Test
  void constructor_should_throw_when_command_is_blank() {
    var emptyList = List.<String>of();
    var emptyMap = Map.<String, String>of();
    var defaultTransports = Set.of(TransportMode.STDIO);
    assertThatThrownBy(() -> new ProxiedMcpServerConfig("server", "  ", emptyList, emptyMap, emptyList, defaultTransports))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Proxied MCP server command cannot be null or blank");
  }

  @Test
  void constructor_should_throw_when_supported_transports_is_empty() {
    var emptyList = List.<String>of();
    var emptyMap = Map.<String, String>of();
    var emptyTransports = Set.<TransportMode>of();

    assertThatThrownBy(() -> new ProxiedMcpServerConfig("server", "npx", emptyList, emptyMap, emptyList, emptyTransports))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Proxied MCP server must support at least one transport mode");
  }

  @Test
  void supportsTransport_should_return_true_for_supported_transport() {
    var config = new ProxiedMcpServerConfig("server", "npx", List.of(), Map.of(), List.of(), Set.of(TransportMode.STDIO, TransportMode.HTTP));

    assertThat(config.supportsTransport(TransportMode.STDIO)).isTrue();
    assertThat(config.supportsTransport(TransportMode.HTTP)).isTrue();
  }

  @Test
  void supportsTransport_should_return_false_for_unsupported_transport() {
    var config = new ProxiedMcpServerConfig("server", "npx", List.of(), Map.of(), List.of(), Set.of(TransportMode.STDIO));

    assertThat(config.supportsTransport(TransportMode.HTTP)).isFalse();
  }

}
