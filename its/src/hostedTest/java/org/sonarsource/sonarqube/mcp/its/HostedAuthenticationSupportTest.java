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
package org.sonarsource.sonarqube.mcp.its;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.modelcontextprotocol.json.McpJsonDefaults;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sonarsource.sonarqube.mcp.its.HostedAuthenticationSupport.ISSUER;
import static org.sonarsource.sonarqube.mcp.its.HostedAuthenticationSupport.MCP;
import static org.sonarsource.sonarqube.mcp.its.HostedAuthenticationSupport.METADATA;
import static org.sonarsource.sonarqube.mcp.its.HostedAuthenticationSupport.ORIGIN;
import static org.sonarsource.sonarqube.mcp.its.HostedAuthenticationSupport.alterSignature;
import static org.sonarsource.sonarqube.mcp.its.HostedAuthenticationSupport.assertContainsKey;
import static org.sonarsource.sonarqube.mcp.its.HostedAuthenticationSupport.assertMcpReads;
import static org.sonarsource.sonarqube.mcp.its.HostedAuthenticationSupport.assertMetadata;
import static org.sonarsource.sonarqube.mcp.its.HostedAuthenticationSupport.get;
import static org.sonarsource.sonarqube.mcp.its.HostedAuthenticationSupport.headers;
import static org.sonarsource.sonarqube.mcp.its.HostedAuthenticationSupport.json;
import static org.sonarsource.sonarqube.mcp.its.HostedAuthenticationSupport.metadataLocation;
import static org.sonarsource.sonarqube.mcp.its.HostedAuthenticationSupport.requiredEnvironment;
import static org.sonarsource.sonarqube.mcp.its.HostedAuthenticationSupport.safely;
import static org.sonarsource.sonarqube.mcp.its.HostedAuthenticationSupport.validateOAuthToken;

@Timeout(15)
class HostedAuthenticationSupportTest {
  private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");
  private static final String SYNTHETIC_TOKEN = "synthetic-credential-for-local-tests";

  @Test
  void it_should_require_only_the_requested_environment_variable_without_echoing_values() {
    assertThat(requiredEnvironment(Map.of("SELECTED", "present"), "SELECTED")).isEqualTo("present");
    assertThatThrownBy(() -> requiredEnvironment(Map.of(), "SELECTED")).isInstanceOf(AssertionError.class).hasMessageContaining("Missing SELECTED");
    assertThatThrownBy(() -> requiredEnvironment(Map.of("SELECTED", "  "), "SELECTED")).isInstanceOf(AssertionError.class).hasMessageContaining("Missing SELECTED");
    assertThatThrownBy(() -> requiredEnvironment(Map.of("SELECTED", SYNTHETIC_TOKEN + "\n"), "SELECTED"))
      .isInstanceOf(AssertionError.class).hasMessageNotContaining(SYNTHETIC_TOKEN);
  }

  @Test
  void it_should_accept_string_and_array_mcp_audiences_and_alter_only_signature_bytes() throws Exception {
    var claims = validClaims();
    var token = token(claims);
    assertThatCode(() -> validateOAuthToken(token, NOW)).doesNotThrowAnyException();
    claims.put("aud", List.of(MCP.toString()));
    assertThatCode(() -> validateOAuthToken(token(claims), NOW)).doesNotThrowAnyException();

    var altered = alterSignature(token);
    var before = token.split("\\.");
    var after = altered.split("\\.");
    assertThat(after[0]).isEqualTo(before[0]);
    assertThat(after[1]).isEqualTo(before[1]);
    assertThat(Base64.getUrlDecoder().decode(after[2])).hasSameSizeAs(Base64.getUrlDecoder().decode(before[2]))
      .isNotEqualTo(Base64.getUrlDecoder().decode(before[2]));
  }

  @ParameterizedTest
  @ValueSource(strings = {"issuer", "audience", "cloud-audience", "expiry", "expiry-missing", "scope"})
  void it_should_reject_unsuitable_oauth_fixtures(String invalidClaim) throws Exception {
    var claims = validClaims();
    switch (invalidClaim) {
      case "issuer" -> claims.put("iss", "https://another-issuer.example/");
      case "audience" -> claims.put("aud", ORIGIN + "/");
      case "cloud-audience" -> claims.put("aud", List.of(MCP.toString(), ORIGIN + "/"));
      case "expiry" -> claims.put("exp", NOW.getEpochSecond());
      case "expiry-missing" -> claims.remove("exp");
      case "scope" -> claims.put("scope", "mcp:tools:write");
      default -> throw new IllegalArgumentException(invalidClaim);
    }
    var token = token(claims);
    assertThatThrownBy(() -> validateOAuthToken(token, NOW)).isInstanceOf(AssertionError.class).hasMessageNotContaining(token);
  }

