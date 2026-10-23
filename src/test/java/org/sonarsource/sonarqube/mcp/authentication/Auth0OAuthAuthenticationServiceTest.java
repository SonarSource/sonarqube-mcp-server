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

import ch.qos.logback.classic.Level;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import com.google.gson.Gson;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.analytics.OAuthAuthenticationResult;
import org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.LogCapture;
import org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.MutableClock;
import org.sonarsource.sonarqube.mcp.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.KEY;
import static org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.OTHER_KEY;
import static org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.keyValues;

class Auth0OAuthAuthenticationServiceTest {
  // Example hosts are token claim values; exchanges and signing-key requests are mocked.
  private static final String ISSUER = "https://auth.example.com/";
  private static final String RESOURCE = "https://api.example.com/mcp";
  private static final String CLOUD = "https://api.example.com/";
  private static final String USER = "oauth2|github|github|123456789";
  private static final String CLIENT = "mcp-client";
  private static final String BACKEND = "mcp-backend";
  private Algorithm algorithm;
  private OAuthConfiguration configuration;
  private Auth0TokenVerifier verifier;
  private Auth0TokenExchange exchange;
  private Auth0OAuthAuthenticationService service;
  private MutableClock clock;
  private final List<OAuthAuthenticationResult> authenticationEvents = new CopyOnWriteArrayList<>();

  @BeforeEach
  void setUp() {
    algorithm = Algorithm.RSA256((RSAPublicKey) KEY.getPublic(), (RSAPrivateKey) KEY.getPrivate());
    var keyProvider = mock(RSAKeyProvider.class);
    when(keyProvider.getPublicKeyById("test-key")).thenReturn((RSAPublicKey) KEY.getPublic());
    configuration = config(Duration.ofSeconds(60), 256);
    clock = new MutableClock();
    verifier = spy(new Auth0TokenVerifier(configuration, keyProvider, clock));
    exchange = mock(Auth0TokenExchange.class);
    service = new Auth0OAuthAuthenticationService(configuration, verifier, exchange, clock, authenticationEvents::add);
  }

  @Test
  void it_should_use_the_provided_http_client_for_signing_keys() {
    var client = mock(HttpClient.class);
    var response = mock(HttpClient.Response.class);
    when(response.isSuccessful()).thenReturn(true);
    when(response.bodyAsString()).thenReturn(new Gson().toJson(Map.of("keys", List.of(keyValues("test-key", KEY)))));
    when(client.getAsyncAnonymousWithoutRedirects(ISSUER + ".well-known/jwks.json"))
      .thenReturn(CompletableFuture.completedFuture(response));
    var authentication = new Auth0OAuthAuthenticationService(configuration, client);
    var source = source().withSubject(" ").withIssuedAt(Instant.now().minusSeconds(1))
      .withExpiresAt(Instant.now().plusSeconds(299)).sign(algorithm);

    assertThatThrownBy(() -> authentication.authenticate(source))
      .isInstanceOfSatisfying(OAuthAuthenticationException.class, failure -> assertThat(failure.status()).isEqualTo(401));

    verify(client).getAsyncAnonymousWithoutRedirects(ISSUER + ".well-known/jwks.json");
    verify(response).close();
  }

  @ParameterizedTest
  @MethodSource("scopeCases")
  void it_should_exchange_only_explicit_independent_delegation_scopes(String scope, Set<String> expected) {
    var source = source().withClaim("scope", scope).sign(algorithm);
    var cloud = downstream().withClaim("scope", String.join(" ", expected)).sign(algorithm);
    when(exchange.exchange(source, expected)).thenReturn(cloud);

    Function<String, OAuthRequestAuthentication> authenticate = service::authenticate;
    var authenticated = authenticate.apply(source);

    assertThat(authenticated.cloudToken()).isEqualTo(cloud).isNotEqualTo(source);
    assertThat(authenticated.scopes()).isEqualTo(expected);
    assertThat(authenticated.toString()).doesNotContain(cloud);
    verify(exchange).exchange(source, expected);
  }

  static Stream<Arguments> scopeCases() {
    return Stream.of(Arguments.of("mcp:tools:read", Set.of("read:all")), Arguments.of("mcp:tools:write", Set.of("write:all")),
      Arguments.of("mcp:tools:read mcp:tools:write", Set.of("read:all", "write:all")),
      Arguments.of("mcp:tools:read offline_access", Set.of("read:all")),
      Arguments.of("mcp:tools:write openid profile", Set.of("write:all")),
      Arguments.of("mcp:tools:read mcp:tools:write offline_access other:permission", Set.of("read:all", "write:all")),
      Arguments.of("mcp:tools:read read:all write:all", Set.of("read:all")),
      Arguments.of("mcp:tools:write read:all write:all", Set.of("write:all")));
  }

  @ParameterizedTest
  @CsvSource({"301,300", "300,301", "3600,3600", "7200,3600", "3600,7200"})
  void it_should_accept_auth0_defined_token_lifetimes(long sourceLifetime, long cloudLifetime) {
    var issuedAt = clock.instant().minusSeconds(1);
    var source = source().withIssuedAt(issuedAt).withExpiresAt(issuedAt.plusSeconds(sourceLifetime))
      .withClaim("scope", "mcp:tools:read").sign(algorithm);
    var cloud = downstream().withIssuedAt(issuedAt).withExpiresAt(issuedAt.plusSeconds(cloudLifetime))
      .withClaim("scope", "read:all").sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all"))).thenReturn(cloud);

    var authenticated = service.authenticate(source);

    assertThat(authenticated.cloudToken()).isEqualTo(cloud).isNotEqualTo(source);
    assertThat(authenticated.scopes()).containsExactly("read:all");
    verify(exchange).exchange(source, Set.of("read:all"));
  }

