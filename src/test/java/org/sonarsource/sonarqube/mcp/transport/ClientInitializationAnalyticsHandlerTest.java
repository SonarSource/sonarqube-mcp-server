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
package org.sonarsource.sonarqube.mcp.transport;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import java.util.function.Supplier;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.analytics.AnalyticsService;
import org.sonarsource.sonarqube.mcp.analytics.HttpAnalyticsContext;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ClientInitializationAnalyticsHandlerTest {
  private final AnalyticsService analytics = mock(AnalyticsService.class);
  private final McpStatelessServerHandler delegate = mock(McpStatelessServerHandler.class);
  private final ClientInitializationAnalyticsHandler handler = new ClientInitializationAnalyticsHandler(delegate, analytics, ignored -> null);
  private final HttpAnalyticsContext attribution = new HttpAnalyticsContext("oauth", "client-key");
  private final McpTransportContext context = McpTransportContext.create(Map.of(HttpAnalyticsContext.CONTEXT_KEY, attribution));

  @Test
  void it_should_preserve_client_name_and_version_without_storing_session_state() {
    var request = initialize(Map.of("name", "Custom Client ", "version", "2026.10+preview"));
    var response = McpSchema.JSONRPCResponse.result(1, Map.of("serverInfo", Map.of("name", "server")));
    when(delegate.handleRequest(context, request)).thenReturn(Mono.just(response));

    assertThat(handler.handleRequest(context, request).block()).isSameAs(response);

    verify(analytics).notifyClientInitialized(eq(attribution), eq("Custom Client "),
      eq("2026.10+preview"), anyLong(), any());
  }

  @Test
  void it_should_defer_identity_lookup_and_capture_each_request_context() {
    var firstApi = mock(ServerApi.class);
    var secondApi = mock(ServerApi.class);
    var first = McpTransportContext.create(Map.of(HttpAnalyticsContext.CONTEXT_KEY, attribution, "api", firstApi));
    var second = McpTransportContext.create(Map.of(HttpAnalyticsContext.CONTEXT_KEY, attribution, "api", secondApi));
    var initializationHandler = new ClientInitializationAnalyticsHandler(delegate, analytics, c -> (ServerApi) c.get("api"));
    var request = initialize(Map.of("name", "client", "version", "1"));
    var response = McpSchema.JSONRPCResponse.result(1, Map.of());
    when(delegate.handleRequest(first, request)).thenReturn(Mono.just(response));
    when(delegate.handleRequest(second, request)).thenReturn(Mono.just(response));
    assertThat(initializationHandler.handleRequest(first, request).block()).isSameAs(response);
    assertThat(initializationHandler.handleRequest(second, request).block()).isSameAs(response);
    verifyNoInteractions(firstApi, secondApi);
    @SuppressWarnings("unchecked")
    var suppliers = ArgumentCaptor.forClass((Class<Supplier<ServerApi>>) (Class<?>) Supplier.class);
    verify(analytics, times(2)).notifyClientInitialized(any(), any(), any(), anyLong(), suppliers.capture());
    assertThat(suppliers.getAllValues().getFirst().get()).isSameAs(firstApi);
    assertThat(suppliers.getAllValues().getLast().get()).isSameAs(secondApi);
  }

  @ParameterizedTest
  @ValueSource(strings = {"tools/list", "ping"})
  void it_should_not_emit_initialization_for_other_methods(String method) {
    var request = new McpSchema.JSONRPCRequest("2.0", method, 1, Map.of());
    var response = McpSchema.JSONRPCResponse.result(1, Map.of());
    when(delegate.handleRequest(context, request)).thenReturn(Mono.just(response));
    assertThat(handler.handleRequest(context, request).block()).isSameAs(response);
    verifyNoInteractions(analytics);
  }

  @Test
  void it_should_not_emit_initialization_when_the_request_fails() {
    var request = initialize(Map.of("name", "client", "version", "1"));
    var response = McpSchema.JSONRPCResponse.error(1, new McpSchema.JSONRPCResponse.JSONRPCError(-32602, "invalid", null));
    when(delegate.handleRequest(context, request)).thenReturn(Mono.just(response));
    assertThat(handler.handleRequest(context, request).block()).isSameAs(response);
    verifyNoInteractions(analytics);
  }

  @Test
  void it_should_not_change_the_handshake_when_analytics_fails() {
    var request = initialize(Map.of("name", "client", "version", "1"));
    var response = McpSchema.JSONRPCResponse.result(1, Map.of());
    when(delegate.handleRequest(context, request)).thenReturn(Mono.just(response));
    doThrow(new IllegalStateException("stopped")).when(analytics).notifyClientInitialized(any(), any(), any(), anyLong(), any());
    assertThat(handler.handleRequest(context, request).block()).isSameAs(response);
  }

  @Test
  void it_should_delegate_notifications_without_events() {
    var notification = new McpSchema.JSONRPCNotification("2.0", "notifications/initialized", null);
    when(delegate.handleNotification(context, notification)).thenReturn(Mono.empty());
    handler.handleNotification(context, notification).block();
    verify(delegate).handleNotification(context, notification);
    verifyNoInteractions(analytics);
  }

  private static McpSchema.JSONRPCRequest initialize(Map<String, Object> client) {
    return new McpSchema.JSONRPCRequest("2.0", "initialize", 1, Map.of("protocolVersion", "2025-06-18", "capabilities", Map.of(), "clientInfo", client));
  }
}
