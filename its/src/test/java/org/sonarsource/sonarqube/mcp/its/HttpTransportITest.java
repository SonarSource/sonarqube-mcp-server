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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@Timeout(90)
class HttpTransportITest {

  private static final int MCP_HTTP_PORT = 8888;
  private static final String ORGANIZATION = "sonarlint-it";
  private static final String RESOURCE = "https://api.sc-staging.io/mcp";
  private static final String ISSUER = "https://auth.sc-staging.io/";
  private static final String METADATA_PATH = "/.well-known/oauth-protected-resource/mcp";
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

  @Container
  private static final GenericContainer<?> httpServerContainer = createHttpServerContainer(false);

  @Container
  private static final GenericContainer<?> oauthHttpServerContainer = createHttpServerContainer(true);

  @Test
  void should_start_server_with_http_transport() {
    assertThat(httpServerContainer.isRunning()).isTrue();
    assertThat(httpServerContainer.getEnvMap())
      .withFailMessage("Telemetry must be disabled in integration tests")
      .containsEntry("TELEMETRY_DISABLED", "true");

    assertThat(httpServerContainer.getLogs()).contains("Created HTTP transport provider");
  }

  @Test
  void should_provide_accessible_http_endpoint() throws Exception {
    var result = httpServerContainer.execInContainer(
      "wget", "-qO-", "http://localhost:" + MCP_HTTP_PORT + "/mcp"
    );

    // 6=auth required
    assertThat(result.getExitCode()).isEqualTo(6);
  }

  @Test
  void should_handle_http_security_warnings() {
    var logs = httpServerContainer.getLogs();

    assertThat(logs)
      .contains("SECURITY WARNING: MCP HTTP server is configured to bind to all network interfaces")
      .contains("SECURITY WARNING: MCP server is using HTTP without SSL/TLS encryption");
  }

  @ParameterizedTest
  @CsvSource({"Authorization, false", "SONARQUBE_TOKEN, false", "Authorization, true", "SONARQUBE_TOKEN, true"})
  void should_read_staging_with_legacy_credentials(String header, boolean oauthEnabled) {
    try (var client = newClient(header, McpServerTestContainers.getSonarCloudToken(), oauthEnabled)) {
      client.initialize();
      assertThat(client.listTools().tools()).isNotEmpty()
        .allSatisfy(tool -> assertThat(tool.annotations().readOnlyHint()).isTrue())
        .extracting(McpSchema.Tool::name).contains("list_organizations", "search_my_sonarqube_projects");

      var organizations = successfulToolResponse(client.callTool(new McpSchema.CallToolRequest("list_organizations", Map.of(), null)));
      assertThat(organizations.getAsJsonArray("organizations"))
        .anySatisfy(organization -> assertThat(organization.getAsJsonObject().get("key").getAsString()).isEqualTo(ORGANIZATION));

      var projects = successfulToolResponse(client.callTool(new McpSchema.CallToolRequest("search_my_sonarqube_projects", Map.of("pageSize", 1), null)));
      assertThat(projects.getAsJsonArray("projects")).isNotEmpty();
    }
  }

