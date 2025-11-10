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

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.authentication.AuthMode;

class HttpsServerTransportIntegrationTest {

  private HttpServerTransportProvider httpsServer;
  private int testPort;

  @BeforeEach
  void setUp() throws Exception {
    // Use test keystore from resources
    var keystoreResource = getClass().getResource("/ssl/test-keystore.p12");
    if (keystoreResource == null) {
      throw new IllegalStateException("Test keystore not found at /ssl/test-keystore.p12");
    }

    var testKeystorePath = Paths.get(keystoreResource.toURI());
    testPort = findAvailablePort();
    
    httpsServer = new HttpServerTransportProvider(
      testPort,
      "127.0.0.1",
      AuthMode.TOKEN,
      true,
      testKeystorePath,
      "test123",
      "PKCS12",
      Path.of(""),
      "",
      ""
    );
  }

  @AfterEach
  void tearDown() {
    if (httpsServer != null) {
      httpsServer.stopServer();
    }
  }

  @Test
  void should_start_https_server_with_valid_certificate() throws Exception {
    var startFuture = httpsServer.startServer();

    // Wait for the future to complete
    startFuture.get(5, TimeUnit.SECONDS);
    
    assertThat(startFuture).isCompleted();
    
    var expectedUrl = "https://127.0.0.1:" + testPort + "/mcp";
    assertThat(httpsServer.getServerUrl()).isEqualTo(expectedUrl);
  }

  @Test
  void should_stop_https_server_cleanly() throws Exception {
    var startFuture = httpsServer.startServer();
    startFuture.get(5, TimeUnit.SECONDS);

    assertThat(startFuture).isCompleted();

    // Should stop without exceptions
    httpsServer.stopServer().get();
  }

  @Test
  void should_handle_https_restart() throws Exception {
    var startFuture1 = httpsServer.startServer();
    startFuture1.get(5, TimeUnit.SECONDS);
    assertThat(startFuture1).isCompleted();

    httpsServer.stopServer();
    
    // Wait a bit for port to be released
    Thread.sleep(500);

    var startFuture2 = httpsServer.startServer();
    startFuture2.get(5, TimeUnit.SECONDS);
    assertThat(startFuture2).isCompleted();
  }

  @Test
  void should_start_with_non_existent_keystore() throws Exception {
    // Create server with non-existent keystore path
    var nonExistentKeystore = Paths.get("/tmp/non-existent-keystore.p12");
    var serverWithMissingKeystore = new HttpServerTransportProvider(
      findAvailablePort(),
      "127.0.0.1",
      AuthMode.TOKEN,
      true,
      nonExistentKeystore,
      "test123",
      "PKCS12",
      Path.of(""),
      "",
      ""
    );

    try {
      // Should start without crashing
      var startFuture = serverWithMissingKeystore.startServer();
      startFuture.get(5, TimeUnit.SECONDS);
      
      assertThat(startFuture).isCompleted();
      assertThat(serverWithMissingKeystore.getServerUrl()).startsWith("https://");
    } finally {
      serverWithMissingKeystore.stopServer();
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

