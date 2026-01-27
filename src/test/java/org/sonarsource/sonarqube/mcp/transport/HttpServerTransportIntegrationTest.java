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
package org.sonarsource.sonarqube.mcp.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.modelcontextprotocol.spec.DefaultMcpStreamableServerSessionFactory;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStreamableServerSession;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.authentication.AuthMode;
import reactor.core.publisher.Mono;

class HttpServerTransportIntegrationTest {

  private HttpServerTransportProvider httpServer;
  private int testPort;

  @BeforeEach
  void setUp() {
    // Use a random available port for testing
    testPort = findAvailablePort();
    httpServer = new HttpServerTransportProvider(testPort, "127.0.0.1", AuthMode.TOKEN, false,
      Paths.get("keystore.p12"), "sonarlint", "PKCS12", null, null, null);
  }

  @AfterEach
  void tearDown() {
    if (httpServer != null) {
      httpServer.stopServer();
    }
  }

  @Test
  void should_start_and_stop_http_server() {
    var startFuture = httpServer.startServer();

    // Wait for server to be ready
    await().atMost(5, TimeUnit.SECONDS).until(() -> isServerRunning(httpServer.getServerUrl()));

    assertThat(startFuture).isCompleted();

    var expectedUrl = "http://127.0.0.1:" + testPort + "/mcp";
    assertThat(httpServer.getServerUrl()).isEqualTo(expectedUrl);

    httpServer.stopServer();

    // Wait for server to be stopped
    await().atMost(5, TimeUnit.SECONDS).until(() -> !isServerRunning(httpServer.getServerUrl()));
  }

  @Test
  void should_handle_options_request_for_cors() throws Exception {
    httpServer.startServer().join();

    // Wait for server to be ready
    await().atMost(5, TimeUnit.SECONDS).until(() -> isServerRunning(httpServer.getServerUrl()));

    // Send OPTIONS request
    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
      var request = HttpRequest.newBuilder()
        .uri(URI.create(httpServer.getServerUrl()))
        .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
        .build();

      var response = client.send(request, HttpResponse.BodyHandlers.ofString());

      // Should respond with 200 OK and appropriate CORS headers
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
        .isPresent()
        .get()
        .isEqualTo("*");
      assertThat(response.headers().firstValue("Access-Control-Allow-Methods"))
        .isPresent();
      assertThat(response.headers().firstValue("Access-Control-Allow-Headers"))
        .isPresent();
    }
  }

  @Test
  void should_respond_to_get_request() throws Exception {
    httpServer.startServer().join();

    // Wait for server to be ready
    await().atMost(5, TimeUnit.SECONDS).until(() -> isServerRunning(httpServer.getServerUrl()));

    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
      var request = HttpRequest.newBuilder()
        .uri(URI.create(httpServer.getServerUrl()))
        .GET()
        .build();

      // Note: This should return 200 even without MCP initialization
      // because the servlet container is running
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());

      // Server should respond
      assertThat(response.statusCode()).isBetween(200, 599);
    }
  }

  @Test
  void should_allow_unknown_properties() throws Exception {
    httpServer.startServer().join();
    httpServer.getTransportProvider().setSessionFactory(new DefaultMcpStreamableServerSessionFactory(Duration.ofSeconds(2), new McpStreamableServerSession.InitRequestHandler() {
      @Override
      public Mono<McpSchema.InitializeResult> handle(McpSchema.InitializeRequest initializeRequest) {
        return Mono.empty();
      }
    }, Map.of(), Map.of()));

    // Wait for server to be ready
    await().atMost(5, TimeUnit.SECONDS).until(() -> isServerRunning(httpServer.getServerUrl()));

    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
      var request = HttpRequest.newBuilder()
        .uri(URI.create(httpServer.getServerUrl()))
        .header("Accept", "text/event-stream, application/json")
        .header("SONARQUBE_TOKEN", "XXX")
        .POST(HttpRequest.BodyPublishers.ofString("""
          {
            "jsonrpc": "2.0",
            "id": 1,
            "method": "initialize",
            "params": {
              "capabilities": {
                "elicitation": {
                  "XXX": {}
                }
              }
            }
          }"""))
        .build();

      var response = client.send(request, HttpResponse.BodyHandlers.ofString());

      assertThat(response.statusCode()).isBetween(200, 299);
    }
  }

  @Test
  void should_use_custom_host_and_port() {
    var customPort = findAvailablePort();
    var customServer = new HttpServerTransportProvider(customPort, "127.0.0.1", AuthMode.TOKEN, false,
      Paths.get("keystore.p12"), "sonarlint", "PKCS12", null, null, null);

    try {
      customServer.startServer().join();

      var expectedUrl = "http://127.0.0.1:" + customPort + "/mcp";
      assertThat(customServer.getServerUrl()).isEqualTo(expectedUrl);

      // Verify server is actually listening on the correct port
      await().atMost(5, TimeUnit.SECONDS).until(() -> isServerRunning(customServer.getServerUrl()));
    } finally {
      customServer.stopServer();
    }
  }

  @Test
  void should_handle_multiple_start_stop_cycles() {
    for (int i = 0; i < 3; i++) {
      var startFuture = httpServer.startServer();

      // Wait for server to be ready
      await().atMost(5, TimeUnit.SECONDS).until(() -> isServerRunning(httpServer.getServerUrl()));

      assertThat(startFuture).isCompleted();

      httpServer.stopServer();

      await().atMost(5, TimeUnit.SECONDS).until(() -> !isServerRunning(httpServer.getServerUrl()));
    }
  }

  private boolean isServerRunning(String serverUrl) {
    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()) {
      var request = HttpRequest.newBuilder()
        .uri(URI.create(serverUrl))
        .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
        .timeout(Duration.ofSeconds(1))
        .build();

      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      return response.statusCode() >= 200 && response.statusCode() < 600;
    } catch (Exception e) {
      return false;
    }
  }

  private int findAvailablePort() {
    try (var serverSocket = new ServerSocket(0)) {
      return serverSocket.getLocalPort();
    } catch (IOException e) {
      throw new RuntimeException("Failed to find available port", e);
    }
  }
}
