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

import io.modelcontextprotocol.common.McpTransportContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.transport.HttpServerTransportProvider;
import org.sonarsource.sonarqube.mcp.transport.StdioServerTransportProvider;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class SonarQubeMcpServerGenericTest {

  // No per-test teardown needed — the stateless HTTP mode has no global session state.

  @SonarQubeMcpServerTest
  void get_should_return_server_api_in_stdio_mode(SonarQubeMcpServerTestHarness harness) {
    var environment = createStdioEnvironment(harness.getMockSonarQubeServer().baseUrl());
    harness.prepareMockWebServer(environment);

    var server = new SonarQubeMcpServer(
      new StdioServerTransportProvider(null),
      null,
      environment);
    server.start();

    var serverApi = server.get();

    assertThat(serverApi).isNotNull();
  }

  @SonarQubeMcpServerTest
  void should_log_sanitized_config_when_elevated_debug_enabled(SonarQubeMcpServerTestHarness harness) {
    System.setProperty("SONARQUBE_DEBUG_ENABLED", "true");
    var originalErr = System.err;
    var errBuffer = new ByteArrayOutputStream();
    System.setErr(new PrintStream(errBuffer, true, StandardCharsets.UTF_8));

    try {
      var environment = createStdioEnvironment(harness.getMockSonarQubeServer().baseUrl());
      harness.prepareMockWebServer(environment);

      var server = new SonarQubeMcpServer(
        new StdioServerTransportProvider(null),
        null,
        environment);
      server.start();
      var configuration = server.getMcpConfiguration();

      assertThat(System.getProperty("os.name")).isNotNull().isNotEmpty();
      assertThat(configuration.getUserAgent()).isNotNull().isNotEmpty();
      assertThat(configuration.getEnabledToolsets().toString()).isNotNull().isNotEmpty();
      assertThat(configuration.getAppVersion()).isNotNull().isNotEmpty();
      assertThat(configuration.getStoragePath().toString()).isNotNull().isNotEmpty();
      assertThat(configuration.getLogFilePath().toAbsolutePath().toString()).isNotNull().isNotEmpty();

      await().atMost(2, SECONDS).untilAsserted(() -> {
        var stderrOutput = errBuffer.toString(StandardCharsets.UTF_8);
        var proxySelector = java.net.ProxySelector.getDefault();
        var proxySelectorName = proxySelector != null ? proxySelector.getClass().getName() : "none";
        assertThat(stderrOutput)
          .contains("SSL/TLS - OS: " + System.getProperty("os.name"))
          .contains("SSL/TLS configured - protocol: TLS")
          .contains("Proxy selector: " + proxySelectorName)
          .contains("HTTP client user agent: " + configuration.getUserAgent())
          .contains("Enabled toolsets: " + configuration.getEnabledToolsets())
          .contains("Advanced analysis: false")
          .contains("Telemetry enabled: false")
          .contains("App version: " + configuration.getAppVersion())
          .contains("Storage path: " + configuration.getStoragePath())
          .contains("Log file: " + configuration.getLogFilePath().toAbsolutePath())
          .contains("IDE port: " + (configuration.getSonarQubeIdePort() != null ? configuration.getSonarQubeIdePort() : "not set"))
          .contains("================================");
      });

      server.shutdown();
    } finally {
      System.clearProperty("SONARQUBE_DEBUG_ENABLED");
      System.setErr(originalErr);
    }
  }

  @SonarQubeMcpServerTest
  void shutdown_should_be_idempotent(SonarQubeMcpServerTestHarness harness) {
    var environment = createStdioEnvironment(harness.getMockSonarQubeServer().baseUrl());
    harness.prepareMockWebServer(environment);

    var server = new SonarQubeMcpServer(
      new StdioServerTransportProvider(null),
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
      new StdioServerTransportProvider(null),
      null,
      environment);

    // Shutdown before start should not throw
    server.shutdown();
  }

  @SonarQubeMcpServerTest
  void should_skip_analyzer_download_when_analysis_tools_disabled(SonarQubeMcpServerTestHarness harness) throws Exception {
    var environment = createStdioEnvironment(harness.getMockSonarQubeServer().baseUrl());
    environment.put("SONARQUBE_TOOLSETS", "projects");
    harness.prepareMockWebServer(environment);

    var server = new SonarQubeMcpServer(
      new StdioServerTransportProvider(null),
      null,
      environment);
    server.start();

    server.waitForInitialization();

    // Verify that no analyzer synchronization was attempted
    assertThat(harness.getMockSonarQubeServer().hasReceivedInstalledPluginsRequest()).isFalse();

    var config = server.getMcpConfiguration();
    var enabledToolNames = server.getSupportedTools().stream()
      .filter(tool -> config.isToolCategoryEnabled(tool.getCategory()))
      .map(tool -> tool.definition().name())
      .toList();
    assertThat(enabledToolNames)
      .isNotEmpty()
      .doesNotContain("analyze_code_snippet")
      .doesNotContain("analyze_file_list");

    server.shutdown();
  }

  @SonarQubeMcpServerTest
  void should_download_analyzers_when_analysis_tools_enabled(SonarQubeMcpServerTestHarness harness) throws Exception {
    var environment = createStdioEnvironment(harness.getMockSonarQubeServer().baseUrl());
    // Explicitly enable ANALYSIS tools (default behavior)
    environment.put("SONARQUBE_TOOLSETS", "analysis,issues,projects,quality-gates");
    harness.prepareMockWebServer(environment);

    var server = new SonarQubeMcpServer(
      new StdioServerTransportProvider(null),
      null,
      environment);
    server.start();

    server.waitForInitialization();

    // Verify that analyzer synchronization was attempted
    assertThat(harness.getMockSonarQubeServer().hasReceivedInstalledPluginsRequest()).isTrue();

    var config = server.getMcpConfiguration();
    var enabledToolNames = server.getSupportedTools().stream()
      .filter(tool -> config.isToolCategoryEnabled(tool.getCategory()))
      .map(tool -> tool.definition().name())
      .toList();
    assertThat(enabledToolNames)
      .contains("analyze_code_snippet");

    server.shutdown();
  }

  @SonarQubeMcpServerTest
  void get_should_return_server_api_in_http_mode_when_context_has_valid_token(SonarQubeMcpServerTestHarness harness) {
    var environment = createHttpEnvironment(harness.getMockSonarQubeServer().baseUrl());
    harness.prepareMockWebServer(environment);

    var server = new SonarQubeMcpServer(environment);
    server.start();

    var context = McpTransportContext.create(Map.of(HttpServerTransportProvider.CONTEXT_TOKEN_KEY, "squ_valid_token"));
    server.withTransportContext(context, () ->
      assertThat(server.get()).isNotNull());

    server.shutdown();
  }

  @SonarQubeMcpServerTest
  void get_should_throw_in_http_mode_when_context_token_is_blank(SonarQubeMcpServerTestHarness harness) {
    var environment = createHttpEnvironment(harness.getMockSonarQubeServer().baseUrl());
    harness.prepareMockWebServer(environment);

    var server = new SonarQubeMcpServer(environment);
    server.start();

    var context = McpTransportContext.create(Map.of(HttpServerTransportProvider.CONTEXT_TOKEN_KEY, ""));
    assertThatThrownBy(() -> server.withTransportContext(context, server::get))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("No SONARQUBE_TOKEN in transport context");

    server.shutdown();
  }

  @SonarQubeMcpServerTest
  void get_should_throw_in_http_mode_when_called_outside_tool_execution(SonarQubeMcpServerTestHarness harness) {
    var environment = createHttpEnvironment(harness.getMockSonarQubeServer().baseUrl());
    harness.prepareMockWebServer(environment);

    var server = new SonarQubeMcpServer(environment);
    server.start();

    assertThatThrownBy(server::get)
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("No transport context available for HTTP stateless mode");

    server.shutdown();
  }

  @SonarQubeMcpServerTest
  void shutdown_should_be_idempotent_in_http_mode(SonarQubeMcpServerTestHarness harness) {
    var environment = createHttpEnvironment(harness.getMockSonarQubeServer().baseUrl());
    harness.prepareMockWebServer(environment);

    var server = new SonarQubeMcpServer(environment);
    server.start();

    server.shutdown();
    server.shutdown();
    server.shutdown();
  }

  @SonarQubeMcpServerTest
  void shutdown_should_work_before_start_in_http_mode(SonarQubeMcpServerTestHarness harness) {
    var environment = createHttpEnvironment(harness.getMockSonarQubeServer().baseUrl());
    harness.prepareMockWebServer(environment);

    var server = new SonarQubeMcpServer(environment);

    server.shutdown();
  }

  @SonarQubeMcpServerTest
  void get_should_return_server_api_in_http_mode_when_server_org_is_set_and_no_org_header(SonarQubeMcpServerTestHarness harness) {
    var environment = createSonarCloudHttpEnvironment();
    environment.put("SONARQUBE_ORG", "my-org");
    harness.prepareMockWebServer(environment);

    var server = new SonarQubeMcpServer(environment);
    server.start();

    var context = McpTransportContext.create(Map.of(HttpServerTransportProvider.CONTEXT_TOKEN_KEY, "squ_valid_token"));
    server.withTransportContext(context, () ->
      assertThat(server.get()).isNotNull());

    server.shutdown();
  }

  @SonarQubeMcpServerTest
  void get_should_throw_in_http_mode_when_server_org_is_set_and_client_sends_org_header(SonarQubeMcpServerTestHarness harness) {
    var environment = createSonarCloudHttpEnvironment();
    environment.put("SONARQUBE_ORG", "server-org");
    harness.prepareMockWebServer(environment);

    var server = new SonarQubeMcpServer(environment);
    server.start();

    var context = McpTransportContext.create(Map.of(
      HttpServerTransportProvider.CONTEXT_TOKEN_KEY, "squ_valid_token",
      HttpServerTransportProvider.CONTEXT_ORG_KEY, "client-org"));
    assertThatThrownBy(() -> server.withTransportContext(context, server::get))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("SONARQUBE_ORG header is not allowed")
      .hasMessageContaining("server-org");

    server.shutdown();
  }

  @SonarQubeMcpServerTest
  void get_should_return_server_api_in_http_mode_when_no_server_org_and_client_sends_org_header(SonarQubeMcpServerTestHarness harness) {
    var environment = createSonarCloudHttpEnvironment();
    harness.prepareMockWebServer(environment);

    var server = new SonarQubeMcpServer(environment);
    server.start();

    var context = McpTransportContext.create(Map.of(
      HttpServerTransportProvider.CONTEXT_TOKEN_KEY, "squ_valid_token",
      HttpServerTransportProvider.CONTEXT_ORG_KEY, "client-org"));
    server.withTransportContext(context, () ->
      assertThat(server.get()).isNotNull());

    server.shutdown();
  }

  @SonarQubeMcpServerTest
  void get_should_throw_in_http_mode_when_no_server_org_and_client_sends_no_org_header_for_sonarcloud(SonarQubeMcpServerTestHarness harness) {
    var environment = createSonarCloudHttpEnvironment();
    harness.prepareMockWebServer(environment);

    var server = new SonarQubeMcpServer(environment);
    server.start();

    var context = McpTransportContext.create(Map.of(HttpServerTransportProvider.CONTEXT_TOKEN_KEY, "squ_valid_token"));
    assertThatThrownBy(() -> server.withTransportContext(context, server::get))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("SONARQUBE_ORG header is required");

    server.shutdown();
  }

  private Map<String, String> createSonarCloudHttpEnvironment() {
    var environment = new HashMap<String, String>();
    environment.put("STORAGE_PATH", System.getProperty("java.io.tmpdir"));
    environment.put("SONARQUBE_TRANSPORT", "http");
    environment.put("SONARQUBE_HTTP_PORT", String.valueOf(findAvailablePort()));
    environment.put("SONARQUBE_HTTP_HOST", "127.0.0.1");
    return environment;
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
    environment.put("SONARQUBE_HTTP_PORT", String.valueOf(findAvailablePort()));
    environment.put("SONARQUBE_HTTP_HOST", "127.0.0.1");
    return environment;
  }

  private static int findAvailablePort() {
    try (var serverSocket = new ServerSocket(0)) {
      return serverSocket.getLocalPort();
    } catch (IOException e) {
      throw new RuntimeException("Failed to find available port", e);
    }
  }

}

