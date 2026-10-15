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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.authentication.AuthMode;

class HttpServerTransportIntegrationTest {

  private HttpServerTransportProvider httpServer;
  private int testPort;

  @BeforeEach
  void setUp() {
    testPort = findAvailablePort();
    httpServer = new HttpServerTransportProvider(testPort, "127.0.0.1", AuthMode.TOKEN, false, null, false,
      Paths.get("keystore.p12"), "sonarlint", "PKCS12", null, null, null, List.of(), "1.0.0", false);
  }

  @AfterEach
  void tearDown() {
    if (httpServer != null) {
      httpServer.stopServer().join();
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
  void should_respond_to_health_check_without_authentication() throws Exception {
    httpServer.startServer().join();

    await().atMost(5, TimeUnit.SECONDS).until(() -> isServerRunning(httpServer.getServerUrl()));

    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
      var healthUrl = "http://127.0.0.1:" + testPort + McpSecurityFilter.HEALTH_ENDPOINT;
      var request = HttpRequest.newBuilder()
        .uri(URI.create(healthUrl))
        .GET()
        .build();

      var response = client.send(request, HttpResponse.BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(200);
    }
  }

  @Test
  void should_respond_to_info_endpoint_without_authentication() throws Exception {
    httpServer.startServer().join();

    await().atMost(5, TimeUnit.SECONDS).until(() -> isServerRunning(httpServer.getServerUrl()));

    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
      var infoUrl = "http://127.0.0.1:" + testPort + McpSecurityFilter.INFO_ENDPOINT;
      var request = HttpRequest.newBuilder()
        .uri(URI.create(infoUrl))
        .GET()
        .build();

      var response = client.send(request, HttpResponse.BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).isEqualTo("{\"version\":\"1.0.0\"}");
    }
  }

  @Test
  void should_reject_tokenless_mcp_with_disallowed_origin_via_auth_layer() throws Exception {
    httpServer.startServer().join();
    await().atMost(5, TimeUnit.SECONDS).until(() -> isServerRunning(httpServer.getServerUrl()));

    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
      var request = HttpRequest.newBuilder()
        .uri(URI.create(httpServer.getServerUrl()))
        .POST(HttpRequest.BodyPublishers.ofString("{}"))
        .header("Content-Type", "application/json")
        .header("Origin", "https://cursor.com")
        .build();

      var response = client.send(request, HttpResponse.BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(401);
      assertThat(response.statusCode()).isNotEqualTo(403);
      assertThat(response.body()).contains("SonarQube token required");
    }
  }

  @Test
  void should_allow_authenticated_mcp_with_external_origin_on_container_binding() throws Exception {
    var containerPort = findAvailablePort();
    var containerServer = new HttpServerTransportProvider(containerPort, "0.0.0.0", AuthMode.TOKEN, false, null, false,
      Paths.get("keystore.p12"), "sonarlint", "PKCS12", null, null, null, List.of(), "1.0.0", true);

    try {
      containerServer.startServer().join();
      var serverUrl = "http://127.0.0.1:" + containerPort + "/mcp";
      await().atMost(5, TimeUnit.SECONDS).until(() -> isServerRunning(serverUrl));

      try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
        var request = HttpRequest.newBuilder()
          .uri(URI.create(serverUrl))
          .POST(HttpRequest.BodyPublishers.ofString("{}"))
          .header("Content-Type", "application/json")
          .header("Origin", "https://cursor.com")
          .header("Authorization", "Bearer my-token")
          .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isNotEqualTo(403);
      }
    } finally {
      containerServer.stopServer();
    }
  }

  @Test
  void should_reject_unauthenticated_external_origin_on_non_mcp_path() throws Exception {
    var containerPort = findAvailablePort();
    var containerServer = new HttpServerTransportProvider(containerPort, "0.0.0.0", AuthMode.TOKEN, false, null, false,
      Paths.get("keystore.p12"), "sonarlint", "PKCS12", null, null, null, List.of(), "1.0.0", true);

    try {
      containerServer.startServer().join();
      var otherPathUrl = "http://127.0.0.1:" + containerPort + "/other";
      await().atMost(5, TimeUnit.SECONDS).until(() -> isServerRunning("http://127.0.0.1:" + containerPort + "/mcp"));

      try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
        var request = HttpRequest.newBuilder()
          .uri(URI.create(otherPathUrl))
          .POST(HttpRequest.BodyPublishers.ofString("{}"))
          .header("Content-Type", "application/json")
          .header("Origin", "https://cursor.com")
          .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("Origin not allowed");
      }
    } finally {
      containerServer.stopServer();
    }
  }

  @Test
  void should_reject_get_requests_with_token() throws Exception {
    httpServer.startServer().join();

    // Wait for server to be ready
    await().atMost(5, TimeUnit.SECONDS).until(() -> isServerRunning(httpServer.getServerUrl()));

    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
      var request = HttpRequest.newBuilder()
        .uri(URI.create(httpServer.getServerUrl()))
        .header("Authorization", "Bearer test-token")
        .GET()
        .build();

      var response = client.send(request, HttpResponse.BodyHandlers.ofString());

      // Stateless transport only accepts POST
      assertThat(response.statusCode()).isEqualTo(405);
    }
  }

  @Test
  void should_use_custom_host_and_port() {
    var customPort = findAvailablePort();
    var customServer = new HttpServerTransportProvider(customPort, "127.0.0.1", AuthMode.TOKEN, false, null, false,
      Paths.get("keystore.p12"), "sonarlint", "PKCS12", null, null, null, List.of(), "1.0.0", false);

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

  @Test
  void should_return_immediately_when_server_already_running() {
    httpServer.startServer().join();
    await().atMost(5, TimeUnit.SECONDS).until(() -> isServerRunning(httpServer.getServerUrl()));

    // Calling startServer() a second time while running should return a completed future without restarting
    var secondStartFuture = httpServer.startServer();

    assertThat(secondStartFuture).isCompleted();
    assertThat(isServerRunning(httpServer.getServerUrl())).isTrue();
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
