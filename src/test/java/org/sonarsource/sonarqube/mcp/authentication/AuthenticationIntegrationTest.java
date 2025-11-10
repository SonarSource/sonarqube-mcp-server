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
package org.sonarsource.sonarqube.mcp.authentication;

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
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.transport.HttpServerTransportProvider;

class AuthenticationIntegrationTest {

  private HttpServerTransportProvider httpServer;
  private int testPort;

  @AfterEach
  void tearDown() {
    if (httpServer != null) {
      httpServer.stopServer();
    }
  }

  @Test
  void should_allow_request_with_sonarqube_token_header() throws Exception {
    testPort = findAvailablePort();
    httpServer = new HttpServerTransportProvider(testPort, "127.0.0.1", AuthMode.TOKEN, false, 
      java.nio.file.Paths.get("keystore.p12"), "sonarlint", "PKCS12", null, null, null);
    httpServer.startServer().join();
    await().atMost(5, TimeUnit.SECONDS).until(this::isServerRunning);

    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
      var request = HttpRequest.newBuilder()
        .uri(URI.create(httpServer.getServerUrl()))
        .POST(HttpRequest.BodyPublishers.ofString("{}"))
        .header("Content-Type", "application/json")
        .header("SONARQUBE_TOKEN", "squ_my-sonarqube-token-123")
        .build();

      var response = client.send(request, HttpResponse.BodyHandlers.ofString());

      assertThat(response.statusCode()).isNotEqualTo(401);
    }
  }

  @Test
  void should_reject_request_without_token_header() throws Exception {
    testPort = findAvailablePort();
    httpServer = new HttpServerTransportProvider(testPort, "127.0.0.1", AuthMode.TOKEN, false, 
      java.nio.file.Paths.get("keystore.p12"), "sonarlint", "PKCS12", null, null, null);
    httpServer.startServer().join();
    await().atMost(5, TimeUnit.SECONDS).until(this::isServerRunning);

    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
      var request = HttpRequest.newBuilder()
        .uri(URI.create(httpServer.getServerUrl()))
        .POST(HttpRequest.BodyPublishers.ofString("{}"))
        .header("Content-Type", "application/json")
        // No SONARQUBE_TOKEN header
        .build();

      var response = client.send(request, HttpResponse.BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(401);
      assertThat(response.headers().firstValue("WWW-Authenticate"))
        .isPresent()
        .get()
        .asString()
        .contains("Bearer");
      var body = response.body();
      assertThat(body)
        .contains("\"error\":\"unauthorized\"")
        .contains("SonarQube token required");
    }
  }

  @Test
  void should_always_allow_options_requests_regardless_of_auth() throws Exception {
    testPort = findAvailablePort();
    httpServer = new HttpServerTransportProvider(testPort, "127.0.0.1", AuthMode.TOKEN, false, 
      java.nio.file.Paths.get("keystore.p12"), "sonarlint", "PKCS12", null, null, null);
    httpServer.startServer().join();
    await().atMost(5, TimeUnit.SECONDS).until(this::isServerRunning);

    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
      // OPTIONS request without Authorization header
      var request = HttpRequest.newBuilder()
        .uri(URI.create(httpServer.getServerUrl()))
        .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
        .build();

      var response = client.send(request, HttpResponse.BodyHandlers.ofString());

      // OPTIONS should always succeed (CORS preflight)
      assertThat(response.statusCode()).isEqualTo(200);
    }
  }

  @Test
  void should_reject_requests_with_empty_token() throws Exception {
    testPort = findAvailablePort();
    httpServer = new HttpServerTransportProvider(testPort, "127.0.0.1", AuthMode.TOKEN, false, 
      java.nio.file.Paths.get("keystore.p12"), "sonarlint", "PKCS12", null, null, null);
    
    httpServer.startServer().join();
    await().atMost(5, TimeUnit.SECONDS).until(this::isServerRunning);

    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
      // Test with empty token
      var request = HttpRequest.newBuilder()
        .uri(URI.create(httpServer.getServerUrl()))
        .POST(HttpRequest.BodyPublishers.ofString("{}"))
        .header("Content-Type", "application/json")
        .header("SONARQUBE_TOKEN", "")
        .build();

      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(401);
    }
  }

  private boolean isServerRunning() {
    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()) {
      var request = HttpRequest.newBuilder()
        .uri(URI.create(httpServer.getServerUrl()))
        .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
        .timeout(Duration.ofSeconds(1))
        .build();

      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
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


