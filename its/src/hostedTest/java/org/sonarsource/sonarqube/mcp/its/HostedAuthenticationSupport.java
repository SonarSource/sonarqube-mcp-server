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

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.TypeRef;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

final class HostedAuthenticationSupport {
  static final String ORIGIN = "https://api.sc-staging.io";
  static final URI MCP = URI.create(ORIGIN + "/mcp");
  static final String ISSUER = "https://auth.sc-staging.io/";
  static final URI METADATA = URI.create(ORIGIN + "/authentication/.well-known/oauth-protected-resource/mcp");
  static final URI ORGANIZATIONS = URI.create(ORIGIN + "/organizations/organizations?excludeEligibility=true");
  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final String ORGANIZATIONS_TOOL = "list_organizations";
  private static final String PROJECTS_TOOL = "search_my_sonarqube_projects";
  private static final Pattern METADATA_PARAMETER = Pattern.compile("(?i)(?:^|,)\\s*resource_metadata\\s*=\\s*(?:\"([^\"]+)\"|([^,\\s]+))");
  private static final TypeRef<Map<String, Object>> OBJECT = new TypeRef<>() { };

  private HostedAuthenticationSupport() {
  }

  static String requiredEnvironment(Map<String, String> environment, String name) {
    var value = environment.get(name);
    check(value != null && !value.isBlank(), "Missing " + name + "; supply it through the environment before running hosted acceptance.");
    check(value.equals(value.strip()) && value.chars().noneMatch(Character::isISOControl), "Invalid whitespace in " + name + ".");
    return value;
  }

