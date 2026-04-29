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

import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class InitializeMetaInjectingClientTransportTest {

  @Test
  void sendMessage_should_populate_meta_on_initialize_request() {
    var capture = new CapturingTransport();
    var transport = new InitializeMetaInjectingClientTransport(capture,
      Map.of("mcp_server_id", "server-123"));

    var message = initializeMessage(null);

    transport.sendMessage(message).block();

    var sent = (McpSchema.JSONRPCRequest) capture.lastMessage.get();
    assertThat(sent.method()).isEqualTo(McpSchema.METHOD_INITIALIZE);
    var init = (McpSchema.InitializeRequest) sent.params();
    assertThat(init.meta()).containsEntry("mcp_server_id", "server-123");
  }

  @Test
  void sendMessage_should_merge_with_pre_existing_meta() {
    var capture = new CapturingTransport();
    var transport = new InitializeMetaInjectingClientTransport(capture,
      Map.of("mcp_server_id", "server-123"));

    var message = initializeMessage(Map.of("existing_key", "existing_value"));

    transport.sendMessage(message).block();

    var sent = (McpSchema.JSONRPCRequest) capture.lastMessage.get();
    var init = (McpSchema.InitializeRequest) sent.params();
    assertThat(init.meta())
      .containsEntry("existing_key", "existing_value")
      .containsEntry("mcp_server_id", "server-123");
  }

  @Test
  void sendMessage_should_override_pre_existing_meta_with_injected_values_on_conflict() {
    var capture = new CapturingTransport();
    var transport = new InitializeMetaInjectingClientTransport(capture,
      Map.of("mcp_server_id", "injected-id"));

    var message = initializeMessage(Map.of("mcp_server_id", "original-id", "other_key", "other_value"));

    transport.sendMessage(message).block();

    var sent = (McpSchema.JSONRPCRequest) capture.lastMessage.get();
    var init = (McpSchema.InitializeRequest) sent.params();
    assertThat(init.meta())
      .containsEntry("mcp_server_id", "injected-id")
      .containsEntry("other_key", "other_value");
  }

  @Test
  void sendMessage_should_not_touch_non_initialize_requests() {
    var capture = new CapturingTransport();
    var transport = new InitializeMetaInjectingClientTransport(capture, Map.of("mcp_server_id", "server-123"));

    var callToolRequest = new McpSchema.CallToolRequest("some_tool", Map.of(), null);
    var message = new McpSchema.JSONRPCRequest("2.0", McpSchema.METHOD_TOOLS_CALL, 2, callToolRequest);

    transport.sendMessage(message).block();

    assertThat(capture.lastMessage.get()).isSameAs(message);
  }

  @Test
  void sendMessage_should_not_touch_notifications() {
    var capture = new CapturingTransport();
    var transport = new InitializeMetaInjectingClientTransport(capture, Map.of("mcp_server_id", "server-123"));

    var notification = new McpSchema.JSONRPCNotification("2.0", McpSchema.METHOD_NOTIFICATION_INITIALIZED, null);

    transport.sendMessage(notification).block();

    assertThat(capture.lastMessage.get()).isSameAs(notification);
  }

  @Test
  void delegate_methods_should_forward_to_wrapped_transport() {
    var capture = new CapturingTransport();
    var transport = new InitializeMetaInjectingClientTransport(capture, Map.of("mcp_server_id", "x"));

    Consumer<Throwable> handler = t -> {};
    transport.setExceptionHandler(handler);
    transport.closeGracefully().block();
    var protocols = transport.protocolVersions();

    assertThat(capture.exceptionHandler.get()).isSameAs(handler);
    assertThat(capture.closeGracefullyCalled.get()).isTrue();
    assertThat(protocols).isEqualTo(capture.protocolVersions());
  }

  private static McpSchema.JSONRPCRequest initializeMessage(Map<String, Object> meta) {
    McpSchema.InitializeRequest init = meta == null
      ? new McpSchema.InitializeRequest("2024-11-05",
          McpSchema.ClientCapabilities.builder().build(),
          new McpSchema.Implementation("client", "1.0"))
      : new McpSchema.InitializeRequest("2024-11-05",
          McpSchema.ClientCapabilities.builder().build(),
          new McpSchema.Implementation("client", "1.0"),
          meta);
    return new McpSchema.JSONRPCRequest("2.0", McpSchema.METHOD_INITIALIZE, 1, init);
  }

  /**
   * Minimal transport stub capturing sent messages and delegate interactions.
   */
  private static final class CapturingTransport implements McpClientTransport {
    final AtomicReference<McpSchema.JSONRPCMessage> lastMessage = new AtomicReference<>();
    final AtomicReference<Consumer<Throwable>> exceptionHandler = new AtomicReference<>();
    final AtomicReference<Boolean> closeGracefullyCalled = new AtomicReference<>(false);

    @Override
    public Mono<Void> connect(Function<Mono<McpSchema.JSONRPCMessage>, Mono<McpSchema.JSONRPCMessage>> handler) {
      return Mono.empty();
    }

    @Override
    public void setExceptionHandler(Consumer<Throwable> handler) {
      exceptionHandler.set(handler);
    }

    @Override
    public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
      lastMessage.set(message);
      return Mono.empty();
    }

    @Override
    public <T> T unmarshalFrom(Object data, TypeRef<T> typeRef) {
      return null;
    }

    @Override
    public Mono<Void> closeGracefully() {
      closeGracefullyCalled.set(true);
      return Mono.empty();
    }

    @Override
    public List<String> protocolVersions() {
      return List.of("2024-11-05");
    }
  }

}