  @ParameterizedTest
  @CsvSource({"Authorization, false", "SONARQUBE_TOKEN, false", "Authorization, true", "SONARQUBE_TOKEN, true"})
  void should_reject_invalid_legacy_credentials_on_protected_reads(String header, boolean oauthEnabled) {
    try (var client = newClient(header, "invalid-legacy-token", oauthEnabled)) {
      client.initialize();
      // Initialization and ping do not contact Cloud; a protected tool must reject the token.
      var result = client.callTool(new McpSchema.CallToolRequest("list_organizations", Map.of(), null));

      assertThat(result.isError()).isTrue();
      assertThat(result.content()).singleElement().isInstanceOfSatisfying(McpSchema.TextContent.class,
        content -> assertThat(content.text()).containsAnyOf(
          "Please verify your token is valid and has the correct permissions.",
          "Please verify your token has the required permissions for this operation."));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void should_require_credentials(boolean oauthEnabled) throws Exception {
    var request = HttpRequest.newBuilder(URI.create(origin(oauthEnabled) + "/mcp")).timeout(REQUEST_TIMEOUT)
      .header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream")
      .POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}")).build();

    try (var client = HttpClient.newHttpClient()) {
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(401);
      if (oauthEnabled) {
        assertThat(response.headers().firstValue("WWW-Authenticate")).contains(
          "Bearer resource_metadata=\"https://api.sc-staging.io" + METADATA_PATH + "\", scope=\"mcp:tools:read mcp:tools:write\"");
      }
    }
  }

  @Test
  void should_advertise_staging_oauth_discovery_from_container() throws Exception {
    // Fetch from the container's mapped port, while advertising the canonical Staging audience.
    var request = HttpRequest.newBuilder(URI.create(origin(true) + METADATA_PATH)).timeout(REQUEST_TIMEOUT).GET().build();
    try (var client = HttpClient.newHttpClient()) {
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(200);
      var metadata = JsonParser.parseString(response.body()).getAsJsonObject();
      assertThat(metadata.keySet()).containsExactlyInAnyOrder("resource", "authorization_servers", "scopes_supported", "bearer_methods_supported");
      assertThat(metadata.get("resource").getAsString()).isEqualTo(RESOURCE);
      assertThat(metadata.getAsJsonArray("authorization_servers")).extracting(JsonElement::getAsString).containsExactly(ISSUER);
      assertThat(metadata.getAsJsonArray("scopes_supported")).extracting(JsonElement::getAsString)
        .containsExactlyInAnyOrder("mcp:tools:read", "mcp:tools:write");
      assertThat(metadata.getAsJsonArray("bearer_methods_supported")).extracting(JsonElement::getAsString).containsExactly("header");
    }
  }

  private static JsonObject successfulToolResponse(McpSchema.CallToolResult result) {
    assertThat(result.isError()).isFalse();
    assertThat(result.content()).singleElement().isInstanceOf(McpSchema.TextContent.class);
    return JsonParser.parseString(((McpSchema.TextContent) result.content().getFirst()).text()).getAsJsonObject();
  }

  private static McpSyncClient newClient(String header, String token, boolean oauthEnabled) {
    var credential = header.equals("Authorization") ? ("Bearer " + token) : token;
    var transport = HttpClientStreamableHttpTransport.builder(origin(oauthEnabled)).endpoint("/mcp")
      .connectTimeout(Duration.ofSeconds(10))
      .httpRequestCustomizer((builder, method, uri, body, context) -> builder.timeout(REQUEST_TIMEOUT)
        .header(header, credential).header("SONARQUBE_ORG", ORGANIZATION)
        .header("SONARQUBE_READ_ONLY", "true").header("SONARQUBE_TOOLSETS", "projects"))
      .build();
    return McpClient.sync(transport).requestTimeout(REQUEST_TIMEOUT).initializationTimeout(REQUEST_TIMEOUT).build();
  }

  private static String origin(boolean oauthEnabled) {
    var container = oauthEnabled ? oauthHttpServerContainer : httpServerContainer;
    return "http://" + container.getHost() + ":" + container.getMappedPort(MCP_HTTP_PORT);
  }

  private static GenericContainer<?> createHttpServerContainer(boolean oauthEnabled) {
    var builder = McpServerTestContainers.builder()
      .withExposedPort(MCP_HTTP_PORT)
      .withEnv("SONARQUBE_TRANSPORT", "http")
      .withEnv("SONARQUBE_TOKEN", "")
      .withEnv("SONARQUBE_ORG", "")
      .withEnv("SONARQUBE_HTTP_PORT", String.valueOf(MCP_HTTP_PORT))
      .withEnv("SONARQUBE_HTTP_HOST", "0.0.0.0")
      .withAdditionalApkPackages("wget", "nodejs")
      .withWaitLogMessage(".*SonarQube MCP Server Started.*")
      .withLogPrefix(oauthEnabled ? "OAuth-HTTP-Container" : "HTTP-Container");
    if (oauthEnabled) {
      builder.withEnv("SONARQUBE_OAUTH_ISSUER", ISSUER)
        .withEnv("SONARQUBE_OAUTH_RESOURCE", RESOURCE)
        .withEnv("SONARQUBE_OAUTH_CLOUD_AUDIENCE", "https://api.sc-staging.io/")
        // Discovery and legacy authentication do not use the exchange client credentials.
        .withEnv("SONARQUBE_OAUTH_CLIENT_ID", "unused-discovery-test-client")
        .withEnv("SONARQUBE_OAUTH_CLIENT_SECRET", "unused-discovery-test-secret");
    }
    return builder.build();
  }

}
