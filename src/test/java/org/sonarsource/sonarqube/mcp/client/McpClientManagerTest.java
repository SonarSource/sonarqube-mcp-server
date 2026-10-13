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

import io.modelcontextprotocol.client.transport.ServerParameters;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.transport.McpJsonMappers;
import reactor.core.publisher.Mono;

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
  void initialize_should_close_live_child_after_handshake_timeout() throws URISyntaxException {
    var transports = new ArrayList<ManagedStdioClientTransport>();
    var manager = new McpClientManager(List.of(testServer("timed-out", "initialize-timeout")), UUID.randomUUID().toString(),
      Duration.ofSeconds(1), (name, params) -> trackTransport(transports, name, params));

    manager.initialize();

    assertThat(manager.isServerConnected("timed-out")).isFalse();
    assertThat(manager.getAllProxiedTools()).isEmpty();
    assertThat(manager.getProxiedInstructions()).isEmpty();
    assertThatThrownBy(() -> manager.executeTool("timed-out", "test_tool_1", Map.of(), null))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Service unavailable");
    assertFailedTransportClosed(transports);
  }

  @Test
  void initialize_should_close_live_child_after_tools_list_error_and_keep_sibling_usable() throws URISyntaxException {
    var transports = new ArrayList<ManagedStdioClientTransport>();
    var configs = List.of(testServer("failed", "tools-list-error"), testServer("working", null));
    var manager = new McpClientManager(configs, UUID.randomUUID().toString(), Duration.ofSeconds(5),
      (name, params) -> trackTransport(transports, name, params));

    try {
      manager.initialize();

      assertThat(manager.isServerConnected("failed")).isFalse();
      assertThat(manager.isServerConnected("working")).isTrue();
      assertThat(manager.getAllProxiedTools()).containsKeys("test_tool_1", "test_tool_2");
      assertThat(manager.executeTool("working", "test_tool_1", Map.of(), null).isError()).isFalse();
      assertThatThrownBy(() -> manager.executeTool("failed", "test_tool_1", Map.of(), null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Test tools/list failure");
      assertThat(transports).hasSize(2);
      assertThat(transports.get(1).isProcessAlive()).isTrue();
      assertFailedTransportClosed(transports.subList(0, 1));
    } finally {
      manager.shutdown();
    }
  }

  @Test
  void initialize_should_preserve_discovery_error_when_cleanup_fails() throws URISyntaxException {
    var transports = new ArrayList<ManagedStdioClientTransport>();
    var cleanupAttempted = new AtomicBoolean();
    var allowCleanup = new AtomicBoolean();
    var manager = new McpClientManager(List.of(testServer("failed", "tools-list-error")), UUID.randomUUID().toString(),
      Duration.ofSeconds(5), (name, params) -> {
        var transport = new ManagedStdioClientTransport(name, params, McpJsonMappers.DEFAULT) {
          @Override
          public Mono<Void> closeGracefully() {
            cleanupAttempted.set(true);
            if (!allowCleanup.get()) {
              return Mono.error(new IllegalStateException("Cleanup failed"));
            }
            return super.closeGracefully();
          }
        };
        transports.add(transport);
        return transport;
      });

    try {
      manager.initialize();

      assertThat(cleanupAttempted.get()).isTrue();
      assertThatThrownBy(() -> manager.executeTool("failed", "test_tool_1", Map.of(), null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Test tools/list failure")
        .hasMessageNotContaining("Cleanup failed");
    } finally {
      allowCleanup.set(true);
      transports.forEach(transport -> transport.closeGracefully().block(Duration.ofSeconds(10)));
    }
    assertFailedTransportClosed(transports);
  }

  private static ProxiedMcpServerConfig testServer(String name, String failureMode) throws URISyntaxException {
    var script = Path.of(McpClientManagerTest.class.getResource("/test-mcp-server.py").toURI()).toString();
    var args = failureMode == null ? List.of(script) : List.of(script, "--failure-mode", failureMode);
    return new ProxiedMcpServerConfig(name, "python3", args, Map.of(), List.of(), Set.of(TransportMode.STDIO));
  }

  private static ManagedStdioClientTransport trackTransport(List<ManagedStdioClientTransport> transports, String name, ServerParameters params) {
    var transport = new ManagedStdioClientTransport(name, params, McpJsonMappers.DEFAULT);
    transports.add(transport);
    return transport;
  }

  private static void assertFailedTransportClosed(List<ManagedStdioClientTransport> transports) {
    assertThat(transports).hasSize(1);
    assertThat(transports.get(0).wasProcessStarted()).isTrue();
    assertThat(transports.get(0).isProcessAlive()).isFalse();
    assertThat(transports.get(0).areSchedulersDisposed()).isTrue();
  }

  @Test
  void buildEnvironmentVariables_should_include_explicit_values_from_config() {
    var config = new ProxiedMcpServerConfig("server", "cmd", List.of(),
      Map.of("EXPLICIT_VAR1", "value1", "EXPLICIT_VAR2", "value2"),
      List.of(), Set.of(TransportMode.STDIO));
    var parentEnv = Map.of("PARENT_VAR", "parent_value");

    var result = McpClientManager.buildEnvironmentVariables(config, parentEnv);

    assertThat(result)
      .hasSize(2)
      .containsEntry("EXPLICIT_VAR1", "value1")
      .containsEntry("EXPLICIT_VAR2", "value2")
      .doesNotContainKey("PARENT_VAR");
  }

  @Test
  void buildEnvironmentVariables_should_inherit_variables_from_parent() {
    var config = new ProxiedMcpServerConfig("server", "cmd", List.of(),
      Map.of(),
      List.of("INHERITED_VAR1", "INHERITED_VAR2"),
      Set.of(TransportMode.STDIO));
    var parentEnv = Map.of(
      "INHERITED_VAR1", "inherited_value1",
      "INHERITED_VAR2", "inherited_value2",
      "NOT_INHERITED", "should_not_appear"
    );

    var result = McpClientManager.buildEnvironmentVariables(config, parentEnv);

    assertThat(result)
      .hasSize(2)
      .containsEntry("INHERITED_VAR1", "inherited_value1")
      .containsEntry("INHERITED_VAR2", "inherited_value2")
      .doesNotContainKey("NOT_INHERITED");
  }

  @Test
  void buildEnvironmentVariables_should_prioritize_explicit_values_over_inherited() {
    var config = new ProxiedMcpServerConfig("server", "cmd", List.of(),
      Map.of("OVERRIDE_VAR", "explicit_value"),
      List.of("OVERRIDE_VAR"),
      Set.of(TransportMode.STDIO));
    var parentEnv = Map.of("OVERRIDE_VAR", "parent_value");

    var result = McpClientManager.buildEnvironmentVariables(config, parentEnv);

    assertThat(result)
      .hasSize(1)
      .containsEntry("OVERRIDE_VAR", "explicit_value");
  }

  @Test
  void getProxiedInstructions_should_return_empty_before_initialization() {
    var configs = List.of(
      new ProxiedMcpServerConfig("server1", "npx", List.of(), Map.of(), List.of(), Set.of(TransportMode.STDIO)));
    var manager = new McpClientManager(configs, UUID.randomUUID().toString());

    assertThat(manager.getProxiedInstructions()).isEmpty();
  }

  @Test
  void getProxiedInstructions_should_return_empty_when_no_servers() {
    var manager = new McpClientManager(List.of(), UUID.randomUUID().toString());
    manager.initialize();

    assertThat(manager.getProxiedInstructions()).isEmpty();
  }

  @Test
  void getProxiedInstructions_should_return_empty_when_server_failed_to_connect() {
    var configs = List.of(
      new ProxiedMcpServerConfig("failing-server", "/non/existent/command", List.of(), Map.of(), List.of(), Set.of(TransportMode.STDIO)));
    var manager = new McpClientManager(configs, UUID.randomUUID().toString());
    manager.initialize();

    assertThat(manager.getProxiedInstructions()).isEmpty();
  }

  @Test
  void buildEnvironmentVariables_should_skip_inherited_var_not_in_parent() {
    var config = new ProxiedMcpServerConfig("server", "cmd", List.of(),
      Map.of(),
      List.of("MISSING_VAR", "EXISTING_VAR"),
      Set.of(TransportMode.STDIO));
    var parentEnv = Map.of("EXISTING_VAR", "existing_value");

    var result = McpClientManager.buildEnvironmentVariables(config, parentEnv);

    assertThat(result)
      .hasSize(1)
      .containsEntry("EXISTING_VAR", "existing_value")
      .doesNotContainKey("MISSING_VAR");
  }

}