  @ParameterizedTest
  @ValueSource(ints = {30, 29})
  void it_should_reject_source_expiration_at_or_before_issuance(int expirationSeconds) {
    var source = source().withIssuedAt(clock.instant().plusSeconds(30)).withExpiresAt(clock.instant().plusSeconds(expirationSeconds))
      .withClaim("scope", "mcp:tools:read").sign(algorithm);

    assertStatus(source, 401);

    verify(exchange, times(0)).exchange(anyString(), any());
  }

  @ParameterizedTest
  @ValueSource(ints = {30, 29})
  void it_should_reject_cloud_expiration_at_or_before_issuance(int expirationSeconds) {
    var source = source().withClaim("scope", "mcp:tools:read").sign(algorithm);
    var cloud = downstream().withIssuedAt(clock.instant().plusSeconds(30)).withExpiresAt(clock.instant().plusSeconds(expirationSeconds))
      .withClaim("scope", "read:all").sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all"))).thenReturn(cloud);

    assertStatus(source, 502);

    verify(exchange).exchange(source, Set.of("read:all"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "read:all", "write:all", "read:all write:all", "offline_access", "openid offline_access", "other:permission"})
  void it_should_reject_source_without_recognized_delegation_scopes_before_exchange(String scope) {
    try (var logs = new LogCapture(false)) {
      assertStatus(source().withClaim("scope", scope).sign(algorithm), 403);
      logs.assertLogged(Level.WARN, "OAuth source token rejected (missing_mcp_scope)");
    }
    verify(exchange, times(0)).exchange(anyString(), any());
  }

  @Test
  void it_should_reject_source_without_delegation() {
    assertStatus(source().sign(algorithm), 403);
    assertStatus(source().withNullClaim("scope").sign(algorithm), 403);
    verify(exchange, times(0)).exchange(anyString(), any());
  }

  @ParameterizedTest
  @MethodSource("malformedSourceScopes")
  void it_should_reject_malformed_source_scopes_without_exchange(Object scope) {
    assertStatus(source().withPayload(Map.of("scope", scope)).sign(algorithm), 403);
    verify(exchange, times(0)).exchange(anyString(), any());
  }

  static Stream<Object> malformedSourceScopes() {
    return Stream.of(true, 123, List.of("mcp:tools:read"), Map.of("scope", "mcp:tools:read"));
  }

  @Test
  void it_should_log_scope_decode_failures_without_claim_contents() {
    var source = source().withClaim("scope", "mcp:tools:read").sign(algorithm);
    var decoded = spy(JWT.decode(source));
    when(verifier.verifyMcp(source)).thenReturn(decoded);
    doThrow(new JWTDecodeException("private-scope-data")).when(decoded).getClaim("scope");

    try (var logs = new LogCapture(false)) {
      assertStatus(source, 403);
      logs.assertLogged(Level.WARN, "OAuth source scope decoding failed (JWTDecodeException)");
    }
  }

  @ParameterizedTest
  @CsvSource({"lifetime,invalid_lifetime", "aud,unexpected_cloud_audience", "sub,missing_subject", "azp,missing_authorized_party",
    "org_id,organization_binding", "bound_org,organization_binding", "act,unexpected_actor", "existing_actor,unexpected_actor"})
  void it_should_reject_untrusted_source_before_decoding_scopes(String field, String reason) {
    var token = source().withClaim("scope", "mcp:tools:read");
    switch (field) {
      case "lifetime" -> token.withIssuedAt(clock.instant().plusSeconds(30)).withExpiresAt(clock.instant().plusSeconds(29));
      case "aud" -> token.withAudience(RESOURCE, CLOUD);
      case "sub" -> token.withSubject(" ");
      case "azp" -> token.withClaim("azp", "");
      case "bound_org" -> token.withClaim("org_id", "private-organization-data");
      case "existing_actor" -> token.withClaim("act", Map.of("sub", "private-actor-data"));
      default -> token.withNullClaim(field);
    }
    var source = token.sign(algorithm);
    var decoded = spy(JWT.decode(source));
    when(verifier.verifyMcp(source)).thenReturn(decoded);
    doThrow(new JWTDecodeException("malformed scope")).when(decoded).getClaim("scope");

    try (var logs = new LogCapture(false)) {
      assertStatus(source, 401);
      logs.assertLogged(Level.WARN, "OAuth source token rejected (" + reason + ")");
    }
    verify(exchange, times(0)).exchange(anyString(), any());
  }

  @Test
  void it_should_log_unexpected_cache_load_failures_without_the_cause_message() {
    var source = source().withClaim("scope", "mcp:tools:read").sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all"))).thenThrow(new IllegalStateException("private-token-or-secret"));

    try (var logs = new LogCapture()) {
      assertStatus(source, 503);
      logs.assertLogged(Level.WARN, "OAuth exchange cache load failed (IllegalStateException)");
    }
  }

  @Test
  void it_should_not_grant_read_from_write_only_delegation() {
    var source = source().withClaim("scope", "mcp:tools:write").sign(algorithm);
    when(exchange.exchange(source, Set.of("write:all"))).thenReturn(downstream().withClaim("scope", "read:all").sign(algorithm));

    assertStatus(source, 502);
    verify(exchange).exchange(source, Set.of("write:all"));
  }

  @ParameterizedTest
  @MethodSource("invalidSources")
  void it_should_reject_source_trust_or_organization_changes(String field, String value) {
    var source = source().withClaim("scope", "mcp:tools:read");
    switch (field) {
      case "iss" -> source.withIssuer(value);
      case "aud" -> source.withAudience(value);
      case "sub" -> source.withSubject(value);
      default -> source.withClaim(field, value);
    }
    assertStatus(source.sign(algorithm), 401);
  }

  static Stream<Arguments> invalidSources() {
    return Stream.of(Arguments.of("iss", "https://other-tenant.auth0.com/"), Arguments.of("aud", CLOUD),
      Arguments.of("azp", ""), Arguments.of("org_id", "org_sso"));
  }

  @Test
  void it_should_reject_expired_or_missing_expiration_source() {
    assertStatus(source().withExpiresAt(clock.instant().minusSeconds(1)).withClaim("scope", "mcp:tools:read").sign(algorithm), 401);
    assertStatus(JWT.create().withIssuer(ISSUER).withAudience(RESOURCE).withSubject(USER).withClaim("azp", CLIENT)
      .withIssuedAt(clock.instant()).withClaim("scope", "mcp:tools:read").withKeyId("test-key").sign(algorithm), 401);
  }

  @Test
  void it_should_reject_wrong_actor_chain_depth() {
    var source = source().withClaim("scope", "mcp:tools:read").sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all"))).thenReturn(downstream().withClaim("scope", "read:all")
      .withClaim("act", Map.of("sub", BACKEND, "act", Map.of("sub", CLIENT, "act", Map.of("sub", "unexpected")))).sign(algorithm));
    assertStatus(source, 502);
  }

