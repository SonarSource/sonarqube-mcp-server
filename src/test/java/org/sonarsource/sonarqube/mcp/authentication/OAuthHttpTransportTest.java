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

import com.github.tomakehurst.wiremock.WireMockServer;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sonarsource.sonarqube.mcp.SonarQubeMcpServer;
import org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfiguration;
import org.sonarsource.sonarqube.mcp.harness.SslTestUtils;
import org.sonarsource.sonarqube.mcp.serverapi.components.ComponentsApi;
import org.sonarsource.sonarqube.mcp.serverapi.issues.IssuesApi;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.OrganizationsApi;
import org.sonarsource.sonarqube.mcp.transport.HttpServerTransportProvider;
import org.sonarsource.sonarqube.mcp.transport.HttpTransportSettings;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.KEY;
import static org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.keyValues;
import static org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.sign;
import static org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.token;

class OAuthHttpTransportTest {
  private static final String ISSUER = OAuthTestSupport.CONFIGURATION.issuer();
  private static final String CLIENT_ORIGIN = "https://mcp-client.example.com";
  private static final String EXTERNAL_METADATA = "https://metadata.example.com/oauth-protected-resource";
  private static final String TRUST_STORE = "javax.net.ssl.trustStore";
  private static final String TRUST_STORE_PASSWORD = "javax.net.ssl.trustStorePassword";
  private static final Path KEYSTORE = Path.of("src/test/resources/ssl/test-keystore.p12").toAbsolutePath();
  private static final String PASSWORD = "test123";
  private static final Gson GSON = new Gson();

  @TempDir
  Path storage;
  private WireMockServer cloud;
  private WireMockServer auth0;
  private SonarQubeMcpServer server;
  private HttpClient client;
  private OAuthTestSupport.MutableClock clock;
  private Map<String, String> environment;
  private OAuthConfiguration oauth;
  private String endpoint;
  private String originalTrustStore;
  private String originalTrustStorePassword;

