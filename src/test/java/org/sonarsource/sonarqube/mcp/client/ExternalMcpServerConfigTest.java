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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExternalMcpServerConfigTest {

  @Test
  void constructor_should_throw_when_name_is_null() {
    assertThatThrownBy(() -> new ExternalMcpServerConfig(null, "npx", List.of(), Map.of()))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("External MCP server name cannot be null or blank");
  }

  @Test
  void constructor_should_throw_when_name_is_blank() {
    assertThatThrownBy(() -> new ExternalMcpServerConfig("  ", "npx", List.of(), Map.of()))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("External MCP server name cannot be null or blank");
  }

  @Test
  void constructor_should_throw_when_command_is_null() {
    assertThatThrownBy(() -> new ExternalMcpServerConfig("server", null, List.of(), Map.of()))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("External MCP server command cannot be null or blank");
  }

  @Test
  void constructor_should_throw_when_command_is_blank() {
    assertThatThrownBy(() -> new ExternalMcpServerConfig("server", "  ", List.of(), Map.of()))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("External MCP server command cannot be null or blank");
  }

  @Test
  void constructor_should_use_name_as_namespace_when_namespace_is_null() {
    var config = new ExternalMcpServerConfig("myServer", null, "npx", List.of("-y", "server"), Map.of());
    
    assertThat(config.name()).isEqualTo("myServer");
    assertThat(config.namespace()).isEqualTo("myServer");
    assertThat(config.command()).isEqualTo("npx");
    assertThat(config.args()).containsExactly("-y", "server");
    assertThat(config.env()).isEmpty();
  }

  @Test
  void constructor_should_use_name_as_namespace_when_namespace_is_blank() {
    var config = new ExternalMcpServerConfig("myServer", "  ", "npx", List.of(), Map.of());
    
    assertThat(config.namespace()).isEqualTo("myServer");
  }

  @Test
  void constructor_should_use_provided_namespace() {
    var config = new ExternalMcpServerConfig("internalId", "publicPrefix", "npx", List.of(), Map.of());
    
    assertThat(config.name()).isEqualTo("internalId");
    assertThat(config.namespace()).isEqualTo("publicPrefix");
  }

  @Test
  void constructor_should_default_args_to_empty_list() {
    var config = new ExternalMcpServerConfig("server", "npx", null, Map.of());
    
    assertThat(config.args()).isEmpty();
  }

  @Test
  void constructor_should_default_env_to_empty_map() {
    var config = new ExternalMcpServerConfig("server", "npx", List.of(), null);
    
    assertThat(config.env()).isEmpty();
  }

  @Test
  void constructor_should_create_immutable_copies() {
    var args = new java.util.ArrayList<>(List.of("arg1", "arg2"));
    var env = new java.util.HashMap<>(Map.of("KEY", "value"));
    
    var config = new ExternalMcpServerConfig("server", "npx", args, env);
    
    // Modify originals
    args.add("arg3");
    env.put("KEY2", "value2");
    
    // Config should not be affected
    assertThat(config.args()).containsExactly("arg1", "arg2");
    assertThat(config.env()).containsOnlyKeys("KEY");
  }

  @Test
  void getServerId_should_normalize_name() {
    var config = new ExternalMcpServerConfig("My-Server_123", "npx", List.of(), Map.of());
    
    assertThat(config.getServerId()).isEqualTo("my-server_123");
  }

  @Test
  void getServerId_should_replace_special_characters() {
    var config = new ExternalMcpServerConfig("Server!@#$%", "npx", List.of(), Map.of());
    
    assertThat(config.getServerId()).isEqualTo("server_____");
  }

  @Test
  void getToolNamespace_should_normalize_namespace() {
    var config = new ExternalMcpServerConfig("server", "My-Namespace_123", "npx", List.of(), Map.of());
    
    assertThat(config.getToolNamespace()).isEqualTo("my-namespace_123");
  }

  @Test
  void getToolNamespace_should_replace_special_characters() {
    var config = new ExternalMcpServerConfig("server", "Namespace!@#", "npx", List.of(), Map.of());
    
    assertThat(config.getToolNamespace()).isEqualTo("namespace___");
  }

  @Test
  void toString_should_contain_all_fields() {
    var config = new ExternalMcpServerConfig("myServer", "myNamespace", "npx", List.of("-y", "pkg"), Map.of("KEY", "val"));
    
    var result = config.toString();
    
    assertThat(result)
      .contains("name='myServer'")
      .contains("namespace='myNamespace'")
      .contains("command='npx'")
      .contains("args=[-y, pkg]");
  }

  @Test
  void convenience_constructor_should_work() {
    var config = new ExternalMcpServerConfig("server", "npx", List.of("arg"), Map.of("K", "V"));
    
    assertThat(config.name()).isEqualTo("server");
    assertThat(config.namespace()).isEqualTo("server"); // Uses name as namespace
    assertThat(config.command()).isEqualTo("npx");
    assertThat(config.args()).containsExactly("arg");
    assertThat(config.env()).containsEntry("K", "V");
  }
}