  @Test
  void it_should_sanitize_parser_and_transport_failures_including_nested_exceptions() {
    assertThatThrownBy(() -> validateOAuthToken("header.%%%.signature", NOW)).isInstanceOf(AssertionError.class)
      .hasNoCause().hasMessageNotContaining("%%%");
    assertThatThrownBy(() -> json("{\"access_token\":\"" + SYNTHETIC_TOKEN + "\", malformed"))
      .isInstanceOf(AssertionError.class).hasNoCause().hasMessageNotContaining(SYNTHETIC_TOKEN);
    assertThatThrownBy(() -> json("null")).isInstanceOf(AssertionError.class).hasMessage("Expected a JSON object response.");
    var failure = new IOException(SYNTHETIC_TOKEN, new IllegalStateException(SYNTHETIC_TOKEN));
    failure.addSuppressed(new IOException(SYNTHETIC_TOKEN));
    assertThatThrownBy(() -> safely("Test request", () -> { throw failure; }))
      .isInstanceOf(AssertionError.class).hasMessage("Test request failed (IOException).")
      .hasNoCause().satisfies(error -> assertThat(error.getSuppressed()).isEmpty());
  }

  @Test
  void it_should_parse_bearer_metadata_and_reject_legacy_or_untrusted_metadata_locations() {
    assertThat(metadataLocation(List.of("Basic realm=\"other\"", "Bearer scope=\"mcp:tools:read\", resource_metadata=\"" + METADATA + "\"")))
      .isEqualTo(METADATA);
    assertThat(metadataLocation(List.of("bearer resource_metadata=" + METADATA))).isEqualTo(METADATA);
    assertThatThrownBy(() -> metadataLocation(List.of("Bearer realm=\"MCP Server\""))).isInstanceOf(AssertionError.class).hasMessageContaining("deploy");
    assertThatThrownBy(() -> metadataLocation(List.of("Bearer resource_metadata=\"https://untrusted.example/metadata\"")))
      .isInstanceOf(AssertionError.class).hasMessageContaining("expected Staging");
    assertThatCode(() -> assertMetadata(Map.of("resource", MCP.toString(), "authorization_servers", List.of(ISSUER),
      "scopes_supported", List.of("mcp:tools:read", "mcp:tools:write")))).doesNotThrowAnyException();
    assertThatThrownBy(() -> assertMetadata(Map.of("resource", ORIGIN))).isInstanceOf(AssertionError.class);
  }

