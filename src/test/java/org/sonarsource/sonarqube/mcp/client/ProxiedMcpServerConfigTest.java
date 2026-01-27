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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProxiedMcpServerConfigTest {

  @Test
  void constructor_should_throw_when_name_is_blank() {
    var emptyList = List.<String>of();
    var emptyMap = Map.<String, String>of();
    assertThatThrownBy(() -> new ProxiedMcpServerConfig("  ", "namespace", "npx", emptyList, emptyMap))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Proxied MCP server name cannot be null or blank");
  }

  @Test
  void constructor_should_throw_when_namespace_is_blank() {
    var emptyList = List.<String>of();
    var emptyMap = Map.<String, String>of();
    assertThatThrownBy(() -> new ProxiedMcpServerConfig("server", "  ", "npx", emptyList, emptyMap))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Proxied MCP server namespace cannot be null or blank");
  }

  @Test
  void constructor_should_throw_when_command_is_blank() {
    var emptyList = List.<String>of();
    var emptyMap = Map.<String, String>of();
    assertThatThrownBy(() -> new ProxiedMcpServerConfig("server", "namespace", "  ", emptyList, emptyMap))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Proxied MCP server command cannot be null or blank");
  }

}