  @ParameterizedTest
  @MethodSource("invalidExchanges")
  void it_should_reject_expanded_or_untrusted_downstream_token(String field, String value) {
    var source = source().withClaim("scope", "mcp:tools:read").sign(algorithm);
    var result = downstream().withClaim("scope", "read:all");
    switch (field) {
      case "iss" -> result.withIssuer(value);
      case "aud" -> result.withAudience(value);
      case "sub" -> result.withSubject(value);
      case "actor" -> result.withClaim("act", Map.of("sub", value, "act", Map.of("sub", CLIENT)));
      case "initiator" -> result.withClaim("act", Map.of("sub", BACKEND, "act", Map.of("sub", value)));
      default -> result.withClaim(field, value);
    }
    when(exchange.exchange(source, Set.of("read:all"))).thenReturn(result.sign(algorithm));
    assertStatus(source, 502);
  }

  static Stream<Arguments> invalidExchanges() {
    return Stream.of(Arguments.of("scope", "write:all"), Arguments.of("scope", "read:all write:all"),
      Arguments.of("scope", "read:all other:permission"), Arguments.of("scope", "read:all offline_access"), Arguments.of("scope", "mcp:tools:read"),
      Arguments.of("scope", "read:all mcp:tools:read"), Arguments.of("scope", ""), Arguments.of("azp", "different-backend"),
      Arguments.of("actor", "different-backend"), Arguments.of("initiator", "different-client"), Arguments.of("sub", USER + "2"),
      Arguments.of("aud", RESOURCE), Arguments.of("iss", "https://other-tenant.auth0.com/"), Arguments.of("org_id", "org_sso"));
  }

  @ParameterizedTest
  @ValueSource(strings = {USER, "samlp|enterprise-connection|user-id", "opaque-enterprise-user"})
  void it_should_exchange_matching_subjects_reuse_authorization_and_reverify_every_request(String subject) {
    var source = source().withSubject(subject).withClaim("scope", "mcp:tools:read").sign(algorithm);
    var cloud = downstream().withSubject(subject).withClaim("scope", "read:all").sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all"))).thenReturn(cloud);
    var authentication = service.authenticate(source);
    assertThat(authentication.cloudToken()).isEqualTo(cloud);
    assertThat(authentication.scopes()).containsExactly("read:all");
    service.authenticate(source);
    verify(exchange).exchange(source, Set.of("read:all"));
    verify(verifier, times(2)).verifyMcp(source);
    verify(verifier, times(3)).verifyCloud(cloud);
    clock.advance(Duration.ofSeconds(59));
    service.authenticate(source);
    verify(exchange).exchange(source, Set.of("read:all"));
    verify(verifier, times(3)).verifyMcp(source);
    verify(verifier, times(4)).verifyCloud(cloud);
    clock.advance(Duration.ofSeconds(1));
    service.authenticate(source);
    verify(exchange, times(2)).exchange(source, Set.of("read:all"));
  }

  @Test
  void it_should_reject_mismatched_enterprise_subjects() {
    var source = source().withSubject("samlp|enterprise-connection|first-user").withClaim("scope", "mcp:tools:read").sign(algorithm);
    var cloud = downstream().withSubject("samlp|enterprise-connection|second-user").withClaim("scope", "read:all").sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all"))).thenReturn(cloud);

    assertStatus(source, 502);
    verify(exchange).exchange(source, Set.of("read:all"));
  }

