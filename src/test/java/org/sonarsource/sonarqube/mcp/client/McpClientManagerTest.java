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
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpClientManagerTest {

  @Test
  void constructor_should_accept_empty_list() {
    var manager = new McpClientManager(List.of(), UUID.randomUUID().toString());

    assertThat(manager.getTotalCount()).isZero();
    assertThat(manager.isInitialized()).isFalse();
    assertThat(manager.getConnectedCount()).isZero();
  }

  @Test
  void initialize_should_be_idempotent() {
    var manager = new McpClientManager(List.of(), UUID.randomUUID().toString());

    manager.initialize();
    assertThat(manager.isInitialized()).isTrue();

    manager.initialize();
    assertThat(manager.isInitialized()).isTrue();
  }

  @Test
  void getTotalCount_should_match_config_count() {
    var configs = List.of(
      new ProxiedMcpServerConfig("server1", "npx", List.of(), Map.of(), List.of(), Set.of(TransportMode.STDIO)),
      new ProxiedMcpServerConfig("server2", "uv", List.of(), Map.of(), List.of(), Set.of(TransportMode.STDIO))
    );

    var manager = new McpClientManager(configs, UUID.randomUUID().toString());

    assertThat(manager.getTotalCount()).isEqualTo(2);
  }

  @Test
  void getAllProxiedTools_should_return_empty_map_before_initialization() {
    var configs = List.of(new ProxiedMcpServerConfig("server1", "npx", List.of(), Map.of(), List.of(), Set.of(TransportMode.STDIO)));

    var manager = new McpClientManager(configs, UUID.randomUUID().toString());

    // Before initialization, no tools discovered
    assertThat(manager.getAllProxiedTools()).isEmpty();
  }

  @Test
  void isServerConnected_should_return_false_for_unknown_server() {
    var manager = new McpClientManager(List.of(), UUID.randomUUID().toString());

    manager.initialize();

    assertThat(manager.isServerConnected("unknown")).isFalse();
  }

  @Test
  void executeTool_should_throw_when_server_not_connected() {
    var manager = new McpClientManager(List.of(), UUID.randomUUID().toString());
    var emptyMap = Map.<String, Object>of();

    manager.initialize();

    assertThatThrownBy(() -> manager.executeTool("unknown", "tool", emptyMap, null))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Service connection not established");
  }

  @Test
  void shutdown_should_clear_state() {
    var manager = new McpClientManager(List.of(), UUID.randomUUID().toString());
    manager.initialize();

    manager.shutdown();

    assertThat(manager.isInitialized()).isFalse();
    assertThat(manager.getClients()).isEmpty();
  }

  @Test
  void executeTool_should_include_error_message_for_failed_server() {
    var configs = List.of(new ProxiedMcpServerConfig("failing-server", "/non/existent/command", List.of(), Map.of(), List.of(), Set.of(TransportMode.STDIO)));
    var manager = new McpClientManager(configs, UUID.randomUUID().toString());
    var emptyMap = Map.<String, Object>of();

    manager.initialize();

    assertThatThrownBy(() -> manager.executeTool("failing-server", "tool", emptyMap, null))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Service unavailable");
  }

  @Test
  void buildEnvironmentVariables_should_include_explicit_values_from_config() {
    var manager = new McpClientManager(List.of(), UUID.randomUUID().toString());
    var config = new ProxiedMcpServerConfig("server", "cmd", List.of(),
      Map.of("EXPLICIT_VAR1", "value1", "EXPLICIT_VAR2", "value2"),
      List.of(), Set.of(TransportMode.STDIO));
    var parentEnv = Map.of("PARENT_VAR", "parent_value");

    var result = manager.buildEnvironmentVariables(config, parentEnv);

    assertThat(result)
      .hasSize(2)
      .containsEntry("EXPLICIT_VAR1", "value1")
      .containsEntry("EXPLICIT_VAR2", "value2")
      .doesNotContainKey("PARENT_VAR");
  }

  @Test
  void buildEnvironmentVariables_should_inherit_variables_from_parent() {
    var manager = new McpClientManager(List.of(), UUID.randomUUID().toString());
    var config = new ProxiedMcpServerConfig("server", "cmd", List.of(),
      Map.of(),
      List.of("INHERITED_VAR1", "INHERITED_VAR2"),
      Set.of(TransportMode.STDIO));
    var parentEnv = Map.of(
      "INHERITED_VAR1", "inherited_value1",
      "INHERITED_VAR2", "inherited_value2",
      "NOT_INHERITED", "should_not_appear"
    );

    var result = manager.buildEnvironmentVariables(config, parentEnv);

    assertThat(result)
      .hasSize(2)
      .containsEntry("INHERITED_VAR1", "inherited_value1")
      .containsEntry("INHERITED_VAR2", "inherited_value2")
      .doesNotContainKey("NOT_INHERITED");
  }

  @Test
  void buildEnvironmentVariables_should_prioritize_explicit_values_over_inherited() {
    var manager = new McpClientManager(List.of(), UUID.randomUUID().toString());
    var config = new ProxiedMcpServerConfig("server", "cmd", List.of(),
      Map.of("OVERRIDE_VAR", "explicit_value"),
      List.of("OVERRIDE_VAR"),
      Set.of(TransportMode.STDIO));
    var parentEnv = Map.of("OVERRIDE_VAR", "parent_value");

    var result = manager.buildEnvironmentVariables(config, parentEnv);

    assertThat(result)
      .hasSize(1)
      .containsEntry("OVERRIDE_VAR", "explicit_value");
  }

  @Test
  void buildEnvironmentVariables_should_skip_inherited_var_not_in_parent() {
    var manager = new McpClientManager(List.of(), UUID.randomUUID().toString());
    var config = new ProxiedMcpServerConfig("server", "cmd", List.of(),
      Map.of(),
      List.of("MISSING_VAR", "EXISTING_VAR"),
      Set.of(TransportMode.STDIO));
    var parentEnv = Map.of("EXISTING_VAR", "existing_value");

    var result = manager.buildEnvironmentVariables(config, parentEnv);

    assertThat(result)
      .hasSize(1)
      .containsEntry("EXISTING_VAR", "existing_value")
      .doesNotContainKey("MISSING_VAR");
  }

}
