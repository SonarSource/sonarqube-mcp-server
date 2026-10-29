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
package org.sonarsource.sonarqube.mcp.tools.agenticreadiness;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarqube.mcp.SonarQubeMcpServer;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.agenticreadiness.AgenticReadinessApi;
import org.sonarsource.sonarqube.mcp.serverapi.agenticreadiness.WasFeatureFlagsApi;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.OrganizationsApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgenticReadinessToolsetTests {

  private static final String ORG_ONE_ID = "00000000-0000-0000-0000-000000000001";
  private static final String ORG_TWO_ID = "00000000-0000-0000-0000-000000000002";
  private static final String TWO_ORGANIZATIONS = """
    [{"key":"org-one","uuidV4":"%s"},{"key":"org-two","uuidV4":"%s"}]
    """.formatted(ORG_ONE_ID, ORG_TWO_ID);
  private static final Map<String, String> STDIO_DEFAULTS = Map.of("SONARQUBE_IS_CLOUD", "true", "SONARQUBE_TOOLSETS", "");
  private static final List<String> TOOL_NAMES = List.of(StartAgenticReadinessAssessmentTool.TOOL_NAME,
    GetAgenticReadinessAssessmentTool.TOOL_NAME, ListAgenticReadinessAssessmentsTool.TOOL_NAME);

  @SonarQubeMcpServerTest
  void it_should_not_bypass_the_stdio_flag_with_explicit_opt_in(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient(Map.of("SONARQUBE_ORG", "org-one", "SONARQUBE_TOOLSETS", "agentic-readiness"));

    assertThat(client.listTools()).extracting(McpSchema.Tool::name).doesNotContainAnyElementsOf(TOOL_NAMES);
    for (var name : TOOL_NAMES) {
      assertThatThrownBy(() -> client.callTool(name)).isInstanceOfSatisfying(McpError.class,
        error -> assertThat(error.getJsonRpcError().data()).isEqualTo("Tool not found: " + name));
    }
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(AgenticReadinessApi.ASSESSMENTS_PATH)).isZero();
  }

  @SonarQubeMcpServerTest
  void it_should_check_the_uniquely_detected_organization_in_stdio(SonarQubeMcpServerTestHarness harness) {
    stubOrganizations(harness, "[{\"key\":\"org-one\",\"uuidV4\":\"" + ORG_ONE_ID + "\"}]");
    stubFlag(harness, ORG_ONE_ID, true);

    var client = harness.newClient(STDIO_DEFAULTS);

    assertThat(client.listTools()).extracting(McpSchema.Tool::name).containsAll(TOOL_NAMES);
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(WasFeatureFlagsApi.FEATURE_FLAGS_PATH)).isEqualTo(1);
  }

  @SonarQubeMcpServerTest
  void it_should_register_sara_when_one_accessible_organization_is_enabled(SonarQubeMcpServerTestHarness harness) {
    stubOrganizations(harness, TWO_ORGANIZATIONS);
    stubFlag(harness, ORG_ONE_ID, false);
    stubFlag(harness, ORG_TWO_ID, true);

    var client = harness.newClient(STDIO_DEFAULTS);

    assertThat(client.listTools()).extracting(McpSchema.Tool::name).containsAll(TOOL_NAMES);
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(WasFeatureFlagsApi.FEATURE_FLAGS_PATH)).isEqualTo(2);
  }

  @SonarQubeMcpServerTest
  void it_should_hide_sara_when_no_accessible_organization_is_enabled(SonarQubeMcpServerTestHarness harness) {
    stubOrganizations(harness, TWO_ORGANIZATIONS);
    stubFlag(harness, ORG_ONE_ID, false);
    stubFlag(harness, ORG_TWO_ID, false);

    var client = harness.newClient(STDIO_DEFAULTS);

    assertThat(client.listTools()).extracting(McpSchema.Tool::name).doesNotContainAnyElementsOf(TOOL_NAMES);
  }

  @SonarQubeMcpServerTest
  void it_should_continue_checking_organizations_after_a_flag_lookup_fails(SonarQubeMcpServerTestHarness harness) {
    stubOrganizations(harness, TWO_ORGANIZATIONS);
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(WasFeatureFlagsApi.FEATURE_FLAGS_PATH))
      .withQueryParam("organizationId", equalTo(ORG_ONE_ID)).willReturn(aResponse().withStatus(403)));
    stubFlag(harness, ORG_TWO_ID, true);

    var client = harness.newClient(STDIO_DEFAULTS);

    assertThat(client.listTools()).extracting(McpSchema.Tool::name).containsAll(TOOL_NAMES);
  }

  @SonarQubeMcpServerTest
  void it_should_continue_after_an_organization_uuid_lookup_fails(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH))
      .willReturn(okJson("[{\"key\":\"org-one\"},{\"key\":\"org-two\",\"uuidV4\":\"" + ORG_TWO_ID + "\"}]")));
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH))
      .withQueryParam("organizationKey", equalTo("org-one")).willReturn(aResponse().withStatus(403)));
    stubFlag(harness, ORG_TWO_ID, true);

    var client = harness.newClient(STDIO_DEFAULTS);

    assertThat(client.listTools()).extracting(McpSchema.Tool::name).containsAll(TOOL_NAMES);
  }

  @SonarQubeMcpServerTest
  void it_should_limit_the_flag_check_to_the_configured_organization(SonarQubeMcpServerTestHarness harness) {
    stubOrganizations(harness, TWO_ORGANIZATIONS);
    stubFlag(harness, ORG_ONE_ID, false);
    stubFlag(harness, ORG_TWO_ID, true);

    var client = harness.newClient(Map.of("SONARQUBE_ORG", "org-one", "SONARQUBE_TOOLSETS", "agentic-readiness"));

    assertThat(client.listTools()).extracting(McpSchema.Tool::name).doesNotContainAnyElementsOf(TOOL_NAMES);
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("organizationId=" + ORG_TWO_ID)).isFalse();
  }

  @SonarQubeMcpServerTest
  void it_should_hide_sara_when_the_flag_is_missing(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(WasFeatureFlagsApi.FEATURE_FLAGS_PATH)).willReturn(okJson("{}")));

    var client = harness.newClient(Map.of("SONARQUBE_ORG", "org-one", "SONARQUBE_TOOLSETS", "agentic-readiness"));

    assertThat(client.listTools()).extracting(McpSchema.Tool::name).doesNotContainAnyElementsOf(TOOL_NAMES);
  }

  @SonarQubeMcpServerTest
  void it_should_hide_sara_without_any_accessible_organization(SonarQubeMcpServerTestHarness harness) {
    stubOrganizations(harness, "[]");

    var client = harness.newClient(STDIO_DEFAULTS);

    assertThat(client.listTools()).extracting(McpSchema.Tool::name).doesNotContainAnyElementsOf(TOOL_NAMES);
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(WasFeatureFlagsApi.FEATURE_FLAGS_PATH)).isZero();
  }

  @SonarQubeMcpServerTest
  void it_should_not_allow_http_clients_to_enable_sara_without_server_opt_in(SonarQubeMcpServerTestHarness harness, @TempDir Path storage) throws Exception {
    var port = availablePort();
    var server = new SonarQubeMcpServer(httpEnvironment(harness, storage, port));
    try (var client = HttpClient.newHttpClient()) {
      server.start();
      assertThat(server.getComposedInstructions()).doesNotContain("## Agentic Readiness Assessment");
      for (var headers : List.of(Map.<String, String>of(), Map.of("SONARQUBE_TOOLSETS", "agentic-readiness"))) {
        assertThat(toolNames(send(client, port, "tools/list", Map.of(), headers))).doesNotContainAnyElementsOf(TOOL_NAMES);
        for (var name : TOOL_NAMES) {
          var response = send(client, port, "tools/call", Map.of("name", name, "arguments", Map.of()), headers);
          assertThat(response.getAsJsonObject("error").get("code").getAsInt()).isEqualTo(McpSchema.ErrorCodes.METHOD_NOT_FOUND);
        }
      }
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(WasFeatureFlagsApi.FEATURE_FLAGS_PATH)).isZero();
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(AgenticReadinessApi.ASSESSMENTS_PATH)).isZero();
    } finally {
      server.shutdown();
    }
  }

  @SonarQubeMcpServerTest
  void it_should_allow_http_opt_in_with_backend_enforcement_and_request_filters(SonarQubeMcpServerTestHarness harness, @TempDir Path storage) throws Exception {
    var port = availablePort();
    var environment = httpEnvironment(harness, storage, port);
    environment.put("SONARQUBE_TOOLSETS", "agentic-readiness");
    harness.stubSaraError();
    harness.getMockSonarQubeServer().stubFor(get(AgenticReadinessApi.ASSESSMENTS_PATH + "/assessment-id")
      .willReturn(aResponse().withStatus(403)));
    var server = new SonarQubeMcpServer(environment);
    try (var client = HttpClient.newHttpClient()) {
      server.start();
      assertThat(server.getComposedInstructions()).contains("## Agentic Readiness Assessment");
      assertThat(toolNames(send(client, port, "tools/list", Map.of(), Map.of()))).containsAll(TOOL_NAMES);
      assertThat(toolNames(send(client, port, "tools/list", Map.of(), Map.of("SONARQUBE_READ_ONLY", "true"))))
        .contains(GetAgenticReadinessAssessmentTool.TOOL_NAME, ListAgenticReadinessAssessmentsTool.TOOL_NAME)
        .doesNotContain(StartAgenticReadinessAssessmentTool.TOOL_NAME);
      assertThat(toolNames(send(client, port, "tools/list", Map.of(), Map.of("SONARQUBE_TOOLSETS", "projects"))))
        .doesNotContainAnyElementsOf(TOOL_NAMES);
      var rejectedWrite = send(client, port, "tools/call", Map.of("name", StartAgenticReadinessAssessmentTool.TOOL_NAME,
        "arguments", Map.of("projectKey", "project")), Map.of("SONARQUBE_READ_ONLY", "true"));
      assertThat(rejectedWrite.getAsJsonObject("error").get("code").getAsInt()).isEqualTo(McpSchema.ErrorCodes.METHOD_NOT_FOUND);
      var rejectedRead = send(client, port, "tools/call", Map.of("name", GetAgenticReadinessAssessmentTool.TOOL_NAME,
        "arguments", Map.of("assessmentId", "assessment-id")), Map.of());
      assertThat(rejectedRead.getAsJsonObject("result").get("isError").getAsBoolean()).isTrue();
      assertThat(rejectedRead.toString()).contains("Forbidden");
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(AgenticReadinessApi.ASSESSMENTS_PATH)).isEqualTo(1);
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(WasFeatureFlagsApi.FEATURE_FLAGS_PATH)).isZero();
    } finally {
      server.shutdown();
    }
  }

  private static void stubOrganizations(SonarQubeMcpServerTestHarness harness, String body) {
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH)).willReturn(okJson(body)));
  }

  private static void stubFlag(SonarQubeMcpServerTestHarness harness, String organizationId, boolean enabled) {
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(WasFeatureFlagsApi.FEATURE_FLAGS_PATH))
      .withQueryParam("organizationId", equalTo(organizationId))
      .willReturn(okJson(new Gson().toJson(Map.of(WasFeatureFlagsApi.SARA_FEATURE_FLAG_KEY, enabled)))));
  }

  private static Map<String, String> httpEnvironment(SonarQubeMcpServerTestHarness harness, Path storage, int port) {
    return new HashMap<>(Map.of("STORAGE_PATH", storage.toString(), "SONARQUBE_TRANSPORT", "http", "SONARQUBE_HTTP_PORT", Integer.toString(port),
      "SONARQUBE_IS_CLOUD", "true", "SONARQUBE_URL", harness.getMockSonarQubeServer().baseUrl(), "TELEMETRY_DISABLED", "true"));
  }

  private static List<String> toolNames(JsonObject response) {
    return response.getAsJsonObject("result").getAsJsonArray("tools").asList().stream()
      .map(tool -> tool.getAsJsonObject().get("name").getAsString()).toList();
  }

  private static JsonObject send(HttpClient client, int port, String method, Map<String, Object> params, Map<String, String> headers) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp"))
      .header("Authorization", "Bearer test-token").header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream");
    headers.forEach(request::header);
    var response = client.send(request.POST(HttpRequest.BodyPublishers.ofString(
      new Gson().toJson(Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", params)))).build(), HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    return JsonParser.parseString(response.body()).getAsJsonObject();
  }

  private static int availablePort() throws Exception {
    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }
}
