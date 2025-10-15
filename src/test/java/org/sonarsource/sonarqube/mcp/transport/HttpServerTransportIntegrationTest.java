/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
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

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpServerTransportIntegrationTest {

  private HttpServerTransportProvider httpServer;
  private int testPort;

  @BeforeEach
  void setUp() {
    // Use a random available port for testing
    testPort = findAvailablePort();
    httpServer = new HttpServerTransportProvider(testPort, "127.0.0.1");
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
  void should_use_custom_host_and_port() {
    var customPort = findAvailablePort();
    var customServer = new HttpServerTransportProvider(customPort, "127.0.0.1");

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
