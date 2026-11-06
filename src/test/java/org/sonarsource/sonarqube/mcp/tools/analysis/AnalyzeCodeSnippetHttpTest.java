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
package org.sonarsource.sonarqube.mcp.tools.analysis;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.SonarQubeMcpServer;
import org.sonarsource.sonarqube.mcp.harness.ReceivedRequest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.harness.SslTestUtils;
import org.sonarsource.sonarqube.mcp.serverapi.a3s.A3sAnalysisApi;
import org.sonarsource.sonarqube.mcp.serverapi.cag.CagApi;
import org.sonarsource.sonarqube.mcp.serverapi.plugins.PluginsApi;
import org.sonarsource.sonarqube.mcp.serverapi.plugins.SonarCloudCdnPluginsApi;
import org.sonarsource.sonarqube.mcp.serverapi.qualityprofiles.QualityProfilesApi;
import org.sonarsource.sonarqube.mcp.serverapi.rules.RulesApi;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

class AnalyzeCodeSnippetHttpTest {

  @RegisterExtension
  final SonarQubeMcpServerTestHarness harness = new SonarQubeMcpServerTestHarness();

  @ParameterizedTest
  @ValueSource(strings = {"http", "https"})
  void it_should_analyze_using_the_callers_cloud_rules(String transport, @TempDir Path storage) throws Exception {
    var environment = environment(transport, storage);
    prepareMockWebServer(environment);
    var mock = harness.getMockSonarQubeServer();
    mock.stubFor(get(urlPathEqualTo(QualityProfilesApi.SEARCH_PATH))
      .withHeader("Authorization", equalTo("Bearer caller-token"))
      .withQueryParam("organization", equalTo("selected-org"))
      .withQueryParam("defaults", equalTo("true"))
      .willReturn(okJson("{\"profiles\":[{\"key\":\"selected-profile\"}]}")));
    mock.stubFor(get(urlPathEqualTo(RulesApi.SEARCH_PATH))
      .withHeader("Authorization", equalTo("Bearer caller-token"))
      .withQueryParam("organization", equalTo("selected-org"))
      .withQueryParam("qprofile", equalTo("selected-profile"))
      .willReturn(okJson("{\"actives\":{\"php:S1135\":[{\"params\":[]}]}}")));
    var server = new SonarQubeMcpServer(environment);
    try (var client = HttpClient.newBuilder().sslContext(SslTestUtils.trustAllSslContext()).build()) {
      server.start();
      server.waitForInitialization();
      assertThat(mock.countRequestsContaining(PluginsApi.INSTALLED_PLUGINS_PATH)).isEqualTo(1);
      assertThat(mock.countRequestsContaining("/plugins/php/versions/")).isEqualTo(1);
      assertThat(mock.getReceivedRequests()).extracting(ReceivedRequest::authorizationHeader).containsOnlyNulls();
      var startupRequests = mock.getReceivedRequests().size();
      var url = server.getHttpServerUrl().replace("127.0.0.1", "localhost");
      assertTools(client, url, true);

      var response = send(client, url, "tools/call", Map.of("name", AnalyzeCodeSnippetTool.TOOL_NAME, "arguments",
        Map.of("organization", "selected-org", "language", "php", "fileContent", "<?php\n// TODO implement this\n")));

      var result = JsonParser.parseString(response).getAsJsonObject().getAsJsonObject("result");
      assertThat(result.get("isError").getAsBoolean()).isFalse();
      assertThat(result.toString()).contains("php:S1135");
      assertThat(mock.countRequestsContaining(QualityProfilesApi.SEARCH_PATH)).isEqualTo(1);
      assertThat(mock.countRequestsContaining(RulesApi.SEARCH_PATH)).isEqualTo(1);
      assertThat(mock.getReceivedRequests()).filteredOn(request -> request.authorizationHeader() == null).hasSize(startupRequests);
      assertThat(mock.getReceivedRequests()).extracting(ReceivedRequest::authorizationHeader).doesNotContain("Bearer startup-token");
      assertThat(mock.getReceivedRequests()).filteredOn(request -> "Bearer caller-token".equals(request.authorizationHeader())).hasSize(2);
      assertNoVortexRequests();
    } finally {
      server.shutdown();
    }
  }