  @BeforeEach
  void setUp() throws IOException {
    originalTrustStore = System.getProperty(TRUST_STORE);
    originalTrustStorePassword = System.getProperty(TRUST_STORE_PASSWORD);
    System.setProperty(TRUST_STORE, KEYSTORE.toString());
    System.setProperty(TRUST_STORE_PASSWORD, PASSWORD);
    cloud = new WireMockServer(wireMockConfig().httpDisabled(true).dynamicHttpsPort().keystorePath(KEYSTORE.toString())
      .keystorePassword(PASSWORD).keyManagerPassword(PASSWORD));
    cloud.start();
    auth0 = new WireMockServer(wireMockConfig().dynamicPort());
    auth0.start();
    auth0.stubFor(get("/.well-known/jwks.json").willReturn(okJson(GSON.toJson(Map.of("keys", List.of(keyValues("key", KEY)))))));
    clock = new OAuthTestSupport.MutableClock();
    client = HttpClient.newBuilder().sslContext(SslTestUtils.trustAllSslContext()).build();
    environment = new HashMap<>();
    environment.put("STORAGE_PATH", storage.toString());
    environment.put("SONARQUBE_TRANSPORT", "http");
    environment.put("SONARQUBE_HTTP_PORT", Integer.toString(availablePort()));
    environment.put("SONARQUBE_HTTP_AUTH_MODE", "OAUTH");
    environment.put("SONARQUBE_HTTP_ALLOWED_ORIGINS", CLIENT_ORIGIN);
    environment.put("SONARQUBE_IS_CLOUD", "true");
    environment.put("SONARQUBE_URL", cloud.baseUrl());
    environment.put("SONARQUBE_CLOUD_API_URL", cloud.baseUrl());
    environment.put("SONARQUBE_OAUTH_ISSUER", ISSUER);
    environment.put("SONARQUBE_OAUTH_RESOURCE", cloud.baseUrl() + "/mcp");
    environment.put("SONARQUBE_OAUTH_CLOUD_AUDIENCE", cloud.baseUrl() + "/");
    environment.put("SONARQUBE_OAUTH_CLIENT_ID", "backend");
    environment.put("SONARQUBE_OAUTH_CLIENT_SECRET", "test-secret");
    environment.put("SONARQUBE_TOOLSETS", "issues");
    environment.put("TELEMETRY_DISABLED", "true");
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.shutdown();
    }
    client.close();
    auth0.stop();
    cloud.stop();
    restoreProperty(TRUST_STORE, originalTrustStore);
    restoreProperty(TRUST_STORE_PASSWORD, originalTrustStorePassword);
  }

  @Test
  void it_should_exchange_and_isolate_users_grants_organizations_and_independent_scopes_over_http() throws Exception {
    startWithRealAuthentication();
    var first = grant("123", "first", "read:all write:all", "read:all");
    var second = grant("456", "second", "read:all", "read:all");
    var writer = grant("123", "writer", "write:all", "write:all");
    var full = grant("123", "full", "read:all write:all", "read:all write:all");
    stubOrganization(first, "first-org");
    stubOrganization(second, "second-org");
    stubOrganization(full, "full-org");
    stubWrite(writer);
    stubWrite(full);

    for (var entry : Map.of(first, "first-org", second, "second-org", full, "full-org").entrySet()) {
      var discovered = send(call(entry.getKey(), "list_organizations", Map.of()));
      assertSuccess(discovered, entry.getValue());
      assertSuccess(send(call(entry.getKey(), "search_my_sonarqube_projects", Map.of("organization", entry.getValue()))), entry.getValue() + "-project");
    }
    var firstTools = send(request(first.source(), "tools/list", Map.of()).build());
    assertThat(firstTools.body()).contains("list_organizations").doesNotContain("change_sonar_issue_status");
    var projectTool = JsonParser.parseString(firstTools.body()).getAsJsonObject().getAsJsonObject("result").getAsJsonArray("tools").asList()
      .stream().map(JsonElement::getAsJsonObject).filter(tool -> tool.get("name").getAsString().equals("search_my_sonarqube_projects")).findFirst().orElseThrow();
    assertThat(projectTool.getAsJsonObject("inputSchema").getAsJsonObject("properties").has("organization")).isTrue();
    var writerTools = send(request(writer.source(), "tools/list", Map.of()).header("SONARQUBE_READ_ONLY", "false").build());
    assertThat(writerTools.body()).contains("change_sonar_issue_status").doesNotContain("list_organizations");
    assertScopeDenied(send(call(first, "change_sonar_issue_status", Map.of("key", "issue", "status", "accept"))), "write:all");
    assertScopeDenied(send(call(writer, "list_organizations", Map.of())), "read:all");
    assertSuccess(send(call(writer, "change_sonar_issue_status", Map.of("key", "issue", "status", "accept"))), "successfully changed");
    assertSuccess(send(call(full, "change_sonar_issue_status", Map.of("key", "issue", "status", "accept"))), "successfully changed");

    var requests = new ArrayList<CompletableFuture<Void>>();
    for (var index = 0; index < 18; index++) {
      var grant = switch (index % 3) {
        case 0 -> first;
        case 1 -> second;
        default -> full;
      };
      var org = switch (index % 3) {
        case 0 -> "first-org";
        case 1 -> "second-org";
        default -> "full-org";
      };
      requests.add(client.sendAsync(call(grant, "search_my_sonarqube_projects", Map.of("organization", org)), HttpResponse.BodyHandlers.ofString())
        .thenAccept(response -> assertSuccess(response, org + "-project")));
    }
    CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).join();
    assertSuccess(send(call(first, "list_organizations", Map.of())), "first-org");
    auth0.verify(4, postRequestedFor(urlEqualTo("/oauth/token")));
    auth0.verify(1, getRequestedFor(urlEqualTo("/.well-known/jwks.json")));
    cloud.verify(21, getRequestedFor(urlPathEqualTo(ComponentsApi.COMPONENTS_SEARCH_PATH)));
    cloud.verify(2, postRequestedFor(urlEqualTo(IssuesApi.DO_TRANSITION_PATH)));
    assertThat(cloud.getAllServeEvents()).allSatisfy(event -> {
      assertThat(event.getWasMatched()).isTrue();
      assertThat(event.getRequest().getHeader("Authorization")).isIn("Bearer " + first.cloud(), "Bearer " + second.cloud(), "Bearer " + writer.cloud(), "Bearer " + full.cloud());
    });
    var cloudRequests = cloud.getAllServeEvents().size();
    clock.advance(Duration.ofSeconds(299));
    var expired = send(call(first, "list_organizations", Map.of()));
    assertThat(expired.statusCode()).isEqualTo(401);
    assertThat(expired.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(value -> assertThat(value).contains("error=\"invalid_token\""));
    assertThat(cloud.getAllServeEvents()).hasSize(cloudRequests);
    auth0.verify(4, postRequestedFor(urlEqualTo("/oauth/token")));
  }

  @Test
  void it_should_reject_malformed_credentials_and_organization_headers_before_exchange() throws Exception {
    startWithRealAuthentication();
    var grant = grant("123", "valid", "read:all", "read:all");
    var missing = send(request(null, "ping", Map.of()).header("SONARQUBE_TOKEN", grant.source()).build());
    assertThat(missing.statusCode()).isEqualTo(401);
    assertThat(missing.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(value -> assertThat(value).contains("resource_metadata=").doesNotContain("invalid_token"));
    for (var authorization : List.of("Basic " + grant.source(), "Bearer", "Bearer ", "Bearer  " + grant.source(), "Bearer a b", "Bearer a,b")) {
      var response = send(request(null, "ping", Map.of()).header("Authorization", authorization).build());
      assertThat(response.statusCode()).isEqualTo(401);
      assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
      assertThat(response.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(value -> assertThat(value).contains("invalid_token"));
    }
    assertThat(send(request(grant.source(), "ping", Map.of()).header("Authorization", "Bearer " + grant.source()).build()).statusCode()).isEqualTo(401);
    for (var org : List.of("", "organization")) {
      assertThat(send(request(grant.source(), "ping", Map.of()).header("SONARQUBE_ORG", org).build()).statusCode()).isEqualTo(400);
    }
    assertThat(send(request(grant.cloud(), "ping", Map.of()).build()).statusCode()).isEqualTo(401);
    assertThat(send(request(grant.source(), "ping", Map.of()).header("SONARQUBE_READ_ONLY", "invalid").build()).statusCode()).isEqualTo(400);
    assertThat(cloud.getAllServeEvents()).isEmpty();
    auth0.verify(0, postRequestedFor(urlEqualTo("/oauth/token")));
    assertThat(send(request(null, "ping", Map.of()).header("Authorization", "bEaReR " + grant.source()).build()).statusCode()).isEqualTo(200);
    auth0.verify(1, postRequestedFor(urlEqualTo("/oauth/token")));
  }

  @ParameterizedTest
  @CsvSource({"http,false", "https,false", "http,true"})
  void it_should_wire_production_bootstrap_and_local_or_external_metadata(String transport, boolean external) throws Exception {
    environment.put("SONARQUBE_TRANSPORT", transport);
    environment.put("SONARQUBE_HTTPS_KEYSTORE_PATH", KEYSTORE.toString());
    environment.put("SONARQUBE_HTTPS_KEYSTORE_PASSWORD", PASSWORD);
    if (external) {
      environment.put("SONARQUBE_OAUTH_METADATA_URL", EXTERNAL_METADATA);
    }
    endpoint = transport + "://localhost:" + environment.get("SONARQUBE_HTTP_PORT") + "/mcp";
    server = new SonarQubeMcpServer(environment);
    server.start();
    var expectedMetadata = external ? EXTERNAL_METADATA : cloud.baseUrl() + OAuthProtectedResourceMetadata.PATH;
    var bootstrap = send(request(null, "ping", Map.of()).header("Origin", CLIENT_ORIGIN).build());
    assertThat(bootstrap.statusCode()).isEqualTo(401);
    assertThat(bootstrap.headers().firstValue("Access-Control-Allow-Origin")).hasValue(CLIENT_ORIGIN);
    assertThat(bootstrap.headers().firstValue("Access-Control-Expose-Headers")).hasValue("WWW-Authenticate");
    var untrustedOrigin = "https://untrusted.example.com";
    var untrustedBootstrap = send(request(null, "ping", Map.of()).header("Origin", untrustedOrigin).build());
    assertThat(untrustedBootstrap.statusCode()).isEqualTo(401);
    assertThat(untrustedBootstrap.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    assertThat(untrustedBootstrap.headers().firstValue("Access-Control-Expose-Headers")).isEmpty();
    var blocked = send(HttpRequest.newBuilder(URI.create(endpoint).resolve("/other")).header("Origin", untrustedOrigin).GET().build());
    assertThat(blocked.statusCode()).isEqualTo(403);
    assertThat(blocked.body()).contains("Origin not allowed");
    assertThat(blocked.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    assertThat(blocked.headers().firstValue("Access-Control-Expose-Headers")).isEmpty();
    assertThat(bootstrap.headers().firstValue("WWW-Authenticate")).hasValue("Bearer resource_metadata=\"" + expectedMetadata + "\", scope=\"read:all write:all\"");
    var metadataUri = URI.create(endpoint).resolve(OAuthProtectedResourceMetadata.PATH);
    var metadata = send(HttpRequest.newBuilder(metadataUri).header("Origin", CLIENT_ORIGIN).GET().build());
    assertThat(metadata.statusCode()).isEqualTo(external ? 401 : 200);
    if (!external) {
      var document = JsonParser.parseString(metadata.body()).getAsJsonObject();
      assertThat(document.get("resource").getAsString()).isEqualTo(cloud.baseUrl() + "/mcp");
      assertThat(document.getAsJsonArray("authorization_servers").get(0).getAsString()).isEqualTo(ISSUER);
      assertThat(metadata.headers().firstValue("Cache-Control")).hasValue("public, max-age=300");
    }
    assertThat(send(HttpRequest.newBuilder(metadataUri).POST(HttpRequest.BodyPublishers.noBody()).build()).statusCode()).isEqualTo(401);
    assertThat(send(HttpRequest.newBuilder(URI.create(endpoint).resolve(OAuthProtectedResourceMetadata.PATH + "/other")).GET().build()).statusCode()).isEqualTo(401);
    assertThat(send(request("not-a-jwt", "ping", Map.of()).build()).statusCode()).isEqualTo(401);
    assertThat(cloud.getAllServeEvents()).isEmpty();
  }

  private void startWithRealAuthentication() {
    var configuration = new McpServerLaunchConfiguration(environment);
    oauth = configuration.getOAuthConfiguration();
    var keys = new Auth0JwkProvider(auth0.baseUrl() + "/", clock);
    var service = new Auth0OAuthAuthenticationService(oauth, new Auth0TokenVerifier(oauth, keys, clock), new Auth0TokenExchange(oauth, auth0.baseUrl()), clock);
    var transport = new HttpServerTransportProvider(HttpTransportSettings.from(configuration), configuration.getOAuthMetadata(), service);
    endpoint = transport.getServerUrl();
    server = new SonarQubeMcpServer(null, transport, environment);
    server.start();
  }

  private Grant grant(String user, String id, String sourceScopes, String cloudScopes) {
    var source = sign(token(oauth.resource()).withSubject("oauth2|github|github|" + user).withJWTId(id).withClaim("scope", sourceScopes));
    var downstream = sign(token(oauth.cloudAudience()).withSubject("oauth2|github|github|" + user).withJWTId(id + "-cloud")
      .withClaim("azp", oauth.clientId()).withClaim("scope", cloudScopes)
      .withClaim("act", Map.of("sub", oauth.clientId(), "act", Map.of("sub", "client"))));
    auth0.stubFor(post("/oauth/token").withRequestBody(matchingJsonPath("$.subject_token", equalTo(source)))
      .withRequestBody(matchingJsonPath("$.scope", equalTo(sourceScopes)))
      .withRequestBody(matchingJsonPath("$.audience", equalTo(oauth.cloudAudience())))
      .willReturn(okJson(GSON.toJson(Map.of("access_token", downstream, "token_type", "Bearer")))));
    return new Grant(source, downstream);
  }

  private void stubOrganization(Grant grant, String organization) {
    cloud.stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH)).withHeader("Authorization", equalTo("Bearer " + grant.cloud()))
      .willReturn(okJson("[{\"key\":\"" + organization + "\",\"name\":\"Organization\"}]")));
    cloud.stubFor(get(urlPathEqualTo(ComponentsApi.COMPONENTS_SEARCH_PATH)).withHeader("Authorization", equalTo("Bearer " + grant.cloud()))
      .withQueryParam("organization", equalTo(organization))
      .willReturn(okJson("{\"components\":[{\"key\":\"" + organization + "-project\",\"name\":\"Project\"}],"
        + "\"paging\":{\"pageIndex\":1,\"pageSize\":500,\"total\":1}}")));
  }

  private void stubWrite(Grant grant) {
    cloud.stubFor(post(IssuesApi.DO_TRANSITION_PATH).withHeader("Authorization", equalTo("Bearer " + grant.cloud())).willReturn(okJson("{}")));
  }

  private HttpRequest call(Grant grant, String tool, Map<String, Object> arguments) {
    return request(grant.source(), "tools/call", Map.of("name", tool, "arguments", arguments)).header("Origin", CLIENT_ORIGIN).build();
  }

  private HttpRequest.Builder request(String source, String method, Map<String, Object> params) {
    var builder = HttpRequest.newBuilder(URI.create(endpoint)).header("Content-Type", "application/json")
      .header("Accept", "application/json, text/event-stream")
      .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", params))));
    if (source != null) {
      builder.header("Authorization", "Bearer " + source);
    }
    return builder;
  }

  private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static void assertSuccess(HttpResponse<String> response, String text) {
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains(text).doesNotContain("isError\":true", "Error 404");
  }

  private static void assertScopeDenied(HttpResponse<String> response, String requiredScope) {
    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).hasValue(CLIENT_ORIGIN);
    assertThat(response.headers().firstValue("Access-Control-Expose-Headers")).hasValue("WWW-Authenticate");
    assertThat(response.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(value ->
      assertThat(value).contains("scope=\"" + requiredScope + "\"", "error=\"insufficient_scope\""));
    assertThat(response.body()).contains("Insufficient OAuth scope");
  }

  private static int availablePort() throws IOException {
    try (var socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  private static void restoreProperty(String name, String value) {
    if (value == null) {
      System.clearProperty(name);
    } else {
      System.setProperty(name, value);
    }
  }

  private record Grant(String source, String cloud) {
  }
}
