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
package org.sonarsource.sonarqube.mcp.authentication;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.tools.onboarding.DiscoverGitHubRepositoryTool;
import org.sonarsource.sonarqube.mcp.tools.onboarding.ImportGitHubOrganizationTool;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

class OAuthOnboardingTest {
  private static void getJson(SonarQubeMcpServerTestHarness harness, String path, String body) {
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(path)).willReturn(okJson(body)));
  }

  private static void environment(SonarQubeMcpServerTestHarness harness) {
    getJson(harness, "/static_configuration/configuration.json", "{\"environmentName\":\"dev9\",\"api\":{\"v2\":\"" + harness.getMockSonarQubeServer().baseUrl() + "\"}}");
  }

  private static void organization(SonarQubeMcpServerTestHarness harness) {
    getJson(harness, "/api/organizations/search", """
      {"organizations":[{"key":"org","name":"Org","actions":{"admin":true},"alm":{"key":"github","url":"https://github.com/owner"}}],"paging":{"total":1}}
      """);
  }

  private static void verify(SonarQubeMcpServerTestHarness harness, int count, RequestPatternBuilder pattern) {
    new WireMock("localhost", harness.getMockSonarQubeServer().getPort()).verifyThat(count, pattern);
  }

  private static void verify(SonarQubeMcpServerTestHarness harness, RequestPatternBuilder pattern) {
    verify(harness, 1, pattern);
  }

  @SonarQubeMcpServerTest
  void it_should_forward_exchanged_oauth_token_and_enforce_onboarding_scopes(SonarQubeMcpServerTestHarness harness) throws Exception {
    environment(harness);
    organization(harness);
    getJson(harness, "/api/alm_integration/list_repositories", "{\"repositories\":[]} ");
    int port;
    try (var socket = new java.net.ServerSocket(0)) {
      port = socket.getLocalPort();
    }
    var storage = java.nio.file.Files.createTempDirectory("mcp-onboarding-oauth-test");
    var settings = new org.sonarsource.sonarqube.mcp.transport.HttpTransportSettings(port, "127.0.0.1",
      org.sonarsource.sonarqube.mcp.authentication.AuthMode.OAUTH, true, null,
      new org.sonarsource.sonarqube.mcp.transport.HttpTransportSettings.TlsSettings(false, null, null, null, null, null, null),
      new org.sonarsource.sonarqube.mcp.transport.HttpTransportSettings.RequestSettings(java.util.List.of(), "1.0.0", false));
    var metadata = new org.sonarsource.sonarqube.mcp.authentication.OAuthProtectedResourceMetadata(
      "https://api.sc-dev9.io/mcp", "https://auth-dev9.sc-dev9.io/");
    var transport = new org.sonarsource.sonarqube.mcp.transport.HttpServerTransportProvider(settings, metadata, token -> {
      assertThat(token).isIn("read-token-a", "combined-token-a");
      var scopes = token.equals("read-token-a") ? java.util.Set.of("read:all") : java.util.Set.of("read:all", "write:all");
      return new org.sonarsource.sonarqube.mcp.authentication.OAuthRequestAuthentication("cloud-token-b", scopes);
    });
    // Inject a test authenticator into the real OAuth transport; JWT/OBO are covered by the authentication suite.
    var server = new org.sonarsource.sonarqube.mcp.SonarQubeMcpServer(null, transport, Map.of(
      "SONARQUBE_TRANSPORT", "http", "STORAGE_PATH", storage.toString(),
      "SONARQUBE_URL", harness.getMockSonarQubeServer().baseUrl(), "SONARQUBE_IS_CLOUD", "true",
      "SONARQUBE_TOOLSETS", "projects", "TELEMETRY_DISABLED", "true"));
    try (var http = java.net.http.HttpClient.newHttpClient()) {
      server.start();
      var readTools = oauthRequest(http, port, "read-token-a", "tools/list", "{}");
      assertThat(readTools.body()).contains(DiscoverGitHubRepositoryTool.TOOL_NAME).doesNotContain(ImportGitHubOrganizationTool.TOOL_NAME);
      var combinedTools = oauthRequest(http, port, "combined-token-a", "tools/list", "{}");
      assertThat(combinedTools.body()).contains(ImportGitHubOrganizationTool.TOOL_NAME);
      var importArgs = "{\"name\":\"import_github_organization\",\"arguments\":{\"github\":\"owner\"}}";
      var denied = oauthRequest(http, port, "read-token-a", "tools/call", importArgs);
      assertThat(denied.body()).contains("Tool not found: " + ImportGitHubOrganizationTool.TOOL_NAME);
      verify(harness, 0, getRequestedFor(urlPathEqualTo("/api/organizations/search")));
      var discovery = oauthRequest(http, port, "read-token-a", "tools/call",
        "{\"name\":\"discover_github_repository\",\"arguments\":{\"repository\":\"owner/repo\"}}");
      // Repository listing must also use the exchanged Cloud token.
      assertThat(discovery.body()).contains("\"isError\":false");
      verify(harness, getRequestedFor(urlPathEqualTo("/api/organizations/search")).withHeader("Authorization", equalTo("Bearer cloud-token-b")));
      verify(harness, getRequestedFor(urlPathEqualTo("/api/alm_integration/list_repositories")).withHeader("Authorization", equalTo("Bearer cloud-token-b")));
      var imported = oauthRequest(http, port, "combined-token-a", "tools/call", importArgs);
      assertThat(imported.body()).contains("\"isError\":false", "ready");
      verify(harness, 0, getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer combined-token-a")));
      verify(harness, 0, getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer read-token-a")));
    } finally {
      server.shutdown();
      org.apache.commons.io.FileUtils.deleteDirectory(storage.toFile());
    }
  }

  private static java.net.http.HttpResponse<String> oauthRequest(java.net.http.HttpClient http, int port, String token, String method, String params)
    throws java.io.IOException, InterruptedException {
    var response = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + "/mcp"))
      .header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream")
      .header("Authorization", "Bearer " + token).header("MCP-Protocol-Version", "2025-11-25")
      .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"" + method + "\",\"params\":" + params + "}"))
      .build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    return response;
  }

  @org.junit.jupiter.api.Test
  void it_should_preserve_oauth_account_access_without_an_organization() throws Exception {
    var storage = java.nio.file.Files.createTempDirectory("mcp-oauth-account-test");
    var configuration = new java.util.HashMap<String, String>();
    configuration.put("STORAGE_PATH", storage.toString());
    configuration.put("SONARQUBE_TRANSPORT", "http");
    configuration.put("SONARQUBE_HTTP_AUTH_MODE", "OAUTH");
    configuration.put("SONARQUBE_URL", "https://dev9.sc-dev9.io");
    configuration.put("SONARQUBE_IS_CLOUD", "true");
    configuration.put("SONARQUBE_CLOUD_API_URL", "https://api.sc-dev9.io");
    configuration.put("SONARQUBE_OAUTH_RESOURCE", "https://api.sc-dev9.io/mcp");
    configuration.put("SONARQUBE_OAUTH_ISSUER", "https://auth-dev9.sc-dev9.io/");
    configuration.put("SONARQUBE_OAUTH_CLOUD_AUDIENCE", "https://api.sc-dev9.io/");
    configuration.put("SONARQUBE_OAUTH_CLIENT_ID", "test-backend-client");
    configuration.put("SONARQUBE_OAUTH_CLIENT_SECRET", "test-only-secret");
    configuration.put("SONARQUBE_TOOLSETS", "projects");
    configuration.put("TELEMETRY_DISABLED", "true");
    var server = new org.sonarsource.sonarqube.mcp.SonarQubeMcpServer(configuration);
    try {
      var field = server.getClass().getDeclaredField("currentTransportContext");
      field.setAccessible(true);
      @SuppressWarnings("unchecked")
      var context = (ThreadLocal<io.modelcontextprotocol.common.McpTransportContext>) field.get(server);
      context.set(io.modelcontextprotocol.common.McpTransportContext.create(Map.of(
        org.sonarsource.sonarqube.mcp.transport.HttpServerTransportProvider.CONTEXT_TOKEN_KEY, "cloud-token-b")));
      try {
        assertThat(server.get().isSonarQubeCloud()).isTrue();
        assertThat(server.get().getOrganization()).isNull();
      } finally {
        context.remove();
      }
    } finally {
      server.shutdown();
      org.apache.commons.io.FileUtils.deleteDirectory(storage.toFile());
    }
  }
}