  @Test
  void it_should_not_retry_forbidden_caller_profiles_with_startup_credentials(@TempDir Path storage) throws Exception {
    var environment = environment("http", storage);
    prepareMockWebServer(environment);
    var mock = harness.getMockSonarQubeServer();
    mock.stubFor(get(urlPathEqualTo(QualityProfilesApi.SEARCH_PATH))
      .withHeader("Authorization", equalTo("Bearer caller-token"))
      .willReturn(aResponse().withStatus(403)));
    mock.stubFor(get(urlPathEqualTo(QualityProfilesApi.SEARCH_PATH))
      .withHeader("Authorization", equalTo("Bearer startup-token"))
      .willReturn(okJson("{\"profiles\":[]}")));
    var server = new SonarQubeMcpServer(environment);
    try (var client = HttpClient.newHttpClient()) {
      server.start();
      server.waitForInitialization();
      var startupRequests = mock.getReceivedRequests().size();

      var response = send(client, server.getHttpServerUrl(), "tools/call", Map.of("name", AnalyzeCodeSnippetTool.TOOL_NAME, "arguments",
        Map.of("organization", "selected-org", "language", "php", "fileContent", "<?php echo 'hello';")));

      var result = JsonParser.parseString(response).getAsJsonObject().getAsJsonObject("result");
      assertThat(result.get("isError").getAsBoolean()).isTrue();
      assertThat(result.toString()).containsIgnoringCase("permission");
      assertThat(mock.countRequestsContaining(QualityProfilesApi.SEARCH_PATH)).isEqualTo(1);
      assertThat(mock.countRequestsContaining(RulesApi.SEARCH_PATH)).isZero();
      assertThat(mock.getReceivedRequests()).filteredOn(request -> request.authorizationHeader() == null).hasSize(startupRequests);
      assertThat(mock.getReceivedRequests()).extracting(ReceivedRequest::authorizationHeader).doesNotContain("Bearer startup-token");
      assertNoVortexRequests();
    } finally {
      server.shutdown();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void it_should_hide_snippets_and_skip_downloads_when_analysis_is_disabled(boolean forceLocal, @TempDir Path storage) throws Exception {
    var environment = environment("http", storage);
    environment.put("SONARQUBE_TOOLSETS", "issues");
    environment.put("SONARQUBE_FORCE_LOCAL_ANALYSIS", Boolean.toString(forceLocal));
    prepareMockWebServer(environment);
    var server = new SonarQubeMcpServer(environment);
    try (var client = HttpClient.newHttpClient()) {
      server.start();
      server.waitForInitialization();

      assertTools(client, server.getHttpServerUrl(), false);

      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(PluginsApi.INSTALLED_PLUGINS_PATH)).isZero();
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining("/plugins/php/versions/")).isZero();
      assertNoVortexRequests();
    } finally {
      server.shutdown();
    }
  }

  private void prepareMockWebServer(Map<String, String> environment) throws Exception {
    harness.withPlugins().prepareMockWebServer(environment);
    var plugin = Path.of("build/sonarqube-mcp-server/plugins/sonar-php-plugin-3.58.0.16263.jar");
    harness.getMockSonarQubeServer().stubFor(get(urlPathMatching(SonarCloudCdnPluginsApi.PLUGIN_DOWNLOAD_PATH.formatted("php", ".*")))
      .withHeader("Authorization", absent()).willReturn(aResponse().withBody(Files.readAllBytes(plugin))));
  }

  private Map<String, String> environment(String transport, Path storage) throws Exception {
    var environment = new HashMap<String, String>();
    environment.put("SONARQUBE_URL", harness.getMockSonarQubeServer().baseUrl());
    environment.put("SONARQUBE_TOKEN", "startup-token");
    environment.put("SONARQUBE_IS_CLOUD", "true");
    environment.put("SONARQUBE_TRANSPORT", transport);
    environment.put("SONARQUBE_HTTP_PORT", "0");
    environment.put("STORAGE_PATH", storage.toString());
    environment.put("TELEMETRY_DISABLED", "true");
    if (transport.equals("https")) {
      environment.put("SONARQUBE_HTTPS_KEYSTORE_PATH", Paths.get(getClass().getResource("/ssl/test-keystore.p12").toURI()).toString());
      environment.put("SONARQUBE_HTTPS_KEYSTORE_PASSWORD", "test123");
    }
    return environment;
  }

  private static void assertTools(HttpClient client, String url, boolean snippetsEnabled) throws Exception {
    var response = send(client, url, "tools/list", Map.of());
    var tools = JsonParser.parseString(response).getAsJsonObject().getAsJsonObject("result").getAsJsonArray("tools");
    var names = tools.asList().stream().map(tool -> tool.getAsJsonObject().get("name").getAsString()).toList();
    assertThat(names.contains(AnalyzeCodeSnippetTool.TOOL_NAME)).isEqualTo(snippetsEnabled);
    assertThat(names).doesNotContain(AnalyzeFileListTool.TOOL_NAME, ToggleAutomaticAnalysisTool.TOOL_NAME, RunAdvancedCodeAnalysisTool.TOOL_NAME);
  }

  private static String send(HttpClient client, String url, String method, Map<String, Object> params) throws Exception {
    var request = HttpRequest.newBuilder(URI.create(url))
      .header("Authorization", "Bearer caller-token")
      .header("Content-Type", "application/json")
      .header("Accept", "application/json, text/event-stream")
      .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", params))))
      .build();
    var response = client.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    return response.body();
  }

  private void assertNoVortexRequests() {
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(CagApi.CAG_ENTITLEMENT_PATH)).isZero();
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(A3sAnalysisApi.A3S_ORG_CONFIG_PATH)).isZero();
  }
}
