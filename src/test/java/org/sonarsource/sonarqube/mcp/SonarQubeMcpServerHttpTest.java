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
package org.sonarsource.sonarqube.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.google.gson.JsonParser;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.components.ComponentsApi;
import org.sonarsource.sonarqube.mcp.transport.StdioServerTransportProvider;

class SonarQubeMcpServerHttpTest {

  @TempDir
  Path storagePath;

  @SonarQubeMcpServerTest
  void should_use_stdio_transport_by_default(SonarQubeMcpServerTestHarness harness) {
    var environment = createTestEnvironment(harness.getMockSonarQubeServer().baseUrl());

    harness.prepareMockWebServer(environment);
    
    var server = new SonarQubeMcpServer(environment);

    assertThat(server.getMcpConfiguration().isHttpEnabled()).isFalse();
  }

  @SonarQubeMcpServerTest
  void should_use_http_transport_when_enabled(SonarQubeMcpServerTestHarness harness) {
    var environment = createTestEnvironment(harness.getMockSonarQubeServer().baseUrl());
    environment.put("SONARQUBE_TRANSPORT", "http");
    environment.put("SONARQUBE_HTTP_PORT", "8080");
    environment.put("SONARQUBE_HTTP_HOST", "127.0.0.1");

    harness.prepareMockWebServer(environment);
    
    var server = new SonarQubeMcpServer(environment);

    assertThat(server.getMcpConfiguration().isHttpEnabled()).isTrue();
    assertThat(server.getMcpConfiguration().getHttpPort()).isEqualTo(8080);
    assertThat(server.getMcpConfiguration().getHttpHost()).isEqualTo("127.0.0.1");
  }

  @SonarQubeMcpServerTest
  void should_use_custom_http_configuration(SonarQubeMcpServerTestHarness harness) {
    var environment = createTestEnvironment(harness.getMockSonarQubeServer().baseUrl());
    environment.put("SONARQUBE_TRANSPORT", "http");
    environment.put("SONARQUBE_HTTP_PORT", "9000");
    environment.put("SONARQUBE_HTTP_HOST", "0.0.0.0");
    // Authentication is now optional (will show warning)

    harness.prepareMockWebServer(environment);
    
    var server = new SonarQubeMcpServer(environment);
    
    assertThat(server.getMcpConfiguration().isHttpEnabled()).isTrue();
    assertThat(server.getMcpConfiguration().getHttpPort()).isEqualTo(9000);
    assertThat(server.getMcpConfiguration().getHttpHost()).isEqualTo("0.0.0.0");
  }

  @SonarQubeMcpServerTest
  void should_have_same_tools_regardless_of_transport(SonarQubeMcpServerTestHarness harness) throws Exception {
    var environment = createTestEnvironment(harness.getMockSonarQubeServer().baseUrl());

    harness.prepareMockWebServer(environment);

    var stdioServer = new SonarQubeMcpServer(
        new StdioServerTransportProvider(null),
        null,
        environment);
    environment.put("SONARQUBE_TRANSPORT", "http");
    try (var socket = new ServerSocket(0)) {
      environment.put("SONARQUBE_HTTP_PORT", Integer.toString(socket.getLocalPort()));
    }
    var httpServer = new SonarQubeMcpServer(environment);
    try {
      stdioServer.start();
      stdioServer.waitForInitialization();
      var stdioTools = stdioServer.getSupportedTools().stream()
        .map(tool -> tool.definition().name())
        .sorted()
        .toList();

      httpServer.start();
      httpServer.waitForInitialization();
      var httpTools = httpServer.getSupportedTools().stream()
        .map(tool -> tool.definition().name())
        .sorted()
        .toList();

      assertThat(stdioTools).isNotEmpty();
      assertThat(httpTools)
        .isNotEmpty()
        .isEqualTo(stdioTools);
    } finally {
      httpServer.shutdown();
      stdioServer.shutdown();
    }
  }

  @SonarQubeMcpServerTest
  void should_not_create_http_manager_when_disabled(SonarQubeMcpServerTestHarness harness) {
    var environment = createTestEnvironment(harness.getMockSonarQubeServer().baseUrl());
    environment.put("SONARQUBE_TRANSPORT", "false");

    harness.prepareMockWebServer(environment);

    var server = new SonarQubeMcpServer(new StdioServerTransportProvider(null), null, environment);
    
    assertThat(server.getMcpConfiguration().isHttpEnabled()).isFalse();
  }

  @SonarQubeMcpServerTest
  void it_should_isolate_concurrent_stateless_server_requests(SonarQubeMcpServerTestHarness harness) throws Exception {
    assertStatelessRequestsAreIsolated(harness, false);
  }