  @Test
  void it_should_isolate_two_authorizations_for_the_same_user() {
    var first = source().withClaim("scope", "mcp:tools:read").withJWTId("first").sign(algorithm);
    var second = source().withClaim("scope", "mcp:tools:read").withJWTId("second").sign(algorithm);
    when(exchange.exchange(anyString(), any())).thenReturn(downstream().withClaim("scope", "read:all").sign(algorithm));
    service.authenticate(first);
    service.authenticate(second);
    verify(exchange, times(2)).exchange(anyString(), any());
  }

  @Test
  void it_should_isolate_the_same_authorization_between_backend_configurations() {
    var source = source().withClaim("scope", "mcp:tools:read").sign(algorithm);
    var cloud = downstream().withClaim("scope", "read:all").sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all"))).thenReturn(cloud);
    var otherConfiguration = new OAuthConfiguration(ISSUER, RESOURCE, CLOUD, "other-backend", "other-secret");
    var otherExchange = mock(Auth0TokenExchange.class);
    var otherService = new Auth0OAuthAuthenticationService(otherConfiguration, verifier, otherExchange, clock);
    var otherCloud = downstream().withClaim("scope", "read:all").withClaim("azp", "other-backend")
      .withClaim("act", Map.of("sub", "other-backend", "act", Map.of("sub", CLIENT))).sign(algorithm);
    when(otherExchange.exchange(source, Set.of("read:all"))).thenReturn(otherCloud);

    assertThat(service.authenticate(source).cloudToken()).isEqualTo(cloud);
    assertThat(otherService.authenticate(source).cloudToken()).isEqualTo(otherCloud);
    assertThat(service.authenticate(source).cloudToken()).isEqualTo(cloud);
    assertThat(otherService.authenticate(source).cloudToken()).isEqualTo(otherCloud);
    verify(exchange).exchange(source, Set.of("read:all"));
    verify(otherExchange).exchange(source, Set.of("read:all"));
  }

  @Test
  void it_should_isolate_clients_users_and_scope_combinations() {
    when(exchange.exchange(anyString(), any())).thenAnswer(invocation -> {
      var original = JWT.decode(invocation.getArgument(0));
      Set<String> scopes = invocation.getArgument(1);
      return downstream().withSubject(original.getSubject()).withClaim("scope", String.join(" ", scopes))
        .withClaim("act", Map.of("sub", BACKEND, "act", Map.of("sub", original.getClaim("azp").asString()))).sign(algorithm);
    });
    for (var user : List.of(USER, USER + "2")) {
      for (var client : List.of(CLIENT, "other-client")) {
        for (var scope : List.of("mcp:tools:read", "mcp:tools:write", "mcp:tools:read mcp:tools:write")) {
          service.authenticate(source().withSubject(user).withClaim("azp", client).withClaim("scope", scope).sign(algorithm));
        }
      }
    }
    verify(exchange, times(12)).exchange(anyString(), any());
  }

  @Test
  void it_should_bound_cache_and_allow_disabling_it() {
    configuration = config(Duration.ofSeconds(30), 1);
    service = new Auth0OAuthAuthenticationService(configuration, verifier, exchange, clock, authenticationEvents::add);
    when(exchange.exchange(anyString(), any())).thenReturn(downstream().withClaim("scope", "read:all").sign(algorithm));
    var first = source().withClaim("scope", "mcp:tools:read").withJWTId("first").sign(algorithm);
    var second = source().withClaim("scope", "mcp:tools:read").withJWTId("second").sign(algorithm);
    service.authenticate(first);
    service.authenticate(second);
    service.authenticate(first);
    verify(exchange, times(2)).exchange(first, Set.of("read:all"));
    service = new Auth0OAuthAuthenticationService(config(Duration.ZERO, 1), verifier, exchange, clock);
    service.authenticate(second);
    service.authenticate(second);
    verify(exchange, times(3)).exchange(second, Set.of("read:all"));
  }

  @Test
  void it_should_expire_cache_at_the_earliest_token_expiration() {
    var source = source().withExpiresAt(clock.instant().plusSeconds(10)).withClaim("scope", "mcp:tools:read").sign(algorithm);
    var cloud = downstream().withExpiresAt(clock.instant().plusSeconds(5)).withClaim("scope", "read:all").sign(algorithm);
    var freshCloud = downstream().withExpiresAt(clock.instant().plusSeconds(15)).withClaim("scope", "read:all").sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all"))).thenReturn(cloud, freshCloud);
    service.authenticate(source);
    clock.advance(Duration.ofSeconds(5));
    assertThat(service.authenticate(source).cloudToken()).isEqualTo(freshCloud);
    verify(exchange, times(2)).exchange(source, Set.of("read:all"));
  }

