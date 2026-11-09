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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sonarsource.sonarqube.mcp.harness.ReceivedRequest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.harness.SslTestUtils;
import org.sonarsource.sonarqube.mcp.serverapi.a3s.A3sAnalysisApi;
import org.sonarsource.sonarqube.mcp.serverapi.cag.CagApi;
import org.sonarsource.sonarqube.mcp.serverapi.plugins.SonarCloudCdnPluginsApi;
import org.sonarsource.sonarqube.mcp.serverapi.qualityprofiles.QualityProfilesApi;
import org.sonarsource.sonarqube.mcp.serverapi.rules.RulesApi;
import org.sonarsource.sonarqube.mcp.tools.analysis.AnalyzeCodeSnippetTool;
import org.sonarsource.sonarqube.mcp.transport.StdioServerTransportProvider;

class SonarQubeMcpServerHttpTest {

  @SonarQubeMcpServerTest
  void should_use_stdio_transport_by_default(SonarQubeMcpServerTestHarness harness) {
    var environment = createTestEnvironment(harness.getMockSonarQubeServer().baseUrl());

    harness.prepareMockWebServer(environment);
    
    var server = new SonarQubeMcpServer(environment);

    assertThat(server.getMcpConfiguration().isHttpEnabled()).isFalse();
    assertThat(server.getHttpServerUrl()).isNull();
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
    environment.put("SONARQUBE_HTTP_PORT", "0");
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
      assertThat(URI.create(httpServer.getHttpServerUrl()).getPort()).isBetween(1024, 65535);
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

  @Nested
  class CloudSnippetAnalysis {

    @RegisterExtension
    final SonarQubeMcpServerTestHarness harness = new SonarQubeMcpServerTestHarness();

    private ProxySelector originalProxySelector;

    @BeforeEach
    void route_cloud_downloads_to_mock_server() {
      originalProxySelector = ProxySelector.getDefault();
      var proxySelector = mock(ProxySelector.class);
      when(proxySelector.select(any())).thenAnswer(invocation -> {
        var uri = invocation.getArgument(0, URI.class);
        // The Cloud CDN uses scanner.localhost, which need not resolve on the test machine.
        var proxy = "scanner.localhost".equals(uri.getHost())
          ? new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", harness.getMockSonarQubeServer().getPort()))
          : Proxy.NO_PROXY;
        return List.of(proxy);
      });
      ProxySelector.setDefault(proxySelector);
    }

    @AfterEach
    void restore_proxy_selector() {
      ProxySelector.setDefault(originalProxySelector);
    }

    @ParameterizedTest
    @CsvSource({"http, 200", "https, 200", "http, 403", "https, 403"})
    void it_should_analyze_cloud_snippets_with_caller_credentials(String transport, int profileStatus, @TempDir Path storage) throws Exception {
      var environment = createTestEnvironment(harness.getMockSonarQubeServer().baseUrl());
      environment.put("STORAGE_PATH", storage.toString());
      environment.put("SONARQUBE_IS_CLOUD", "true");
      environment.put("SONARQUBE_TRANSPORT", transport);
      environment.put("SONARQUBE_HTTP_PORT", "0");
      if (transport.equals("https")) {
        environment.put("SONARQUBE_HTTPS_KEYSTORE_PATH", Path.of(getClass().getResource("/ssl/test-keystore.p12").toURI()).toString());
        environment.put("SONARQUBE_HTTPS_KEYSTORE_PASSWORD", "test123");
      }
      harness.withPlugins().prepareMockWebServer(environment);
      var mock = harness.getMockSonarQubeServer();
      var plugin = Path.of("build/sonarqube-mcp-server/plugins/sonar-php-plugin-3.58.0.16263.jar");
      mock.stubFor(get(urlPathMatching(SonarCloudCdnPluginsApi.PLUGIN_DOWNLOAD_PATH.formatted("php", ".*")))
        .withHeader("Authorization", absent())
        .willReturn(aResponse().withBody(Files.readAllBytes(plugin))));
      mock.stubFor(get(urlPathEqualTo(QualityProfilesApi.SEARCH_PATH))
        .withHeader("Authorization", equalTo("Bearer caller-token"))
        .withQueryParam("organization", equalTo("selected-org"))
        .withQueryParam("defaults", equalTo("true"))
        .willReturn(aResponse().withStatus(profileStatus).withBody("{\"profiles\":[{\"key\":\"selected-profile\"}]}")));
      mock.stubFor(get(urlPathEqualTo(RulesApi.SEARCH_PATH))
        .withHeader("Authorization", equalTo("Bearer caller-token"))
        .withQueryParam("organization", equalTo("selected-org"))
        .withQueryParam("qprofile", equalTo("selected-profile"))
        .willReturn(okJson("{\"actives\":{\"php:S1135\":[{\"params\":[]}]}}")));
      var server = new SonarQubeMcpServer(environment);
      try (var client = HttpClient.newBuilder().sslContext(SslTestUtils.trustAllSslContext()).build()) {
        server.start();
        server.waitForInitialization();
        assertThat(mock.countRequestsContaining("/plugins/php/versions/")).isEqualTo(1);
        assertThat(Files.mismatch(plugin, storage.resolve("plugins").resolve(plugin.getFileName()))).isEqualTo(-1);
        assertThat(mock.getReceivedRequests()).extracting(ReceivedRequest::authorizationHeader).containsOnlyNulls();
        var request = HttpRequest.newBuilder(URI.create(server.getHttpServerUrl().replace("127.0.0.1", "localhost")))
          .header("Authorization", "Bearer caller-token")
          .header("Content-Type", "application/json")
          .header("Accept", "application/json, text/event-stream")
          .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/call", "params",
            Map.of("name", AnalyzeCodeSnippetTool.TOOL_NAME, "arguments",
              Map.of("organization", "selected-org", "language", "php", "fileContent", "<?php\n// TODO implement this\n"))))))
          .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        var result = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result");
        assertThat(result.get("isError").getAsBoolean()).isEqualTo(profileStatus == 403);
        if (profileStatus == 200) {
          assertThat(result.toString()).contains("php:S1135");
        }
        assertThat(mock.countRequestsContaining(QualityProfilesApi.SEARCH_PATH)).isEqualTo(1);
        assertThat(mock.getReceivedRequests()).extracting(ReceivedRequest::authorizationHeader).doesNotContain("Bearer test-token");
        assertThat(mock.countRequestsContaining(CagApi.CAG_ENTITLEMENT_PATH)).isZero();
        assertThat(mock.countRequestsContaining(A3sAnalysisApi.A3S_ORG_CONFIG_PATH)).isZero();
        assertThat(mock.countRequestsContaining(A3sAnalysisApi.ANALYSES_PATH)).isZero();
      } finally {
        server.shutdown();
      }
    }
  }

  private Map<String, String> createTestEnvironment(String baseUrl) {
    var environment = new HashMap<String, String>();
    environment.put("STORAGE_PATH", System.getProperty("java.io.tmpdir"));
    environment.put("SONARQUBE_TOKEN", "test-token");
    environment.put("SONARQUBE_URL", baseUrl);
    return environment;
  }
}
