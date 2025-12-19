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

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpClientManagerTest {

  @Test
  void constructor_should_accept_empty_list() {
    var manager = new McpClientManager(List.of());

    assertThat(manager.getTotalCount()).isZero();
    assertThat(manager.isInitialized()).isFalse();
    assertThat(manager.getConnectedCount()).isZero();
  }

  @Test
  void initialize_should_be_idempotent() {
    var manager = new McpClientManager(List.of());

    manager.initialize();
    assertThat(manager.isInitialized()).isTrue();

    // Second call should not throw
    manager.initialize();
    assertThat(manager.isInitialized()).isTrue();
  }

  @Test
  void getTotalCount_should_match_config_count() {
    var configs = List.of(
      new ExternalMcpServerConfig("server1", "server1", "npx", List.of(), Map.of()),
      new ExternalMcpServerConfig("server2", "server2", "uv", List.of(), Map.of())
    );

    var manager = new McpClientManager(configs);

    assertThat(manager.getTotalCount()).isEqualTo(2);
  }

  @Test
  void getAllExternalTools_should_return_empty_map_before_initialization() {
    var configs = List.of(new ExternalMcpServerConfig("server1", "server1", "npx", List.of(), Map.of()));

    var manager = new McpClientManager(configs);

    // Before initialization, no tools discovered
    assertThat(manager.getAllExternalTools()).isEmpty();
  }

  @Test
  void isServerConnected_should_return_false_for_unknown_server() {
    var manager = new McpClientManager(List.of());

    manager.initialize();

    assertThat(manager.isServerConnected("unknown")).isFalse();
  }

  @Test
  void executeTool_should_throw_when_server_not_connected() {
    var manager = new McpClientManager(List.of());

    manager.initialize();

    assertThatThrownBy(() -> manager.executeTool("unknown", "tool", Map.of()))
      .isInstanceOf(McpClientManager.ProviderUnavailableException.class)
      .hasMessage("Service connection not established");
  }

  @Test
  void shutdown_should_clear_state() {
    var manager = new McpClientManager(List.of());
    manager.initialize();

    manager.shutdown();

    assertThat(manager.isInitialized()).isFalse();
    assertThat(manager.getClients()).isEmpty();
  }

  @Test
  void initialize_with_invalid_command_should_log_error_but_not_fail() {
    var configs = List.of(new ExternalMcpServerConfig("failing-server", "failing-server", "/non/existent/command/that/should/fail",
      List.of(), Map.of()));
    var manager = new McpClientManager(configs);

    manager.initialize();

    assertThat(manager.isInitialized()).isTrue();
    assertThat(manager.getConnectedCount()).isZero();
    assertThat(manager.isServerConnected("failing-server")).isFalse();
  }

  @Test
  void executeTool_should_include_error_message_for_failed_server() {
    var configs = List.of(new ExternalMcpServerConfig("failing-server", "failing-server", "/non/existent/command", List.of(), Map.of()));
    var manager = new McpClientManager(configs);

    manager.initialize();

    assertThatThrownBy(() -> manager.executeTool("failing-server", "tool", Map.of()))
      .isInstanceOf(McpClientManager.ProviderUnavailableException.class)
      .hasMessageContaining("Service unavailable");
  }

  @Test
  void toolMapping_record_should_hold_correct_values() {
    var dummyTool = new McpSchema.Tool(
      "tool_name",
      "test description",
      "",
      new McpSchema.JsonSchema("object", Map.of(), List.of(), false, Map.of(), Map.of()),
      Map.of(),
      null,
      Map.of()
    );

    var mapping = new McpClientManager.ToolMapping(
      "server-id",
      "namespace",
      "original_tool",
      dummyTool
    );

    assertThat(mapping.serverId()).isEqualTo("server-id");
    assertThat(mapping.namespace()).isEqualTo("namespace");
    assertThat(mapping.originalToolName()).isEqualTo("original_tool");
    assertThat(mapping.tool()).isEqualTo(dummyTool);
  }

}