  @Test
  void it_should_limit_concurrent_exchanges_and_release_capacity_after_failure() throws Exception {
    var started = new CountDownLatch(16);
    var released = new CountDownLatch(1);
    when(exchange.exchange(anyString(), any())).thenAnswer(invocation -> {
      started.countDown();
      released.await(5, TimeUnit.SECONDS);
      throw OAuthAuthenticationException.unavailable();
    });
    var sources = IntStream.range(0, 17)
      .mapToObj(index -> source().withClaim("scope", "mcp:tools:read").withJWTId("concurrent-" + index).sign(algorithm)).toList();
    var source = sources.get(16);
    var executor = Executors.newFixedThreadPool(16);
    var tasks = sources.subList(0, 16).stream().map(token -> CompletableFuture.runAsync(() -> assertStatus(token, 503), executor)).toList();
    try {
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
      assertStatus(source, 503);
      verify(exchange, times(16)).exchange(anyString(), any());
    } finally {
      released.countDown();
      CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
      executor.close();
    }
    assertStatus(source, 503);
    verify(exchange, times(17)).exchange(anyString(), any());
  }

  @Test
  void it_should_share_a_single_exchange_between_concurrent_requests_for_the_same_authorization() throws Exception {
    var source = source().withClaim("scope", "mcp:tools:read").sign(algorithm);
    var cloud = downstream().withClaim("scope", "read:all").sign(algorithm);
    var started = new CountDownLatch(1);
    var released = new CountDownLatch(1);
    when(exchange.exchange(source, Set.of("read:all"))).thenAnswer(invocation -> {
      started.countDown();
      assertThat(released.await(5, TimeUnit.SECONDS)).isTrue();
      return cloud;
    });
    var workers = new ArrayList<Thread>();
    try (var executor = Executors.newFixedThreadPool(16, runnable -> {
      var thread = new Thread(runnable);
      workers.add(thread);
      return thread;
    })) {
      var requests = new ArrayList<Future<OAuthRequestAuthentication>>();
      requests.add(executor.submit(() -> service.authenticate(source)));
      try {
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        for (var i = 1; i < 16; i++) {
          requests.add(executor.submit(() -> service.authenticate(source)));
        }
        await().atMost(Duration.ofSeconds(3)).until(() -> workers.stream().skip(1)
          .allMatch(thread -> thread.getState() == Thread.State.WAITING));
      } finally {
        released.countDown();
      }
      for (var request : requests) {
        assertThat(request.get(5, TimeUnit.SECONDS).cloudToken()).isEqualTo(cloud);
      }
    }
    verify(exchange).exchange(source, Set.of("read:all"));
    assertThat(authenticationEvents).hasSize(16).allSatisfy(event -> assertThat(event.outcome()).isEqualTo("success"));
  }

