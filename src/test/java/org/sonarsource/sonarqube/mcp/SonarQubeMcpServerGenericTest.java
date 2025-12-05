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
package org.sonarsource.sonarqube.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.sonarsource.sonarqube.mcp.authentication.SessionTokenStore;
import org.sonarsource.sonarqube.mcp.context.RequestContext;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.transport.StdioServerTransportProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SonarQubeMcpServerGenericTest {

  @AfterEach
  void tearDown() {
    RequestContext.clear();
    SessionTokenStore.getInstance().clear();
  }

  @SonarQubeMcpServerTest
  void get_should_return_server_api_in_stdio_mode(SonarQubeMcpServerTestHarness harness) {
    var environment = createStdioEnvironment(harness.getMockSonarQubeServer().baseUrl());
    harness.prepareMockWebServer(environment);

    var server = new SonarQubeMcpServer(
      new StdioServerTransportProvider(new ObjectMapper(), null),
      null,
      environment);
    server.start();

    var serverApi = server.get();

    assertThat(serverApi).isNotNull();
  }

  @SonarQubeMcpServerTest
  void get_should_throw_when_no_request_context_in_http_mode(SonarQubeMcpServerTestHarness harness) {
    var environment = createHttpEnvironment(harness.getMockSonarQubeServer().baseUrl());
    harness.prepareMockWebServer(environment);

    var server = new SonarQubeMcpServer(environment);

    // No RequestContext set - should throw
    assertThatThrownBy(server::get)
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("No request context");
  }

  @SonarQubeMcpServerTest
  void get_should_throw_when_no_token_for_session_in_http_mode(SonarQubeMcpServerTestHarness harness) {
    var environment = createHttpEnvironment(harness.getMockSonarQubeServer().baseUrl());
    harness.prepareMockWebServer(environment);

    var server = new SonarQubeMcpServer(environment);

    // Set RequestContext with session ID but don't store token in SessionTokenStore
    RequestContext.set("unknown-session-id");

    assertThatThrownBy(server::get)
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("No token found for session");
  }

  @SonarQubeMcpServerTest
  void get_should_return_server_api_when_session_has_token_in_http_mode(SonarQubeMcpServerTestHarness harness) {
    var environment = createHttpEnvironment(harness.getMockSonarQubeServer().baseUrl());
    harness.prepareMockWebServer(environment);

    var server = new SonarQubeMcpServer(environment);

    // Simulate what AuthenticationFilter does: store token for session
    var sessionId = "test-session-123";
    var token = "squ_test_token";
    SessionTokenStore.getInstance().setTokenIfValid(sessionId, token);

    // Simulate what tool handler does: set session ID in RequestContext
    RequestContext.set(sessionId);

    var serverApi = server.get();

    assertThat(serverApi).isNotNull();
  }

  @SonarQubeMcpServerTest
  void shutdown_should_be_idempotent(SonarQubeMcpServerTestHarness harness) {
    var environment = createStdioEnvironment(harness.getMockSonarQubeServer().baseUrl());
    harness.prepareMockWebServer(environment);

    var server = new SonarQubeMcpServer(
      new StdioServerTransportProvider(new ObjectMapper(), null),
      null,
      environment);
    server.start();

    // Calling shutdown multiple times should not throw
    server.shutdown();
    server.shutdown();
    server.shutdown();
  }

  @SonarQubeMcpServerTest
  void shutdown_should_work_before_start(SonarQubeMcpServerTestHarness harness) {
    var environment = createStdioEnvironment(harness.getMockSonarQubeServer().baseUrl());
    harness.prepareMockWebServer(environment);

    var server = new SonarQubeMcpServer(
      new StdioServerTransportProvider(new ObjectMapper(), null),
      null,
      environment);

    // Shutdown before start should not throw
    server.shutdown();
  }

  private Map<String, String> createStdioEnvironment(String baseUrl) {
    var environment = new HashMap<String, String>();
    environment.put("STORAGE_PATH", System.getProperty("java.io.tmpdir"));
    environment.put("SONARQUBE_TOKEN", "test-token");
    environment.put("SONARQUBE_URL", baseUrl);
    return environment;
  }

  private Map<String, String> createHttpEnvironment(String baseUrl) {
    var environment = createStdioEnvironment(baseUrl);
    environment.put("SONARQUBE_TRANSPORT", "http");
    environment.put("SONARQUBE_HTTP_PORT", "18080");
    environment.put("SONARQUBE_HTTP_HOST", "127.0.0.1");
    return environment;
  }

}

