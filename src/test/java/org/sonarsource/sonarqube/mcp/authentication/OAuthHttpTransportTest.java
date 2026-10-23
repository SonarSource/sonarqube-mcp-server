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

import com.auth0.jwt.JWTCreator;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.SonarQubeMcpServer;
import org.sonarsource.sonarqube.mcp.authentication.AuthenticationFilter.OAuthSupport;
import org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.LogCapture;
import org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfiguration;
import org.sonarsource.sonarqube.mcp.harness.SslTestUtils;
import org.sonarsource.sonarqube.mcp.http.HttpClientProvider;
import org.sonarsource.sonarqube.mcp.serverapi.components.ComponentsApi;
import org.sonarsource.sonarqube.mcp.serverapi.issues.IssuesApi;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.OrganizationsApi;
import org.sonarsource.sonarqube.mcp.serverapi.system.SystemApi;
import org.sonarsource.sonarqube.mcp.transport.HttpServerTransportProvider;
import org.sonarsource.sonarqube.mcp.transport.HttpTransportSettings;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
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
import static org.awaitility.Awaitility.await;
import static org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.KEY;
import static org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.keyValues;
import static org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.sign;
import static org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.token;

class OAuthHttpTransportTest {
  private static final String ISSUER = OAuthTestSupport.CONFIGURATION.issuer().toString();
  private static final String CLIENT_ORIGIN = "https://mcp-client.example.com";
  private static final String EXTERNAL_METADATA = "https://metadata.example.com/oauth-protected-resource";
  private static final String TRUST_STORE = "javax.net.ssl.trustStore";
  private static final String TRUST_STORE_PASSWORD = "javax.net.ssl.trustStorePassword";
  @TempDir
  static Path tlsStorage;
  private static Path keystore;
  private static final String PASSWORD = "test123";
  private static final Gson GSON = new Gson();

  @TempDir
  Path storage;
  private WireMockServer cloud;
  private WireMockServer auth0;
  private SonarQubeMcpServer server;
  private HttpClient client;
  private HttpClientProvider httpClients;
  private OAuthTestSupport.MutableClock clock;
  private Map<String, String> environment;
  private OAuthConfiguration oauth;
  private String endpoint;
  private String originalAnalyticsEndpoint;
  private String originalTrustStore;
  private String originalTrustStorePassword;

  @BeforeAll
  static void createTlsFixture() throws Exception {
    // OkHttp requires SANs; the older shared fixture only has a common name.
    keystore = tlsStorage.resolve("localhost.p12");
    var keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
    var process = new ProcessBuilder(keytool, "-genkeypair", "-alias", "localhost", "-keyalg", "RSA", "-keysize", "2048",
      "-validity", "2", "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-storetype", "PKCS12",
      "-keystore", keystore.toString(), "-storepass", PASSWORD, "-keypass", PASSWORD).redirectErrorStream(true).start();
    assertThat(process.waitFor(15, TimeUnit.SECONDS)).isTrue();
    assertThat(process.exitValue()).isZero();
  }

  @BeforeEach
  void setUp() {
    originalTrustStore = System.getProperty(TRUST_STORE);
    originalTrustStorePassword = System.getProperty(TRUST_STORE_PASSWORD);
    System.setProperty(TRUST_STORE, keystore.toString());
    System.setProperty(TRUST_STORE_PASSWORD, PASSWORD);
    cloud = new WireMockServer(wireMockConfig().httpDisabled(true).dynamicHttpsPort().http2TlsDisabled(true).keystorePath(keystore.toString())
      .keystorePassword(PASSWORD).keyManagerPassword(PASSWORD));
    cloud.start();
    originalAnalyticsEndpoint = System.getProperty("sonarqube.mcp.analytics.endpoint");
    System.setProperty("sonarqube.mcp.analytics.endpoint", cloud.baseUrl() + "/gessie");
    cloud.stubFor(post("/gessie").willReturn(okJson("{}")));
    auth0 = new WireMockServer(wireMockConfig().dynamicPort());
    auth0.start();
    auth0.stubFor(get("/.well-known/jwks.json").willReturn(okJson(GSON.toJson(Map.of("keys", List.of(keyValues("key", KEY)))))));
    clock = new OAuthTestSupport.MutableClock();
    client = HttpClient.newBuilder().sslContext(SslTestUtils.trustAllSslContext()).build();
    httpClients = new HttpClientProvider("OAuth transport tests");
    environment = new HashMap<>();
    environment.put("STORAGE_PATH", storage.toString());
    environment.put("SONARQUBE_TRANSPORT", "http");
    environment.put("SONARQUBE_HTTP_HOST", "localhost");
    environment.put("SONARQUBE_HTTP_PORT", "0");
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
    httpClients.shutdown();
    auth0.stop();
    cloud.stop();
    restoreProperty("sonarqube.mcp.analytics.endpoint", originalAnalyticsEndpoint);
    restoreProperty(TRUST_STORE, originalTrustStore);
    restoreProperty(TRUST_STORE_PASSWORD, originalTrustStorePassword);
  }