  @Test
  void it_should_not_cache_failed_exchanges() {
    var source = source().withClaim("scope", "mcp:tools:read").sign(algorithm);
    var cloud = downstream().withClaim("scope", "read:all").sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all"))).thenThrow(OAuthAuthenticationException.unavailable()).thenReturn(cloud);

    assertStatus(source, 503);
    assertThat(service.authenticate(source).cloudToken()).isEqualTo(cloud);
    assertThat(service.authenticate(source).cloudToken()).isEqualTo(cloud);

    verify(exchange, times(2)).exchange(source, Set.of("read:all"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"mcp:tools:read mcp:tools:write", "mcp:tools:read mcp:tools:write offline_access openid profile"})
  void it_should_return_only_the_scopes_actually_granted_in_the_cloud_token(String scope) {
    var source = source().withClaim("scope", scope).sign(algorithm);
    var cloud = downstream().withClaim("scope", "read:all").sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all", "write:all"))).thenReturn(cloud);

    assertThat(service.authenticate(source).scopes()).containsExactly("read:all");
    assertThat(service.authenticate(source).scopes()).containsExactly("read:all");
    verify(exchange).exchange(source, Set.of("read:all", "write:all"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"read:all read:all", "read:all "})
  void it_should_preserve_accepted_downstream_scope_formatting(String grantedScope) {
    var source = source().withClaim("scope", "mcp:tools:read").sign(algorithm);
    var cloud = downstream().withClaim("scope", grantedScope).sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all"))).thenReturn(cloud);

    assertThat(service.authenticate(source).scopes()).containsExactly("read:all");
    assertThat(service.authenticate(source).scopes()).containsExactly("read:all");
    verify(exchange).exchange(source, Set.of("read:all"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"act", "org_id"})
  void it_should_reject_even_null_forbidden_source_claims(String claim) {
    assertStatus(source().withNullClaim(claim).withClaim("scope", "mcp:tools:read").sign(algorithm), 401);
    verify(exchange, times(0)).exchange(anyString(), any());
  }

  @Test
  void it_should_reject_a_source_token_also_intended_for_cloud() {
    assertStatus(source().withAudience(RESOURCE, CLOUD).withClaim("scope", "mcp:tools:read").sign(algorithm), 401);
    verify(exchange, times(0)).exchange(anyString(), any());
  }

  @ParameterizedTest
  @MethodSource("malformedActors")
  void it_should_sanitize_malformed_actor_chains(Object actor) {
    var source = source().withClaim("scope", "mcp:tools:read").sign(algorithm);
    var cloud = downstream().withClaim("scope", "read:all").withPayload(Map.of("act", actor)).sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all"))).thenReturn(cloud);

    assertStatus(source, 502);
  }

  @Test
  void it_should_log_downstream_decode_failures_without_claim_contents() {
    var source = source().withClaim("scope", "mcp:tools:read").sign(algorithm);
    var cloud = downstream().withClaim("scope", "read:all").sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all"))).thenReturn(cloud);
    var decoded = spy(JWT.decode(cloud));
    when(verifier.verifyCloud(cloud)).thenReturn(decoded);
    doThrow(new JWTDecodeException("private-actor-data")).when(decoded).getClaim("act");

    try (var logs = new LogCapture()) {
      assertStatus(source, 502);
      logs.assertLogged(Level.WARN, "OAuth Cloud token claim decoding failed (JWTDecodeException)");
    }
  }

  static Stream<Object> malformedActors() {
    var nullPreviousActor = new HashMap<String, Object>();
    nullPreviousActor.put("sub", CLIENT);
    nullPreviousActor.put("act", null);
    return Stream.of(true, "actor", List.of("actor"), Map.of(),
      Map.of("sub", BACKEND), Map.of("sub", BACKEND, "act", "client"),
      Map.of("sub", 123, "act", Map.of("sub", CLIENT)), Map.of("sub", BACKEND, "act", Map.of()),
      Map.of("sub", BACKEND, "act", Map.of("sub", 123)),
      Map.of("sub", BACKEND, "act", nullPreviousActor));
  }

  @ParameterizedTest
  @ValueSource(strings = {"act", "scope", "org_id"})
  void it_should_reject_null_downstream_authorization_claims(String claim) {
    var source = source().withClaim("scope", "mcp:tools:read").sign(algorithm);
    var cloud = downstream().withClaim("scope", "read:all").withNullClaim(claim).sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all"))).thenReturn(cloud);

    assertStatus(source, 502);
  }

  @Test
  void it_should_reject_extra_downstream_audiences() {
    var source = source().withClaim("scope", "mcp:tools:read").sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all")))
      .thenReturn(downstream().withAudience(CLOUD, RESOURCE).withClaim("scope", "read:all").sign(algorithm));

    assertStatus(source, 502);
  }

  @Test
  void it_should_reject_source_expiration_before_reusing_or_replacing_the_cached_token() {
    var source = source().withExpiresAt(clock.instant().plusSeconds(5)).withClaim("scope", "mcp:tools:read").sign(algorithm);
    var cloud = downstream().withExpiresAt(clock.instant().plusSeconds(10)).withClaim("scope", "read:all").sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all"))).thenReturn(cloud);
    service.authenticate(source);

    clock.advance(Duration.ofSeconds(5));
    assertStatus(source, 401);
    verify(exchange).exchange(source, Set.of("read:all"));
  }

  @Test
  void it_should_reject_an_expired_token_returned_by_a_replacement_exchange() {
    var source = source().withClaim("scope", "mcp:tools:read").sign(algorithm);
    var cloud = downstream().withExpiresAt(clock.instant().plusSeconds(5)).withClaim("scope", "read:all").sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all"))).thenReturn(cloud);
    service.authenticate(source);

    clock.advance(Duration.ofSeconds(5));
    assertStatus(source, 502);
    verify(exchange, times(2)).exchange(source, Set.of("read:all"));
  }

  @Test
  void it_should_reject_cached_authentication_when_source_verification_stops_succeeding() {
    var source = source().withClaim("scope", "mcp:tools:read").sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all"))).thenReturn(downstream().withClaim("scope", "read:all").sign(algorithm));
    service.authenticate(source);
    doThrow(OAuthAuthenticationException.unauthorized()).when(verifier).verifyMcp(source);

    assertStatus(source, 401);
    verify(exchange).exchange(source, Set.of("read:all"));
  }

  @Test
  void it_should_reject_cached_authentication_when_cloud_verification_stops_succeeding() {
    var source = source().withClaim("scope", "mcp:tools:read").sign(algorithm);
    var cloud = downstream().withClaim("scope", "read:all").sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all"))).thenReturn(cloud);
    service.authenticate(source);
    doThrow(OAuthAuthenticationException.invalidExchange()).when(verifier).verifyCloud(cloud);

    assertStatus(source, 502);
    verify(exchange).exchange(source, Set.of("read:all"));
    assertThat(authenticationEvents.getLast().failureStage()).isEqualTo("cloud_token_validation");
    assertThat(authenticationEvents.getLast().failureCategory()).isEqualTo("invalid_exchange");
  }

  @Test
  void it_should_keep_request_authentication_immutable_and_redacted() {
    var scopes = new HashSet<>(Set.of("read:all"));
    var authentication = new OAuthRequestAuthentication("private-cloud-token", scopes);
    scopes.clear();

    var grantedScopes = authentication.scopes();
    assertThat(grantedScopes).containsExactly("read:all");
    assertThatThrownBy(() -> grantedScopes.add("write:all")).isInstanceOf(UnsupportedOperationException.class);
    assertThat(authentication).hasToString("OAuthRequestAuthentication[cloudToken=redacted, scopes=[read:all]]");
  }

  @Test
  void it_should_report_success_for_every_caller_including_cache_hits_and_token_refresh() {
    var source = source().withClaim("scope", "mcp:tools:read").sign(algorithm);
    var refreshed = source().withClaim("scope", "mcp:tools:read").withJWTId("refresh").sign(algorithm);
    var cloud = downstream().withClaim("scope", "read:all").sign(algorithm);
    when(exchange.exchange(anyString(), any())).thenReturn(cloud);
    var key = service.authenticate(source).oauthClientKey();
    assertThat(service.authenticate(source).oauthClientKey()).isEqualTo(key);
    assertThat(service.authenticate(refreshed).oauthClientKey()).isEqualTo(key);

    assertThat(key).isEqualTo("57f0fa332af955229dcc63705e8bee9c0e699bc3a6e53f7bee6931743480a458");
    assertThat(authenticationEvents).hasSize(3).allSatisfy(event -> {
      assertThat(event.outcome()).isEqualTo("success");
      assertThat(event.failureStage()).isNull();
      assertThat(event.failureCategory()).isNull();
      assertThat(event.oauthClientKey()).isEqualTo(key);
      assertThat(event.timestamp()).isEqualTo(clock.millis());
      assertThat(event.durationMs()).isNotNegative();
      assertThat(event.toString()).doesNotContain(source, cloud, USER, CLIENT);
    });
    verify(exchange, times(2)).exchange(anyString(), any());
  }

  @Test
  void it_should_correlate_applications_across_replicas_and_users_but_separate_clients_and_issuers() {
    var keys = mock(RSAKeyProvider.class);
    when(keys.getPublicKeyById("test-key")).thenReturn((RSAPublicKey) KEY.getPublic());
    var otherIssuer = "https://another-issuer.example.com/";
    var otherConfiguration = new OAuthConfiguration(otherIssuer, RESOURCE, CLOUD, BACKEND, "test-client-secret", Duration.ofSeconds(60), 256);
    var otherVerifier = new Auth0TokenVerifier(otherConfiguration, keys, clock);
    var replica = new Auth0OAuthAuthenticationService(configuration, verifier, exchange, clock, authenticationEvents::add);
    var otherService = new Auth0OAuthAuthenticationService(otherConfiguration, otherVerifier, exchange, clock, authenticationEvents::add);
    // Verified tokens without delegation are rejected before exchange but still identify the application.
    var token = source().sign(algorithm);
    assertStatus(token, 403);
    assertThatThrownBy(() -> replica.authenticate(token)).isInstanceOf(OAuthAuthenticationException.class);
    assertStatus(source().withSubject("samlp|enterprise|another-user").sign(algorithm), 403);
    assertStatus(source().withClaim("azp", "other-client").sign(algorithm), 403);
    assertThatThrownBy(() -> otherService.authenticate(source().withIssuer(otherIssuer).sign(algorithm)))
      .isInstanceOf(OAuthAuthenticationException.class);

    var clientKeys = authenticationEvents.stream().map(OAuthAuthenticationResult::oauthClientKey).toList();
    assertThat(clientKeys.subList(0, 3)).containsOnly(clientKeys.getFirst());
    assertThat(clientKeys.get(3)).isNotNull().isNotEqualTo(clientKeys.getFirst());
    assertThat(clientKeys.get(4)).isNotNull().isNotEqualTo(clientKeys.getFirst()).isNotEqualTo(clientKeys.get(3));
  }

  @Test
  void it_should_not_change_validation_order_when_analytics_cannot_read_the_client_claim() {
    var source = source().withSubject(null).withClaim("scope", "mcp:tools:read").sign(algorithm);
    var decoded = spy(JWT.decode(source));
    doReturn(decoded).when(verifier).verifyMcp(source);
    doThrow(new JWTDecodeException("private-client-claim")).when(decoded).getClaim("azp");

    assertStatus(source, 401);

    assertThat(authenticationEvents).singleElement().satisfies(event -> {
      assertThat(event.failureCategory()).isEqualTo("invalid_token");
      assertThat(event.oauthClientKey()).isNull();
      assertThat(event.toString()).doesNotContain("private-client-claim");
    });
  }

  @Test
  void it_should_not_attribute_a_forged_authorized_party_before_signature_verification() {
    var otherAlgorithm = Algorithm.RSA256((RSAPublicKey) OTHER_KEY.getPublic(), (RSAPrivateKey) OTHER_KEY.getPrivate());
    var token = source().withClaim("azp", "forged-client").withClaim("scope", "mcp:tools:read").sign(otherAlgorithm);
    assertStatus(token, 401);
    assertThat(authenticationEvents).singleElement().satisfies(event -> {
      assertThat(event.oauthClientKey()).isNull();
      assertThat(event.failureStage()).isEqualTo("mcp_token_verification");
      assertThat(event.toString()).doesNotContain("forged-client", token);
    });
  }

  @ParameterizedTest
  @CsvSource({
    "invalid_token,mcp_token_verification,invalid_token,rejected,401,false",
    "signing_keys_unavailable,mcp_token_verification,unavailable,service_failure,503,false",
    "no_delegation,delegation_validation,insufficient_scope,rejected,403,true",
    "invalid_delegation,delegation_validation,invalid_token,rejected,401,true",
    "exchange_rejected,token_exchange,invalid_token,rejected,401,true",
    "exchange_unavailable,token_exchange,unavailable,service_failure,503,true",
    "exchange_invalid,token_exchange,invalid_exchange,service_failure,502,true",
    "cloud_invalid,cloud_token_validation,invalid_exchange,service_failure,502,true",
    "cloud_keys_unavailable,cloud_token_validation,unavailable,service_failure,503,true",
    "unexpected,mcp_token_verification,unexpected_error,service_failure,0,false"})
  void it_should_report_sanitized_failures_without_changing_authentication(String scenario, String stage, String category, String outcome,
    int status, boolean trustedClient) {
    var builder = source().withClaim("scope", "mcp:tools:read");
    if (scenario.equals("no_delegation")) {
      builder.withClaim("scope", "offline_access");
    } else if (scenario.equals("invalid_delegation")) {
      builder.withClaim("org_id", "private-org");
    }
    var source = scenario.equals("invalid_token") ? "not-a-token" : builder.sign(algorithm);
    var cloud = downstream().withClaim("scope", "read:all").sign(algorithm);
    when(exchange.exchange(anyString(), any())).thenReturn(cloud);
    switch (scenario) {
      case "signing_keys_unavailable" -> doThrow(OAuthAuthenticationException.unavailable()).when(verifier).verifyMcp(source);
      case "exchange_rejected" -> when(exchange.exchange(anyString(), any())).thenThrow(OAuthAuthenticationException.unauthorized());
      case "exchange_unavailable" -> when(exchange.exchange(anyString(), any())).thenThrow(OAuthAuthenticationException.unavailable());
      case "exchange_invalid" -> when(exchange.exchange(anyString(), any())).thenThrow(OAuthAuthenticationException.invalidExchange());
      case "cloud_invalid" -> doThrow(OAuthAuthenticationException.invalidExchange()).when(verifier).verifyCloud(cloud);
      case "cloud_keys_unavailable" -> doThrow(OAuthAuthenticationException.unavailable()).when(verifier).verifyCloud(cloud);
      case "unexpected" -> doThrow(new IllegalStateException("private-error-body")).when(verifier).verifyMcp(source);
      default -> { }
    }
    if (status == 0) {
      assertThatThrownBy(() -> service.authenticate(source)).isInstanceOf(IllegalStateException.class).hasMessage("private-error-body");
    } else {
      assertStatus(source, status);
    }
    assertThat(authenticationEvents).singleElement().satisfies(event -> {
      assertThat(event.outcome()).isEqualTo(outcome);
      assertThat(event.failureStage()).isEqualTo(stage);
      assertThat(event.failureCategory()).isEqualTo(category);
      assertThat(event.oauthClientKey() != null).isEqualTo(trustedClient);
      assertThat(event.toString()).doesNotContain("private-error-body", source, cloud, USER);
    });
  }

  @Test
  void it_should_not_change_authentication_when_the_observer_fails() {
    service = new Auth0OAuthAuthenticationService(configuration, verifier, exchange, clock, event -> { throw new IllegalStateException("stopped"); });
    var source = source().withClaim("scope", "mcp:tools:read").sign(algorithm);
    var cloud = downstream().withClaim("scope", "read:all").sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all"))).thenReturn(cloud);
    assertThat(service.authenticate(source).cloudToken()).isEqualTo(cloud);
    assertStatus("invalid-token", 401);
  }

  @Test
  void it_should_preserve_cloud_validation_stage_for_shared_failures() throws Exception {
    var source = source().withClaim("scope", "mcp:tools:read").sign(algorithm);
    var cloud = downstream().withClaim("scope", "read:all").sign(algorithm);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var failure = OAuthAuthenticationException.invalidExchange();
    when(exchange.exchange(source, Set.of("read:all"))).thenAnswer(invocation -> {
      entered.countDown();
      assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
      return cloud;
    });
    doThrow(failure).when(verifier).verifyCloud(cloud);
    try (var pool = Executors.newFixedThreadPool(4)) {
      var calls = IntStream.range(0, 4).mapToObj(i -> pool.submit(() -> assertStatus(source, 502))).toList();
      try {
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> verify(verifier, times(4)).verifyMcp(source));
      } finally {
        release.countDown();
      }
      for (var call : calls) {
        call.get(5, TimeUnit.SECONDS);
      }
    }
    assertThat(authenticationEvents).hasSize(4).allSatisfy(event -> {
      assertThat(event.failureStage()).isEqualTo("cloud_token_validation");
      assertThat(event.failureCategory()).isEqualTo("invalid_exchange");
    });
    assertThat(failure.stage()).isNull();
  }

  private OAuthConfiguration config(Duration ttl, int size) {
    return new OAuthConfiguration(ISSUER, RESOURCE, CLOUD, BACKEND, "test-client-secret", ttl, size);
  }

  private JWTCreator.Builder source() {
    var now = clock.instant();
    return JWT.create().withIssuer(ISSUER).withAudience(RESOURCE, "https://tenant.example.com/userinfo")
      .withSubject(USER).withClaim("azp", CLIENT).withIssuedAt(now.minusSeconds(1))
      .withExpiresAt(now.plusSeconds(299)).withKeyId("test-key");
  }

  private JWTCreator.Builder downstream() {
    var now = clock.instant();
    return JWT.create().withIssuer(ISSUER).withAudience(CLOUD).withSubject(USER).withClaim("azp", BACKEND)
      .withClaim("act", Map.of("sub", BACKEND, "act", Map.of("sub", CLIENT)))
      .withIssuedAt(now.minusSeconds(1)).withExpiresAt(now.plusSeconds(299)).withKeyId("test-key");
  }

  private void assertStatus(String token, int expected) {
    assertThatThrownBy(() -> service.authenticate(token)).isInstanceOfSatisfying(OAuthAuthenticationException.class,
      error -> assertThat(error.status()).isEqualTo(expected));
  }

}
