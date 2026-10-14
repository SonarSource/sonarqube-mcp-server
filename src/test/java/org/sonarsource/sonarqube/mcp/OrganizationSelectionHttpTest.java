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

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.components.ComponentsApi;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.OrganizationsApi;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class OrganizationSelectionHttpTest {

  @SonarQubeMcpServerTest
  void it_should_fix_header_organization_only_for_its_request(SonarQubeMcpServerTestHarness harness, @TempDir Path storage) throws Exception {
    var port = availablePort();
    stubProjects(harness, "token-a", "header-org");
    stubProjects(harness, "token-a", "argument-org");
    var server = new SonarQubeMcpServer(httpEnvironment(harness, storage, port));
    try (var client = HttpClient.newHttpClient()) {
      server.start();
      assertOrganizationParameter(client, port, "header-org", false);
      assertSuccessful(client, request(port, "token-a", "header-org", "tools/call", toolParams(Map.of())), "header-org");
      for (var organization : new String[]{"header-org", "argument-org", ""}) {
        var rejected = client.send(request(port, "token-a", "header-org", "tools/call", toolParams(Map.of("organization", organization))),
          HttpResponse.BodyHandlers.ofString());
        assertThat(rejected.statusCode()).isEqualTo(200);
        assertThat(rejected.body()).contains("Organization is fixed by SONARQUBE_ORG");
      }
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(ComponentsApi.COMPONENTS_SEARCH_PATH)).isEqualTo(1);
      assertOrganizationParameter(client, port, null, true);
      assertSuccessful(client, request(port, "token-a", null, "tools/call", toolParams(Map.of("organization", "argument-org"))), "argument-org");
      assertSuccessful(client, request(port, "token-a", "header-org", "tools/call", toolParams(Map.of())), "header-org");
    } finally {
      server.shutdown();
    }
  }

  @SonarQubeMcpServerTest
  void it_should_discover_without_header_and_isolate_concurrent_tokens_and_organizations(SonarQubeMcpServerTestHarness harness, @TempDir Path storage) throws Exception {
    var port = availablePort();
    var environment = httpEnvironment(harness, storage, port);
    for (var suffix : new String[]{"a", "b"}) {
      stubProjects(harness, "token-" + suffix, "org-" + suffix);
      harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH))
        .withHeader("Authorization", equalTo("Bearer token-" + suffix))
        .willReturn(okJson("[{\"key\":\"org-" + suffix + "\",\"name\":\"Organization " + suffix + "\"}]")));
    }
    var server = new SonarQubeMcpServer(environment);
    try (var client = HttpClient.newHttpClient()) {
      server.start();
      assertSuccessful(client, request(port, "token-a", null, "tools/call", Map.of("name", "list_organizations", "arguments", Map.of())), "org-a");
      assertSuccessful(client, request(port, "token-b", null, "tools/call", Map.of("name", "list_organizations", "arguments", Map.of())), "org-b");
      var listed = client.send(request(port, "token-a", null, "tools/list", Map.of()), HttpResponse.BodyHandlers.ofString());
      assertThat(listed.statusCode()).isEqualTo(200);
      assertThat(listed.body()).contains("list_organizations", "search_my_sonarqube_projects");
      var futures = new ArrayList<CompletableFuture<Void>>();
      for (var index = 0; index < 12; index++) {
        var suffix = index % 2 == 0 ? "a" : "b";
        var org = "org-" + suffix;
        var req = request(port, "token-" + suffix, null, "tools/call", toolParams(Map.of("organization", org)));
        futures.add(client.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
          assertThat(response.statusCode()).isEqualTo(200);
          assertThat(response.body()).contains(org + "-project").doesNotContain("Forbidden", "Error 404");
        }));
      }
      CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
      var unresolved = client.send(request(port, "token-a", null, "tools/call", toolParams(Map.of())), HttpResponse.BodyHandlers.ofString());
      assertThat(unresolved.statusCode()).isEqualTo(200);
      assertThat(unresolved.body()).contains("list_organizations", "Organization is required");
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(ComponentsApi.COMPONENTS_SEARCH_PATH)).isEqualTo(12);
    } finally {
      server.shutdown();
    }
  }

  private static void assertOrganizationParameter(HttpClient client, int port, String org, boolean expected) throws Exception {
    var response = client.send(request(port, "token-a", org, "tools/list", Map.of()), HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    var tools = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("result").getAsJsonArray("tools");
    var projectTool = tools.asList().stream().map(tool -> tool.getAsJsonObject())
      .filter(tool -> tool.get("name").getAsString().equals("search_my_sonarqube_projects")).findFirst().orElseThrow();
    assertThat(projectTool.getAsJsonObject("inputSchema").getAsJsonObject("properties").has("organization")).isEqualTo(expected);
  }

  private static Map<String, String> httpEnvironment(SonarQubeMcpServerTestHarness harness, Path storage, int port) {
    var environment = new HashMap<String, String>();
    environment.put("STORAGE_PATH", storage.toString());
    environment.put("SONARQUBE_TRANSPORT", "http");
    environment.put("SONARQUBE_HTTP_PORT", Integer.toString(port));
    environment.put("SONARQUBE_IS_CLOUD", "true");
    environment.put("SONARQUBE_URL", harness.getMockSonarQubeServer().baseUrl());
    environment.put("SONARQUBE_TOOLSETS", "issues,dependency-risks");
    environment.put("TELEMETRY_DISABLED", "true");
    return environment;
  }

  private static void stubProjects(SonarQubeMcpServerTestHarness harness, String token, String org) {
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(ComponentsApi.COMPONENTS_SEARCH_PATH))
      .withHeader("Authorization", equalTo("Bearer " + token))
      .withQueryParam("organization", equalTo(org))
      .willReturn(okJson("{\"components\":[{\"key\":\"" + org + "-project\",\"name\":\"Project\"}],"
        + "\"paging\":{\"pageIndex\":1,\"pageSize\":500,\"total\":1}}")));
  }

  private static Map<String, Object> toolParams(Map<String, Object> arguments) {
    return Map.of("name", "search_my_sonarqube_projects", "arguments", arguments);
  }

  private static HttpRequest request(int port, String token, String org, String method, Map<String, Object> params) {
    var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp"))
      .header("Authorization", "Bearer " + token)
      .header("Content-Type", "application/json")
      .header("Accept", "application/json, text/event-stream")
      .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", params))));
    if (org != null) {
      builder.header("SONARQUBE_ORG", org);
    }
    return builder.build();
  }

  private static void assertSuccessful(HttpClient client, HttpRequest request, String marker) throws Exception {
    var response = client.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains(marker).doesNotContain("Organization is required", "Error 404");
  }

  private static int availablePort() throws IOException {
    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }
}
