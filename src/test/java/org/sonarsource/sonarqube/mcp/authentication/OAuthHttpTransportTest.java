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
package org.sonarsource.sonarqube.mcp.authentication;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.transport.HttpServerTransportProvider;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthHttpTransportTest {
  @Test
  void should_forward_only_cloud_token_into_context_and_preserve_read_scope_on_mcp_post() throws Exception {
    var context = new AtomicReference<McpTransportContext>();
    var authentication = new OAuthRequestAuthentication("cloud-token-b", Set.of("read:all"));
    var metadata = new OAuthProtectedResourceMetadata("https://api.sc-dev9.io/mcp", "https://auth-dev9.sc-dev9.io/");
    int port;
    try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
    var server = new HttpServerTransportProvider(port, "127.0.0.1", AuthMode.OAUTH, true, null, false,
      Paths.get("unused.p12"), "unused", "PKCS12", null, null, null, List.of(), "1.0.0", false, metadata,
      token -> {
        assertThat(token).isEqualTo("mcp-token-a");
        return authentication;
      });
    server.getFilteringTransport(List.of()).setMcpHandler(new McpStatelessServerHandler() {
      @Override
      public Mono<McpSchema.JSONRPCResponse> handleRequest(McpTransportContext requestContext, McpSchema.JSONRPCRequest request) {
        context.set(requestContext);
        return Mono.just(new McpSchema.JSONRPCResponse("2.0", request.id(), Map.of(), null));
      }

      @Override
      public Mono<Void> handleNotification(McpTransportContext requestContext, McpSchema.JSONRPCNotification notification) {
        return Mono.empty();
      }
    });
    try (var client = HttpClient.newHttpClient()) {
      server.startServer().join();
      var response = client.send(HttpRequest.newBuilder(URI.create(server.getServerUrl()))
        .header("Authorization", "Bearer mcp-token-a").header("SONARQUBE_READ_ONLY", "false")
        .header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream")
        .POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}")).build(),
        HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(context.get()).isNotNull();
      assertThat(context.get().get(HttpServerTransportProvider.CONTEXT_TOKEN_KEY)).isEqualTo("cloud-token-b");
      assertThat(context.get().get(HttpServerTransportProvider.CONTEXT_READ_ONLY_KEY)).isEqualTo(true);
      assertThat(context.get().get(HttpServerTransportProvider.CONTEXT_ORG_KEY)).isNull();
    } finally {
      server.stopServer().join();
    }
  }
}