  @SonarQubeMcpServerTest
  void it_should_isolate_concurrent_stateless_cloud_requests(SonarQubeMcpServerTestHarness harness) throws Exception {
    assertStatelessRequestsAreIsolated(harness, true);
  }

  private void assertStatelessRequestsAreIsolated(SonarQubeMcpServerTestHarness harness, boolean cloud) throws Exception {
    int port;
    try (var socket = new ServerSocket(0)) {
      port = socket.getLocalPort();
    }
    var environment = createTestEnvironment(harness.getMockSonarQubeServer().baseUrl());
    environment.remove("SONARQUBE_TOKEN");
    environment.put("SONARQUBE_TRANSPORT", "http");
    environment.put("SONARQUBE_HTTP_PORT", Integer.toString(port));
    environment.put("SONARQUBE_HTTP_HOST", "127.0.0.1");
    environment.put("SONARQUBE_IS_CLOUD", Boolean.toString(cloud));
    environment.put("SONARQUBE_TOOLSETS", "projects");
    environment.put("TELEMETRY_DISABLED", "true");
    environment.put("STORAGE_PATH", storagePath.toString());
    harness.prepareMockWebServer(environment);

    for (var caller : List.of("first", "second")) {
      var stub = get(urlPathEqualTo(ComponentsApi.COMPONENTS_SEARCH_PATH))
        .withHeader("Authorization", equalTo("Bearer token-" + caller))
        .withQueryParam("q", equalTo(caller));
      if (cloud) {
        stub.withQueryParam("organization", equalTo("org-" + caller));
      } else {
        stub.withQueryParam("qualifiers", equalTo("TRK"));
      }
      harness.getMockSonarQubeServer().stubFor(stub.willReturn(aResponse()
        .withHeader("Content-Type", "application/json")
        .withFixedDelay(caller.equals("first") ? 700 : 100)
        .withBody("""
          {"components":[{"key":"%s","name":"%s"}],"paging":{"pageIndex":1,"pageSize":500,"total":1}}
          """.formatted(caller, caller))));
    }

    var server = new SonarQubeMcpServer(environment);
    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
      server.start();
      server.waitForInitialization();
      var endpoint = URI.create("http://127.0.0.1:" + port + "/mcp");
      var first = client.sendAsync(projectSearchRequest(endpoint, "first", cloud), HttpResponse.BodyHandlers.ofString());
      var second = client.sendAsync(projectSearchRequest(endpoint, "second", cloud), HttpResponse.BodyHandlers.ofString());

      for (var caller : List.of("first", "second")) {
        var response = (caller.equals("first") ? first : second).get(10, TimeUnit.SECONDS);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Mcp-Session-Id")).isEmpty();
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        var payload = response.body().lines().filter(line -> line.startsWith("data:"))
          .map(line -> line.substring(5).trim()).findFirst().orElse(response.body());
        var result = JsonParser.parseString(payload).getAsJsonObject().getAsJsonObject("result");
        assertThat(result.get("isError").getAsBoolean()).isFalse();
        var content = result.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString();
        var project = JsonParser.parseString(content).getAsJsonObject().getAsJsonArray("projects").get(0).getAsJsonObject();
        assertThat(project.get("key").getAsString()).isEqualTo(caller);
      }

      var unauthenticated = HttpRequest.newBuilder(endpoint)
        .timeout(Duration.ofSeconds(5))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("{}"))
        .build();
      assertThat(client.send(unauthenticated, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
    } finally {
      server.shutdown();
    }
  }

  private static HttpRequest projectSearchRequest(URI endpoint, String caller, boolean cloud) {
    var builder = HttpRequest.newBuilder(endpoint)
      .timeout(Duration.ofSeconds(10))
      .header("Authorization", "Bearer token-" + caller)
      .header("Content-Type", "application/json")
      .header("Accept", "application/json, text/event-stream")
      .header("MCP-Protocol-Version", "2025-06-18");
    if (cloud) {
      builder.header("SONARQUBE_ORG", "org-" + caller);
    }
    return builder.POST(HttpRequest.BodyPublishers.ofString("""
      {"jsonrpc":"2.0","id":"%s","method":"tools/call",
       "params":{"name":"search_my_sonarqube_projects","arguments":{"q":"%s"}}}
      """.formatted(caller, caller))).build();
  }

  private Map<String, String> createTestEnvironment(String baseUrl) {
    var environment = new HashMap<String, String>();
    environment.put("STORAGE_PATH", System.getProperty("java.io.tmpdir"));
    environment.put("SONARQUBE_TOKEN", "test-token");
    environment.put("SONARQUBE_URL", baseUrl);
    return environment;
  }
}