  @Test
  void it_should_exchange_and_isolate_users_grants_organizations_and_independent_scopes_over_http() throws Exception {
    startWithRealAuthentication();
    var first = grant("123", "first", "mcp:tools:read mcp:tools:write", "read:all write:all", "read:all");
    var second = grant("456", "second", "mcp:tools:read", "read:all", "read:all");
    var writer = grant("123", "writer", "mcp:tools:write", "write:all", "write:all");
    var full = grant("123", "full", "mcp:tools:read mcp:tools:write", "read:all write:all", "read:all write:all");
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
    assertScopeDenied(send(call(first, "change_sonar_issue_status", Map.of("key", "issue", "status", "accept"))), "mcp:tools:write");
    assertScopeDenied(send(call(writer, "list_organizations", Map.of())), "mcp:tools:read");
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
  void it_should_authenticate_one_hour_tokens_reexchange_after_cache_expiry_and_reject_expired_source_over_http() throws Exception {
    startWithRealAuthentication();
    var issuedAt = clock.instant().minusSeconds(1);
    var sourceExpiresAt = issuedAt.plusSeconds(3600);
    var source = sign(token(oauth.resource()).withIssuedAt(issuedAt).withExpiresAt(sourceExpiresAt).withClaim("scope", "mcp:tools:read"));
    var firstCloud = sign(token(oauth.cloudAudience()).withIssuedAt(issuedAt).withExpiresAt(issuedAt.plusSeconds(3600))
      .withClaim("azp", oauth.clientId()).withClaim("scope", "read:all")
      .withClaim("act", Map.of("sub", oauth.clientId(), "act", Map.of("sub", "client"))));
    var first = new Grant(source, firstCloud);
    auth0.stubFor(post("/oauth/token").withRequestBody(matchingJsonPath("$.subject_token", equalTo(source)))
      .withRequestBody(matchingJsonPath("$.scope", equalTo("read:all")))
      .withRequestBody(matchingJsonPath("$.audience", equalTo(oauth.cloudAudience())))
      .willReturn(okJson(GSON.toJson(Map.of("access_token", firstCloud, "token_type", "Bearer")))));
    stubOrganization(first, "my-org");

    assertSuccess(send(call(first, "list_organizations", Map.of())), "my-org");
    auth0.verify(1, postRequestedFor(urlEqualTo("/oauth/token")));
    clock.advance(Duration.ofSeconds(30));
    assertSuccess(send(call(first, "list_organizations", Map.of())), "my-org");
    auth0.verify(1, postRequestedFor(urlEqualTo("/oauth/token")));
    cloud.verify(2, getRequestedFor(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH))
      .withHeader("Authorization", equalTo("Bearer " + firstCloud)));

    clock.advance(Duration.ofSeconds(271));
    var refreshedIssuedAt = clock.instant().minusSeconds(1);
    var refreshedExpiresAt = refreshedIssuedAt.plusSeconds(3600);
    var refreshedCloud = sign(token(oauth.cloudAudience()).withIssuedAt(refreshedIssuedAt).withExpiresAt(refreshedExpiresAt)
      .withClaim("azp", oauth.clientId()).withClaim("scope", "read:all")
      .withClaim("act", Map.of("sub", oauth.clientId(), "act", Map.of("sub", "client"))));
    assertThat(refreshedCloud).isNotEqualTo(firstCloud);
    var refreshed = new Grant(source, refreshedCloud);
    auth0.stubFor(post("/oauth/token").withRequestBody(matchingJsonPath("$.subject_token", equalTo(source)))
      .withRequestBody(matchingJsonPath("$.scope", equalTo("read:all")))
      .withRequestBody(matchingJsonPath("$.audience", equalTo(oauth.cloudAudience())))
      .willReturn(okJson(GSON.toJson(Map.of("access_token", refreshedCloud, "token_type", "Bearer")))));
    stubOrganization(refreshed, "my-org");

    assertSuccess(send(call(refreshed, "list_organizations", Map.of())), "my-org");
    auth0.verify(2, postRequestedFor(urlEqualTo("/oauth/token")));
    cloud.verify(1, getRequestedFor(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH))
      .withHeader("Authorization", equalTo("Bearer " + refreshedCloud)));
    assertThat(cloud.getAllServeEvents()).allSatisfy(event -> {
      assertThat(event.getWasMatched()).isTrue();
      assertThat(event.getRequest().getHeader("Authorization")).isIn("Bearer " + firstCloud, "Bearer " + refreshedCloud)
        .isNotEqualTo("Bearer " + source);
    });
    var auth0Requests = auth0.getAllServeEvents().size();
    var cloudRequests = cloud.getAllServeEvents().size();