  static void validateOAuthToken(String token, Instant now) {
    var parts = token.split("\\.", -1);
    check(parts.length == 3 && Arrays.stream(parts).noneMatch(String::isEmpty), "SONARCLOUD_IT_OAUTH_TOKEN must be an access-token JWT.");
    var claims = safely("Decode SONARCLOUD_IT_OAUTH_TOKEN", () -> json(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)));
    check(ISSUER.equals(claims.get("iss")), "SONARCLOUD_IT_OAUTH_TOKEN must come from the Staging Auth0 issuer.");
    var audience = claims.get("aud");
    var audiences = audience instanceof List<?> list ? list : List.of(audience == null ? "" : audience);
    check(audiences.contains(MCP.toString()) && !audiences.contains(ORIGIN + "/"),
      "SONARCLOUD_IT_OAUTH_TOKEN must target the Staging MCP audience, without the Cloud audience.");
    check(claims.get("exp") instanceof Number expiry && expiry.longValue() > now.getEpochSecond(),
      "SONARCLOUD_IT_OAUTH_TOKEN is expired or has no expiry; obtain a fresh token.");
    check(claims.get("scope") instanceof String scopes && Arrays.asList(scopes.split("\\s+")).contains("mcp:tools:read"),
      "SONARCLOUD_IT_OAUTH_TOKEN must grant mcp:tools:read.");
  }

  static String alterSignature(String token) {
    var parts = token.split("\\.", -1);
    return safely("Alter OAuth signature", () -> {
      var signature = Base64.getUrlDecoder().decode(parts[2]);
      signature[0] ^= 1;
      return parts[0] + "." + parts[1] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    });
  }

  static Map<String, String> headers(String header, String token, String organization) {
    var headers = new HashMap<String, String>();
    headers.put(header, header.equals("Authorization") ? "Bearer " + token : token);
    headers.put("SONARQUBE_READ_ONLY", "true");
    headers.put("SONARQUBE_TOOLSETS", "projects");
    if (organization != null) {
      headers.put("SONARQUBE_ORG", organization);
    }
    return headers;
  }

  static HttpResponse<String> ping(Map<String, String> headers) {
    return safely("Send MCP authentication probe", () -> {
      var request = HttpRequest.newBuilder(MCP).timeout(TIMEOUT)
        .header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream")
        .POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"));
      headers.forEach(request::header);
      return send(request.build());
    });
  }

  static HttpResponse<String> get(URI uri, Map<String, String> headers) {
    return safely("Send Staging HTTP request", () -> {
      var request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).header("Accept", "application/json").GET();
      headers.forEach(request::header);
      return send(request.build());
    });
  }

  private static HttpResponse<String> send(HttpRequest request) throws Exception {
    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build()) {
      return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
  }

  static void assertDiscovery() {
    var challenge = ping(Map.of());
    assertThat(challenge.statusCode()).as("Unauthenticated MCP status").isEqualTo(401);
    var metadata = metadataLocation(challenge.headers().allValues("WWW-Authenticate"));
    var response = get(metadata, Map.of());
    assertThat(response.statusCode()).as("Protected-resource metadata status").isEqualTo(200);
    assertMetadata(json(response.body()));
  }

  static URI metadataLocation(List<String> challenges) {
    for (var challenge : challenges) {
      if (!challenge.regionMatches(true, 0, "Bearer ", 0, 7)) {
        continue;
      }
      var parameter = METADATA_PARAMETER.matcher(challenge.substring(7));
      if (parameter.find()) {
        var location = parameter.group(1) != null ? parameter.group(1) : parameter.group(2);
        check(METADATA.toString().equals(location), "MCP must advertise the expected Staging Authentication metadata URL.");
        return METADATA;
      }
    }
    throw new AssertionError("MCP did not advertise resource_metadata in a Bearer challenge; deploy the OAuth configuration before live acceptance.");
  }

  static void assertMetadata(Map<String, Object> metadata) {
    check(MCP.toString().equals(metadata.get("resource")), "Metadata must identify the Staging MCP resource.");
    check(metadata.get("authorization_servers") instanceof List<?> servers && servers.equals(List.of(ISSUER)),
      "Metadata must identify the trusted Staging Auth0 issuer.");
    check(metadata.get("scopes_supported") instanceof List<?> scopes && scopes.containsAll(List.of("mcp:tools:read", "mcp:tools:write")),
      "Metadata must advertise both MCP scopes.");
  }

  static void assertProtectedReads(Map<String, String> headers, String organization, String projectKey, boolean oauth) {
    // A public project would allow a broken authenticated path to appear successful.
    var anonymousProject = get(URI.create("https://sc-staging.io/api/components/show?component="
      + URLEncoder.encode(projectKey, StandardCharsets.UTF_8)), Map.of());
    assertThat(anonymousProject.statusCode()).as("Private fixture must be inaccessible anonymously").isIn(401, 403, 404);
    assertMcpReads(ORIGIN, headers, organization, projectKey, oauth);
  }

  static void assertMcpReads(String origin, Map<String, String> headers, String organization, String projectKey, boolean oauth) {
    safely("Run authenticated MCP reads (check deployment, token validity, SQC onboarding, and fixture access)", () -> {
      var transport = HttpClientStreamableHttpTransport.builder(origin).endpoint("/mcp")
        .connectTimeout(Duration.ofSeconds(10))
        .customizeClient(builder -> builder.followRedirects(HttpClient.Redirect.NEVER))
        .httpRequestCustomizer((builder, method, uri, body, context) -> {
          builder.timeout(TIMEOUT);
          headers.forEach(builder::setHeader);
        }).build();
      try (var client = McpClient.sync(transport).requestTimeout(TIMEOUT).initializationTimeout(TIMEOUT).build()) {
        client.initialize();
        var names = new HashSet<String>();
        var cursors = new HashSet<String>();
        String cursor = null;
        do {
          var tools = client.listTools(cursor);
          for (var tool : tools.tools()) {
            names.add(tool.name());
            check(tool.annotations() != null && Boolean.TRUE.equals(tool.annotations().readOnlyHint()), "Read-only requests must advertise only read-only tools.");
          }
          cursor = tools.nextCursor();
          check(cursor == null || cursors.add(cursor), "Tool listing returned a repeated pagination cursor.");
        } while (cursor != null);
        check(names.containsAll(List.of(ORGANIZATIONS_TOOL, PROJECTS_TOOL)), "Hosted MCP must advertise organization discovery and project search.");

        var organizations = client.callTool(new McpSchema.CallToolRequest(ORGANIZATIONS_TOOL, Map.of(), null));
        assertContainsKey(toolResult(organizations), "organizations", organization);

        var arguments = new HashMap<String, Object>();
        arguments.put("q", projectKey);
        arguments.put("pageSize", 100);
        if (oauth) {
          arguments.put("organization", organization);
        }
        var projects = client.callTool(new McpSchema.CallToolRequest(PROJECTS_TOOL, arguments, null));
        assertContainsKey(toolResult(projects), "projects", projectKey);
      }
      return null;
    });
  }

  static Map<String, Object> toolResult(McpSchema.CallToolResult result) {
    check(!Boolean.TRUE.equals(result.isError()), "MCP tool returned isError=true; check token validity, SQC onboarding, and fixture permissions.");
    check(result.content().size() == 1 && result.content().getFirst() instanceof McpSchema.TextContent, "Expected one JSON text result from the MCP tool.");
    return json(((McpSchema.TextContent) result.content().getFirst()).text());
  }

  static void assertContainsKey(Map<String, Object> result, String field, String key) {
    check(result.get(field) instanceof List<?> entries && entries.stream().anyMatch(entry -> entry instanceof Map<?, ?> object && key.equals(object.get("key"))),
      "MCP result did not contain the expected " + field + " fixture; check token validity, SQC onboarding, and fixture permissions.");
  }

  static Map<String, Object> json(String text) {
    var object = safely("Parse JSON response", () -> McpJsonDefaults.getMapper().readValue(text, OBJECT));
    check(object != null, "Expected a JSON object response.");
    return object;
  }

  static <T> T safely(String operation, Callable<T> action) {
    try {
      return action.call();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(operation + " was interrupted.");
    } catch (Exception e) {
      // SDK/HTTP/parser exceptions can contain request headers, JWTs or response bodies.
      // Do not retain the original message, cause or suppressed exceptions in reports.
      throw new AssertionError(operation + " failed (" + e.getClass().getSimpleName() + ").");
    }
  }

  private static void check(boolean condition, String message) {
    assertThat(condition).withFailMessage(message).isTrue();
  }
}