  @Test
  void it_should_require_exact_fixture_keys() {
    assertThatCode(() -> assertContainsKey(Map.of("projects", List.of(Map.of("key", "private-project"))), "projects", "private-project"))
      .doesNotThrowAnyException();
    assertThatThrownBy(() -> assertContainsKey(Map.of("projects", List.of(Map.of("key", "private-project-other"))), "projects", "private-project"))
      .isInstanceOf(AssertionError.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"OAuth", "Authorization", "SONARQUBE_TOKEN"})
  void it_should_complete_the_mcp_lifecycle_with_headers_and_organization_arguments(String mode) throws Exception {
    var oauth = mode.equals("OAuth");
    var selectedHeader = oauth ? "Authorization" : mode;
    var credentials = headers(selectedHeader, SYNTHETIC_TOKEN, oauth ? null : "fixture-org");
    var requests = new CopyOnWriteArrayList<Map<String, Object>>();
    var receivedHeaders = new CopyOnWriteArrayList<Map<String, List<String>>>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/mcp", exchange -> {
      receivedHeaders.add(new HashMap<>(exchange.getRequestHeaders()));
      answerMcp(exchange, requests, false);
    });
    server.start();
    try {
      assertMcpReads("http://127.0.0.1:" + server.getAddress().getPort(), credentials, "fixture-org", "private-project", oauth);
      assertThat(requests).extracting(request -> request.get("method"))
        .contains("initialize", "notifications/initialized", "tools/list");
      assertThat(requests.stream().filter(request -> request.get("method").equals("tools/call")).toList()).hasSize(2);
      assertThat(receivedHeaders).allSatisfy(actual -> credentials.forEach((name, value) ->
        assertThat(actual.entrySet().stream().anyMatch(entry -> entry.getKey().equalsIgnoreCase(name) && entry.getValue().equals(List.of(value)))).isTrue()));
      if (oauth) {
        assertThat(receivedHeaders).allSatisfy(actual -> assertThat(actual.keySet()).noneMatch(name -> name.equalsIgnoreCase("SONARQUBE_ORG")));
      }
      var search = requests.stream().filter(request -> request.get("params") instanceof Map<?, ?> params
        && "search_my_sonarqube_projects".equals(params.get("name"))).findFirst().orElseThrow();
      var arguments = (Map<?, ?>) ((Map<?, ?>) search.get("params")).get("arguments");
      assertThat(arguments.get("q")).isEqualTo("private-project");
      assertThat(arguments.get("organization")).isEqualTo(oauth ? "fixture-org" : null);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void it_should_fail_on_http_200_tool_errors_without_exposing_the_response() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/mcp", exchange -> answerMcp(exchange, new CopyOnWriteArrayList<>(), true));
    server.start();
    try {
      assertThatThrownBy(() -> assertMcpReads("http://127.0.0.1:" + server.getAddress().getPort(),
        headers("Authorization", SYNTHETIC_TOKEN, null), "fixture-org", "private-project", true))
        .isInstanceOf(AssertionError.class).hasMessageContaining("isError=true").hasMessageNotContaining(SYNTHETIC_TOKEN).hasNoCause();
    } finally {
      server.stop(0);
    }
  }

  @Test
  void it_should_not_follow_http_redirects() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var redirectedRequests = new AtomicInteger();
    server.createContext("/", exchange -> {
      exchange.getResponseHeaders().add("Location", "/unexpected");
      exchange.sendResponseHeaders(302, -1);
      exchange.close();
    });
    server.createContext("/unexpected", exchange -> {
      redirectedRequests.incrementAndGet();
      exchange.sendResponseHeaders(500, -1);
      exchange.close();
    });
    server.start();
    try {
      var origin = "http://127.0.0.1:" + server.getAddress().getPort();
      assertThat(get(URI.create(origin + "/redirect"), Map.of()).statusCode()).isEqualTo(302);
      assertThatThrownBy(() -> assertMcpReads(origin, headers("Authorization", SYNTHETIC_TOKEN, null), "fixture-org", "private-project", true))
        .isInstanceOf(AssertionError.class).hasNoCause().hasMessageNotContaining(SYNTHETIC_TOKEN);
      assertThat(redirectedRequests).hasValue(0);
    } finally {
      server.stop(0);
    }
  }

  private static Map<String, Object> validClaims() {
    return new HashMap<>(Map.of("iss", ISSUER, "aud", MCP.toString(), "exp", NOW.plusSeconds(600).getEpochSecond(), "scope", "openid mcp:tools:read"));
  }

  private static String token(Map<String, Object> claims) throws Exception {
    var encoder = Base64.getUrlEncoder().withoutPadding();
    return encoder.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8)) + "."
      + encoder.encodeToString(McpJsonDefaults.getMapper().writeValueAsBytes(claims)) + "."
      + encoder.encodeToString(new byte[] {1, 2, 3, 4});
  }

  private static void answerMcp(HttpExchange exchange, List<Map<String, Object>> requests, boolean failTool) throws IOException {
    try (exchange) {
      if (!exchange.getRequestMethod().equals("POST")) {
        exchange.sendResponseHeaders(405, -1);
        return;
      }
      var request = json(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      requests.add(request);
      if (!request.containsKey("id")) {
        exchange.sendResponseHeaders(202, -1);
        return;
      }
      var result = switch ((String) request.get("method")) {
        case "initialize" -> Map.of("protocolVersion", ((Map<?, ?>) request.get("params")).get("protocolVersion"),
          "capabilities", Map.of("tools", Map.of()), "serverInfo", Map.of("name", "local-fixture", "version", "1"));
        case "tools/list" -> Map.of("tools", List.of("list_organizations", "search_my_sonarqube_projects").stream()
          .map(name -> Map.of("name", name, "inputSchema", Map.of("type", "object"), "annotations", Map.of("readOnlyHint", true))).toList());
        case "tools/call" -> {
          var organizations = "list_organizations".equals(((Map<?, ?>) request.get("params")).get("name"));
          var data = Map.of(organizations ? "organizations" : "projects", List.of(Map.of("key", organizations ? "fixture-org" : "private-project")));
          yield Map.of("isError", failTool, "content", List.of(Map.of("type", "text", "text",
            failTool ? SYNTHETIC_TOKEN : McpJsonDefaults.getMapper().writeValueAsString(data))));
        }
        default -> throw new IllegalArgumentException("Unexpected local MCP method");
      };
      var body = McpJsonDefaults.getMapper().writeValueAsBytes(Map.of("jsonrpc", "2.0", "id", request.get("id"), "result", result));
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
    }
  }
}