    clock.advance(Duration.between(clock.instant(), sourceExpiresAt));
    assertThat(refreshedExpiresAt).isAfter(clock.instant());
    var expired = send(call(refreshed, "list_organizations", Map.of()));
    assertThat(expired.statusCode()).isEqualTo(401);
    assertThat(expired.headers().firstValue("WWW-Authenticate"))
      .hasValueSatisfying(value -> assertThat(value).contains("error=\"invalid_token\""));
    assertThat(auth0.getAllServeEvents()).hasSize(auth0Requests);
    assertThat(cloud.getAllServeEvents()).hasSize(cloudRequests);
  }

  @ParameterizedTest
  @ValueSource(strings = {"oauth2|github|github|123", "samlp|enterprise-connection|user-id", "opaque-enterprise-user"})
  void it_should_authenticate_social_and_enterprise_subjects_over_http(String subject) throws Exception {
    startWithRealAuthentication();
    var grant = grantForSubject(subject, "subject", "mcp:tools:read", "read:all", "read:all");
    stubOrganization(grant, "my-org");

    assertSuccess(send(call(grant, "list_organizations", Map.of())), "my-org");
    assertSuccess(send(call(grant, "list_organizations", Map.of())), "my-org");

    auth0.verify(1, postRequestedFor(urlEqualTo("/oauth/token")));
    cloud.verify(2, getRequestedFor(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH))
      .withHeader("Authorization", equalTo("Bearer " + grant.cloud())));
  }

  @Test
  void it_should_reject_malformed_credentials_and_organization_headers_before_exchange() throws Exception {
    startWithRealAuthentication();
    var grant = grant("123", "valid", "mcp:tools:read", "read:all", "read:all");
    var missing = send(request(null, "ping", Map.of()).build());
    assertThat(missing.statusCode()).isEqualTo(401);
    assertThat(missing.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(value -> assertThat(value).contains("resource_metadata=").doesNotContain("invalid_token"));
    var legacyOAuth = send(request(null, "ping", Map.of()).header("SONARQUBE_TOKEN", grant.source()).build());
    assertThat(legacyOAuth.statusCode()).isEqualTo(401);
    assertThat(legacyOAuth.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(value -> assertThat(value).contains("invalid_token"));
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
  @CsvSource({
    "mcp:tools:read offline_access openid profile write:all other:permission, read:all",
    "mcp:tools:write offline_access openid profile read:all other:permission, write:all",
    "mcp:tools:read mcp:tools:write offline_access, read:all write:all"
  })
  void it_should_ignore_extra_source_scopes_without_widening_tool_access_over_http(String sourceScopes, String cloudScopes) throws Exception {
    startWithRealAuthentication();
    var grant = grant("123", "extra-scopes", sourceScopes, cloudScopes, cloudScopes);
    stubOrganization(grant, "my-org");
    stubWrite(grant);

    assertSuccess(send(request(grant.source(), "initialize", Map.of("protocolVersion", "2025-06-18", "capabilities", Map.of(),
      "clientInfo", Map.of("name", "oauth-test", "version", "1.0"))).build()), "serverInfo");
    var tools = send(request(grant.source(), "tools/list", Map.of()).build());
    assertThat(tools.statusCode()).isEqualTo(200);
    if (cloudScopes.contains("read:all")) {
      assertThat(tools.body()).contains("list_organizations");
      assertSuccess(send(call(grant, "list_organizations", Map.of())), "my-org");
    } else {
      assertThat(tools.body()).doesNotContain("list_organizations");
      assertScopeDenied(send(call(grant, "list_organizations", Map.of())), "mcp:tools:read");
      cloud.verify(0, getRequestedFor(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH)));
    }
    if (cloudScopes.contains("write:all")) {
      assertThat(tools.body()).contains("change_sonar_issue_status");
      assertSuccess(send(call(grant, "change_sonar_issue_status", Map.of("key", "issue", "status", "accept"))), "successfully changed");
    } else {
      assertThat(tools.body()).doesNotContain("change_sonar_issue_status");
      assertScopeDenied(send(call(grant, "change_sonar_issue_status", Map.of("key", "issue", "status", "accept"))), "mcp:tools:write");
      cloud.verify(0, postRequestedFor(urlPathEqualTo(IssuesApi.DO_TRANSITION_PATH)));
    }
    auth0.verify(1, postRequestedFor(urlEqualTo("/oauth/token")).withRequestBody(matchingJsonPath("$.scope", equalTo(cloudScopes))));
  }

  @Test
  void it_should_reject_source_tokens_without_mcp_scopes_before_exchange_over_http() throws Exception {
    startWithRealAuthentication();
    for (var scope : List.of("", "read:all", "write:all", "read:all write:all", "other:permission", "offline_access", "openid profile offline_access")) {
      var source = sign(token(oauth.resource()).withClaim("scope", scope));

      var response = send(request(source, "tools/call", Map.of("name", "list_organizations", "arguments", Map.of())).build());

      assertThat(response.statusCode()).as("Token A scopes: %s", scope).isEqualTo(403);
      assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
      assertThat(response.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(value -> assertThat(value)
        .contains("scope=\"mcp:tools:read mcp:tools:write\"", "error=\"insufficient_scope\""));
    }
    auth0.verify(0, postRequestedFor(urlEqualTo("/oauth/token")));
    assertThat(cloud.getAllServeEvents()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"mcp:tools:read", "read:all offline_access", "read:all write:all"})
  void it_should_reject_unrequested_scopes_on_the_exchanged_cloud_token_over_http(String cloudScopes) throws Exception {
    startWithRealAuthentication();
    var grant = grant("123", "wrong-cloud-scopes", "mcp:tools:read offline_access write:all", "read:all", cloudScopes);

    var response = send(call(grant, "list_organizations", Map.of()));

    assertThat(response.statusCode()).isEqualTo(502);
    assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
    auth0.verify(1, postRequestedFor(urlEqualTo("/oauth/token")).withRequestBody(matchingJsonPath("$.scope", equalTo("read:all"))));
    assertThat(cloud.getAllServeEvents()).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({"http,false", "https,false", "http,true"})
  void it_should_wire_production_bootstrap_and_local_or_external_metadata(String transport, boolean external) throws Exception {
    environment.put("SONARQUBE_TRANSPORT", transport);
    environment.put("SONARQUBE_HTTPS_KEYSTORE_PATH", keystore.toString());
    environment.put("SONARQUBE_HTTPS_KEYSTORE_PASSWORD", PASSWORD);
    if (external) {
      environment.put("SONARQUBE_OAUTH_METADATA_URL", EXTERNAL_METADATA);
    }
    try (var logs = new LogCapture(false)) {
      startProductionServer();
      server.waitForInitialization();
      logs.assertContains("Transport: HTTP (" + endpoint + ")");
    }
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
    assertThat(bootstrap.headers().firstValue("WWW-Authenticate")).hasValue("Bearer resource_metadata=\"" + expectedMetadata + "\", scope=\"mcp:tools:read mcp:tools:write\"");
    var metadataUri = URI.create(endpoint).resolve(OAuthProtectedResourceMetadata.PATH);
    var metadata = send(HttpRequest.newBuilder(metadataUri).header("Origin", CLIENT_ORIGIN).GET().build());
    assertThat(metadata.statusCode()).isEqualTo(external ? 401 : 200);
    if (!external) {
      var document = JsonParser.parseString(metadata.body()).getAsJsonObject();
      assertThat(document.get("resource").getAsString()).isEqualTo(cloud.baseUrl() + "/mcp");
      assertThat(document.getAsJsonArray("authorization_servers").get(0).getAsString()).isEqualTo(ISSUER);
      assertThat(document.getAsJsonArray("scopes_supported").asList()).extracting(JsonElement::getAsString)
        .containsExactly("mcp:tools:read", "mcp:tools:write");
      assertThat(metadata.headers().firstValue("Cache-Control")).hasValue("public, max-age=300");
    }
    assertThat(send(HttpRequest.newBuilder(metadataUri).POST(HttpRequest.BodyPublishers.noBody()).build()).statusCode()).isEqualTo(401);
    assertThat(send(HttpRequest.newBuilder(URI.create(endpoint).resolve(OAuthProtectedResourceMetadata.PATH + "/other")).GET().build()).statusCode()).isEqualTo(401);
    assertThat(send(request("eyJmalformed", "ping", Map.of()).build()).statusCode()).isEqualTo(401);
    assertThat(cloud.getAllServeEvents()).isEmpty();
  }

  @Test
  void it_should_isolate_oauth_and_both_existing_token_headers_on_the_same_endpoint() throws Exception {
    startWithRealAuthentication();
    var reader = grant("123", "reader", "mcp:tools:read", "read:all", "read:all");
    var writer = grant("456", "writer", "mcp:tools:write", "write:all", "write:all");
    var bearer = new Grant("squ_existing_user", "squ_existing_user");
    var legacy = new Grant("sqco_existing_organization", "sqco_existing_organization");
    stubOrganization(reader, "oauth-org");
    stubOrganization(bearer, "bearer-org");
    stubOrganization(legacy, "legacy-org");
    stubWrite(writer);
    stubWrite(bearer);
    stubWrite(legacy);
    assertScopeDenied(send(call(reader, "change_sonar_issue_status", Map.of("key", "issue", "status", "accept"))), "mcp:tools:write");
    var pending = new ArrayList<CompletableFuture<Void>>();
    for (var index = 0; index < 24; index++) {
      var current = switch (index % 4) {
        case 0 -> request(reader.source(), "tools/call", Map.of("name", "search_my_sonarqube_projects", "arguments", Map.of("organization", "oauth-org"))).build();
        case 1 -> request(bearer.source(), "tools/call", Map.of("name", "search_my_sonarqube_projects", "arguments", Map.of()))
          .header("SONARQUBE_ORG", "bearer-org").build();
        case 2 -> request(null, "tools/call", Map.of("name", "search_my_sonarqube_projects", "arguments", Map.of()))
          .header("SONARQUBE_TOKEN", legacy.source()).header("SONARQUBE_ORG", "legacy-org").build();
        default -> call(writer, "change_sonar_issue_status", Map.of("key", "issue", "status", "accept"));
      };
      var expected = switch (index % 4) {
        case 0 -> "oauth-org-project";
        case 1 -> "bearer-org-project";
        case 2 -> "legacy-org-project";
        default -> "successfully changed";
      };
      pending.add(client.sendAsync(current, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> assertSuccess(response, expected)));
    }
    CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).join();
    assertSuccess(send(call(bearer, "change_sonar_issue_status", Map.of("key", "issue", "status", "accept"))), "successfully changed");
    assertSuccess(send(request(null, "tools/call", Map.of("name", "change_sonar_issue_status", "arguments", Map.of("key", "issue", "status", "accept")))
      .header("SONARQUBE_TOKEN", legacy.source()).build()), "successfully changed");
    assertThat(send(request(bearer.source(), "tools/list", Map.of()).header("SONARQUBE_READ_ONLY", "true").build()).body())
      .contains("list_organizations").doesNotContain("change_sonar_issue_status");
    assertThat(send(request(null, "tools/list", Map.of()).header("SONARQUBE_TOKEN", legacy.source()).header("SONARQUBE_TOOLSETS", "projects").build()).body())
      .contains("list_organizations").doesNotContain("change_sonar_issue_status");
    assertScopeDenied(send(call(writer, "list_organizations", Map.of())), "mcp:tools:read");
    for (var invalid : List.of(reader.cloud(), "eyJmalformed", "invalid.oauth.token")) {
      assertThat(send(request(invalid, "ping", Map.of()).header("SONARQUBE_TOKEN", bearer.source()).build()).statusCode()).isEqualTo(401);
    }
    assertThat(send(request(null, "ping", Map.of()).header("SONARQUBE_TOKEN", reader.source()).build()).statusCode()).isEqualTo(401);
    assertThat(send(request(reader.source(), "ping", Map.of()).header("SONARQUBE_ORG", "legacy-org").build()).statusCode()).isEqualTo(400);
    auth0.verify(2, postRequestedFor(urlEqualTo("/oauth/token")));
    assertThat(cloud.getAllServeEvents()).allSatisfy(event -> {
      assertThat(event.getWasMatched()).isTrue();
      assertThat(event.getRequest().getHeader("Authorization")).isIn("Bearer " + reader.cloud(), "Bearer " + writer.cloud(),
        "Bearer " + bearer.cloud(), "Bearer " + legacy.cloud());
    });
  }

  @Test
  void it_should_keep_legacy_and_bearer_tokens_working_when_auth0_is_unavailable() throws Exception {
    startWithRealAuthentication();
    var oauthGrant = grant("123", "outage", "mcp:tools:read", "read:all", "read:all");
    var legacy = new Grant("squ_existing", "squ_existing");
    stubOrganization(legacy, "existing-org");
    auth0.stubFor(post("/oauth/token").atPriority(1).willReturn(aResponse().withStatus(503)));

    assertThat(send(call(oauthGrant, "list_organizations", Map.of())).statusCode()).isEqualTo(503);
    assertSuccess(send(call(legacy, "list_organizations", Map.of())), "existing-org");
    assertSuccess(send(request(null, "tools/call", Map.of("name", "list_organizations", "arguments", Map.of()))
      .header("SONARQUBE_TOKEN", legacy.source()).build()), "existing-org");
    auth0.verify(1, postRequestedFor(urlEqualTo("/oauth/token")));
    cloud.verify(2, getRequestedFor(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH)).withHeader("Authorization", equalTo("Bearer " + legacy.cloud())));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void it_should_preserve_default_token_configuration_and_organization_headers_without_oauth(boolean legacyHeader) throws Exception {
    environment.keySet().removeIf(key -> key.startsWith("SONARQUBE_OAUTH_"));
    startProductionServer();
    var credential = new Grant("existing.server.token", "existing.server.token");
    stubOrganization(credential, "existing-org");
    var request = request(legacyHeader ? null : credential.source(), "tools/call", Map.of("name", "search_my_sonarqube_projects", "arguments", Map.of()))
      .header("SONARQUBE_ORG", "existing-org");
    if (legacyHeader) {
      request.header("SONARQUBE_TOKEN", credential.source());
    }

    assertSuccess(send(request.build()), "existing-org-project");
    var challenge = send(request(null, "ping", Map.of()).build());
    assertThat(challenge.statusCode()).isEqualTo(401);
    assertThat(challenge.headers().firstValue("WWW-Authenticate")).hasValue("Bearer realm=\"MCP Server\"");
    assertThat(auth0.getAllServeEvents()).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({"false,squ_token", "true,squ_token", "false,existing.server.token", "true,existing.server.token",
    "false,eyJexistingServerToken", "true,eyJexistingServerToken"})
  void it_should_keep_sonarqube_server_token_only_regardless_of_credential_format(boolean legacyHeader, String token) throws Exception {
    environment.keySet().removeIf(key -> key.startsWith("SONARQUBE_OAUTH_"));
    environment.remove("SONARQUBE_IS_CLOUD");
    environment.remove("SONARQUBE_CLOUD_API_URL");
    cloud.stubFor(get(SystemApi.STATUS_PATH).willReturn(okJson("{\"version\":\"2026.1.0\",\"status\":\"UP\"}")));
    cloud.stubFor(get(urlPathEqualTo(ComponentsApi.COMPONENTS_SEARCH_PATH)).withHeader("Authorization", equalTo("Bearer " + token))
      .willReturn(okJson("{\"components\":[{\"key\":\"server-project\",\"name\":\"Project\"}],"
        + "\"paging\":{\"pageIndex\":1,\"pageSize\":500,\"total\":1}}")));
    startProductionServer();
    var request = request(legacyHeader ? null : token, "tools/call", Map.of("name", "search_my_sonarqube_projects", "arguments", Map.of()));
    if (legacyHeader) {
      request.header("SONARQUBE_TOKEN", token);
    }

    assertSuccess(send(request.build()), "server-project");
    cloud.verify(getRequestedFor(urlPathEqualTo(ComponentsApi.COMPONENTS_SEARCH_PATH)).withHeader("Authorization", equalTo("Bearer " + token)));
    var challenge = send(request(null, "ping", Map.of()).build());
    assertThat(challenge.statusCode()).isEqualTo(401);
    assertThat(challenge.headers().firstValue("WWW-Authenticate")).hasValue("Bearer realm=\"MCP Server\"");
    var metadata = send(HttpRequest.newBuilder(URI.create(endpoint).resolve(OAuthProtectedResourceMetadata.PATH)).GET().build());
    assertThat(metadata.statusCode()).isEqualTo(401);
    assertThat(metadata.body()).doesNotContain("authorization_servers", "scopes_supported");
    assertThat(auth0.getAllServeEvents()).isEmpty();
  }

  private void startProductionServer() {
    server = new SonarQubeMcpServer(environment);
    server.start();
    endpoint = server.getHttpServerUrl();
  }

  @Test
  void it_should_emit_correlated_events_with_production_authentication_over_stateless_http() throws Exception {
    startWithProductionAuthentication(true);
    var source = currentToken(oauth.resource()).withClaim("scope", "mcp:tools:read");
    var grant = productionGrant(sign(source));
    stubOrganization(grant, "my-org");
    // Failed enrichment must retain this tool's successful outcome.
    cloud.stubFor(get(urlPathEqualTo("/api/users/current")).willReturn(aResponse().withStatus(503)));
    var params = Map.<String, Object>of("protocolVersion", "2025-06-18", "capabilities", Map.of(),
      "clientInfo", Map.of("name", " Custom Client ", "version", "2026.10+preview"));
    assertSuccess(send(request(grant.source(), "initialize", params).build()), "serverInfo");
    assertSuccess(send(call(grant, "list_organizations", Map.of())), "my-org");
    assertThat(send(request(null, "ping", Map.of()).build()).statusCode()).isEqualTo(401);
    assertThat(send(request("eyJ.invalid.token", "initialize", params).build()).statusCode()).isEqualTo(401);
    var noScope = sign(currentToken(oauth.resource()).withClaim("scope", "offline_access"));
    assertThat(send(request(noScope, "initialize", params).build()).statusCode()).isEqualTo(403);
    await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(analyticsEvents()).hasSize(6));
    var events = analyticsEvents();
    var initialized = events.stream().filter(e -> e.getAsJsonObject("metadata").get("event_type").getAsString().endsWith(".McpClientInitialized"))
      .map(e -> e.getAsJsonObject("event_payload")).toList();
    assertThat(initialized).singleElement().satisfies(payload -> {
      assertThat(payload.get("calling_agent_name").getAsString()).isEqualTo(" Custom Client ");
      assertThat(payload.get("calling_agent_version").getAsString()).isEqualTo("2026.10+preview");
      assertThat(payload.get("authentication_method").getAsString()).isEqualTo("oauth");
      assertThat(payload.has("user_uuid")).isFalse();
    });
    var key = initialized.getFirst().get("oauth_client_key").getAsString();
    var tools = events.stream().filter(e -> e.getAsJsonObject("metadata").get("event_type").getAsString().endsWith(".McpToolInvoked")).toList();
    assertThat(tools).singleElement().satisfies(event -> {
      assertThat(event.getAsJsonObject("metadata").get("event_version").getAsString()).isEqualTo("2");
      var payload = event.getAsJsonObject("event_payload");
      assertThat(payload.has("oauth_client_key")).isFalse();
      assertThat(payload.has("authentication_method")).isFalse();
      assertThat(payload.get("is_successful").getAsBoolean()).isTrue();
      assertThat(payload.has("user_uuid")).isFalse();
    });
    var authentication = events.stream().filter(e -> e.getAsJsonObject("metadata").get("event_type").getAsString().endsWith(".McpOAuthAuthenticationCompleted"))
      .map(e -> e.getAsJsonObject("event_payload")).toList();
    assertThat(authentication).hasSize(4);
    assertThat(authentication.stream().filter(e -> e.get("outcome").getAsString().equals("success")).toList())
      .hasSize(2).allSatisfy(payload -> assertThat(payload.get("oauth_client_key").getAsString()).isEqualTo(key));
    assertThat(authentication.stream().filter(e -> e.has("failure_stage") && e.get("failure_stage").getAsString().equals("mcp_token_verification")).toList())
      .singleElement().satisfies(payload -> assertThat(payload.has("oauth_client_key")).isFalse());
    assertThat(authentication.stream().filter(e -> e.has("failure_category") && e.get("failure_category").getAsString().equals("insufficient_scope")).toList())
      .singleElement().satisfies(payload -> assertThat(payload.get("oauth_client_key").getAsString()).isEqualTo(key));
    assertThat(events).allSatisfy(event -> assertThat(event.toString()).doesNotContain(grant.source(), grant.cloud(), "test-secret", "oauth2|github"));
    assertThat(events.stream().map(e -> e.getAsJsonObject("event_payload").get("mcp_server_id").getAsString()).distinct()).hasSize(1);
    cloud.verify(1, postRequestedFor(urlEqualTo("/oauth/token")));
  }

  @Test
  void it_should_keep_application_attribution_isolated_for_concurrent_http_requests() throws Exception {
    startWithProductionAuthentication(true);
    var first = productionGrant(sign(currentToken(oauth.resource()).withClaim("azp", "application-a").withClaim("scope", "mcp:tools:read")), "application-a");
    var second = productionGrant(sign(currentToken(oauth.resource()).withClaim("azp", "application-b").withClaim("scope", "mcp:tools:read")), "application-b");
    stubOrganization(first, "org-a");
    stubOrganization(second, "org-b");
    cloud.stubFor(get(urlPathEqualTo("/api/users/current")).withHeader("Authorization", equalTo("Bearer " + first.cloud()))
      .willReturn(okJson("{\"id\":\"user-a\"}")));
    cloud.stubFor(get(urlPathEqualTo("/api/users/current")).withHeader("Authorization", equalTo("Bearer " + second.cloud()))
      .willReturn(okJson("{\"id\":\"user-b\"}")));
    var requests = List.of(
      request(first.source(), "initialize", Map.of("protocolVersion", "2025-06-18", "capabilities", Map.of(),
        "clientInfo", Map.of("name", "client-a", "version", "1"))).build(),
      request(second.source(), "initialize", Map.of("protocolVersion", "2025-06-18", "capabilities", Map.of(),
        "clientInfo", Map.of("name", "client-b", "version", "2"))).build(),
      call(first, "list_organizations", Map.of()),
      call(second, "search_my_sonarqube_projects", Map.of("organization", "org-b")));
    var pending = requests.stream().map(request -> client.sendAsync(request, HttpResponse.BodyHandlers.ofString())).toList();
    for (var response : pending) {
      assertSuccess(response.get(10, TimeUnit.SECONDS), "result");
    }
    await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(analyticsEvents()).hasSize(8));
    var keys = new HashMap<String, String>();
    var users = new HashMap<String, String>();
    analyticsEvents().stream().filter(event -> event.getAsJsonObject("metadata").get("event_type").getAsString().endsWith(".McpClientInitialized"))
      .map(event -> event.getAsJsonObject("event_payload"))
      .forEach(payload -> {
        keys.put(payload.get("calling_agent_name").getAsString(), payload.get("oauth_client_key").getAsString());
        users.put(payload.get("calling_agent_name").getAsString(), payload.get("user_uuid").getAsString());
        assertThat(payload.has("organization_uuid_v4")).isFalse();
        assertThat(payload.has("sqs_installation_id")).isFalse();
      });
    assertThat(keys).hasSize(2);
    assertThat(users).containsExactlyInAnyOrderEntriesOf(Map.of("client-a", "user-a", "client-b", "user-b"));
    assertThat(keys.get("client-a")).isNotNull().isNotEqualTo(keys.get("client-b"));
    analyticsEvents().stream().filter(event -> event.getAsJsonObject("metadata").get("event_type").getAsString().endsWith(".McpToolInvoked"))
      .map(event -> event.getAsJsonObject("event_payload"))
      .forEach(payload -> {
        assertThat(payload.has("oauth_client_key")).isFalse();
        assertThat(payload.has("authentication_method")).isFalse();
      });
    cloud.verify(2, postRequestedFor(urlEqualTo("/oauth/token")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"Authorization", "SONARQUBE_TOKEN"})
  void it_should_report_header_token_initialization_and_tools_without_oauth_attribution(String header) throws Exception {
    startWithProductionAuthentication(true);
    var params = Map.<String, Object>of("protocolVersion", "2025-06-18", "capabilities", Map.of(), "clientInfo", Map.of("name", "token-client", "version", "1"));
    var credential = header.equals("Authorization") ? "Bearer existing-token" : "existing-token";
    cloud.stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH)).willReturn(okJson("[]")));
    assertSuccess(send(request(null, "initialize", params).header(header, credential).build()), "serverInfo");
    assertSuccess(send(request(null, "tools/call", Map.of("name", "list_organizations", "arguments", Map.of())).header(header, credential).build()), "organizations");
    await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(analyticsEvents()).hasSize(2));
    assertThat(analyticsEvents()).allSatisfy(event -> {
      var payload = event.getAsJsonObject("event_payload");
      if (event.getAsJsonObject("metadata").get("event_type").getAsString().endsWith("McpClientInitialized")) {
        assertThat(payload.get("authentication_method").getAsString()).isEqualTo("token");
      } else {
        assertThat(payload.has("authentication_method")).isFalse();
        assertThat(event.getAsJsonObject("metadata").get("event_version").getAsString()).isEqualTo("2");
      }
      assertThat(payload.has("oauth_client_key")).isFalse();
      assertThat(event.getAsJsonObject("metadata").get("event_type").getAsString()).doesNotContain("AuthenticationCompleted");
    });
    cloud.verify(0, postRequestedFor(urlEqualTo("/oauth/token")));
  }

  @Test
  void it_should_enrich_server_initialization_without_organization_lookups() throws Exception {
    environment.keySet().removeIf(key -> key.startsWith("SONARQUBE_OAUTH_"));
    environment.remove("SONARQUBE_IS_CLOUD");
    environment.remove("SONARQUBE_CLOUD_API_URL");
    environment.put("TELEMETRY_DISABLED", "false");
    cloud.stubFor(get(SystemApi.STATUS_PATH).willReturn(okJson("{\"id\":\"installation\",\"version\":\"2026.1.0\",\"status\":\"UP\"}")));
    cloud.stubFor(get("/api/users/current").withHeader("Authorization", equalTo("Bearer existing-token"))
      .willReturn(okJson("{\"id\":\"server-user\"}")));
    startProductionServer();
    assertSuccess(send(request("existing-token", "initialize", Map.of("protocolVersion", "2025-06-18", "capabilities", Map.of(),
      "clientInfo", Map.of("name", "server-client", "version", "1"))).build()), "serverInfo");
    await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(analyticsEvents()).hasSize(1));
    var payload = analyticsEvents().getFirst().getAsJsonObject("event_payload");
    assertThat(payload.get("user_uuid").getAsString()).isEqualTo("server-user");
    assertThat(payload.get("sqs_installation_id").getAsString()).isEqualTo("installation");
    assertThat(payload.get("authentication_method").getAsString()).isEqualTo("token");
    assertThat(payload.has("organization_uuid_v4")).isFalse();
    assertThat(payload.has("oauth_client_key")).isFalse();
    cloud.verify(0, getRequestedFor(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH)));
    assertThat(auth0.getAllServeEvents()).isEmpty();
  }

  @Test
  void it_should_disable_all_three_http_events_when_telemetry_is_disabled() throws Exception {
    startWithProductionAuthentication(false);
    var grant = productionGrant(sign(currentToken(oauth.resource()).withClaim("scope", "mcp:tools:read")));
    stubOrganization(grant, "org");
    assertSuccess(send(request(grant.source(), "initialize", Map.of("protocolVersion", "2025-06-18", "capabilities", Map.of(),
      "clientInfo", Map.of("name", "client", "version", "1"))).build()), "serverInfo");
    assertSuccess(send(call(grant, "list_organizations", Map.of())), "org");
    assertThat(send(request("eyJ.invalid.token", "ping", Map.of()).build()).statusCode()).isEqualTo(401);
    server.shutdown();
    assertThat(analyticsEvents()).isEmpty();
  }

  private void startWithProductionAuthentication(boolean telemetryEnabled) {
    environment.put("TELEMETRY_DISABLED", Boolean.toString(!telemetryEnabled));
    environment.put("SONARQUBE_OAUTH_ISSUER", cloud.baseUrl() + "/");
    cloud.stubFor(get("/.well-known/jwks.json").willReturn(okJson(GSON.toJson(Map.of("keys", List.of(keyValues("key", KEY)))))));
    oauth = new McpServerLaunchConfiguration(environment).getOAuthConfiguration();
    startProductionServer();
  }

  private JWTCreator.Builder currentToken(String audience) {
    return token(audience).withIssuer(oauth.issuer().toString()).withIssuedAt(Instant.now().minusSeconds(1)).withExpiresAt(Instant.now().plusSeconds(299));
  }

  private Grant productionGrant(String source) {
    return productionGrant(source, "client");
  }

  private Grant productionGrant(String source, String initiatingClient) {
    var downstream = sign(currentToken(oauth.cloudAudience()).withClaim("azp", oauth.clientId()).withClaim("scope", "read:all")
      .withClaim("act", Map.of("sub", oauth.clientId(), "act", Map.of("sub", initiatingClient))));
    cloud.stubFor(post("/oauth/token").withRequestBody(matchingJsonPath("$.subject_token", equalTo(source)))
      .willReturn(okJson(GSON.toJson(Map.of("access_token", downstream, "token_type", "Bearer")))));
    return new Grant(source, downstream);
  }

  private List<JsonObject> analyticsEvents() {
    return cloud.findAll(postRequestedFor(urlEqualTo("/gessie"))).stream()
      .map(request -> JsonParser.parseString(request.getBodyAsString()).getAsJsonObject()).toList();

  }

  private void startWithRealAuthentication() {
    var configuration = new McpServerLaunchConfiguration(environment);
    oauth = configuration.getOAuthConfiguration();
    var keys = new Auth0JwkProvider(URI.create(auth0.baseUrl() + "/"), httpClients.getAnonymousHttpClient(), clock);
    var service = new Auth0OAuthAuthenticationService(oauth, new Auth0TokenVerifier(oauth, keys, clock), new Auth0TokenExchange(oauth, auth0.baseUrl()), clock);
    var transport = new HttpServerTransportProvider(HttpTransportSettings.from(configuration), new OAuthSupport(
      new OAuthProtectedResourceMetadata(oauth.resource(), oauth.issuer().toString(), configuration.getOAuthMetadataUrl()), service::authenticate));
    server = new SonarQubeMcpServer(null, transport, environment);
    server.start();
    endpoint = transport.getServerUrl();
  }

  private Grant grant(String user, String id, String sourceScopes, String requestedScopes, String cloudScopes) {
    return grantForSubject("oauth2|github|github|" + user, id, sourceScopes, requestedScopes, cloudScopes);
  }

  private Grant grantForSubject(String subject, String id, String sourceScopes, String requestedScopes, String cloudScopes) {
    var source = sign(token(oauth.resource()).withSubject(subject).withJWTId(id).withClaim("scope", sourceScopes));
    var downstream = sign(token(oauth.cloudAudience()).withSubject(subject).withJWTId(id + "-cloud")
      .withClaim("azp", oauth.clientId()).withClaim("scope", cloudScopes)
      .withClaim("act", Map.of("sub", oauth.clientId(), "act", Map.of("sub", "client"))));
    auth0.stubFor(post("/oauth/token").withRequestBody(matchingJsonPath("$.subject_token", equalTo(source)))
      .withRequestBody(matchingJsonPath("$.scope", equalTo(requestedScopes)))
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
    assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
    assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).hasValue(CLIENT_ORIGIN);
    assertThat(response.headers().firstValue("Access-Control-Expose-Headers")).hasValue("WWW-Authenticate");
    assertThat(response.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(value ->
      assertThat(value).contains("scope=\"" + requiredScope + "\"", "error=\"insufficient_scope\""));
    assertThat(response.body()).contains("Insufficient OAuth scope");
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
