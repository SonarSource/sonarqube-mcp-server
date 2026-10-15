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

import com.auth0.jwk.Jwk;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.MutableClock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.KEY;
import static org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.keyValues;

class Auth0OAuthAuthenticationServiceTest {
  private static final String ISSUER = "https://auth-dev9.sc-dev9.io/";
  private static final String RESOURCE = "https://api.sc-dev9.io/mcp";
  private static final String CLOUD = "https://api.sc-dev9.io/";
  private static final String USER = "oauth2|github|github|123456789";
  private static final String CLIENT = "mcp-client";
  private static final String BACKEND = "mcp-backend";
  private Algorithm algorithm;
  private OAuthConfiguration configuration;
  private Auth0TokenVerifier verifier;
  private Auth0TokenExchange exchange;
  private Auth0OAuthAuthenticationService service;
  private MutableClock clock;

  @BeforeEach
  void setUp() {
    algorithm = Algorithm.RSA256((RSAPublicKey) KEY.getPublic(), (RSAPrivateKey) KEY.getPrivate());
    var jwk = Jwk.fromValues(keyValues("test-key", KEY));
    configuration = config(Duration.ofSeconds(30), 256);
    clock = new MutableClock();
    verifier = spy(new Auth0TokenVerifier(configuration, keyId -> jwk, clock));
    exchange = mock(Auth0TokenExchange.class);
    service = new Auth0OAuthAuthenticationService(configuration, verifier, exchange, clock);
  }

  @ParameterizedTest
  @MethodSource("scopeCases")
  void it_should_exchange_only_explicit_independent_delegation_scopes(String scope, Set<String> expected) {
    var source = source().withClaim("scope", "openid " + scope + " offline_access").sign(algorithm);
    var cloud = downstream().withClaim("scope", scope).sign(algorithm);
    when(exchange.exchange(source, expected)).thenReturn(cloud);

    var authenticated = service.authenticate(source);

    assertThat(authenticated.cloudToken()).isEqualTo(cloud).isNotEqualTo(source);
    assertThat(authenticated.scopes()).isEqualTo(expected);
    assertThat(authenticated.toString()).doesNotContain(cloud);
    verify(exchange).exchange(source, expected);
  }

  static Stream<Arguments> scopeCases() {
    return Stream.of(Arguments.of("read:all", Set.of("read:all")), Arguments.of("write:all", Set.of("write:all")),
      Arguments.of("read:all write:all", Set.of("read:all", "write:all")));
  }

  @Test
  void it_should_reject_source_without_delegation() {
    var source = source().withClaim("scope", "openid offline_access").sign(algorithm);
    assertStatus(source, 403);
    verify(exchange, times(0)).exchange(anyString(), any());
  }

  @ParameterizedTest
  @MethodSource("invalidSources")
  void it_should_reject_source_trust_or_organization_changes(String field, String value) {
    var source = source().withClaim("scope", "read:all");
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
      Arguments.of("sub", "backend@clients"), Arguments.of("sub", "samlp|connection|id"),
      Arguments.of("azp", ""), Arguments.of("org_id", "org_sso"));
  }

  @Test
  void it_should_reject_expired_or_missing_expiration_source() {
    assertStatus(source().withExpiresAt(clock.instant().minusSeconds(1)).withClaim("scope", "read:all").sign(algorithm), 401);
    assertStatus(JWT.create().withIssuer(ISSUER).withAudience(RESOURCE).withSubject(USER).withClaim("azp", CLIENT)
      .withIssuedAt(clock.instant()).withClaim("scope", "read:all").withKeyId("test-key").sign(algorithm), 401);
  }

  @Test
  void it_should_reject_overlong_lifetimes_and_wrong_actor_chain_depth() {
    assertStatus(source().withExpiresAt(clock.instant().plusSeconds(600)).withClaim("scope", "read:all").sign(algorithm), 401);
    var source = source().withClaim("scope", "read:all").sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all"))).thenReturn(downstream().withClaim("scope", "read:all")
      .withClaim("act", Map.of("sub", BACKEND, "act", Map.of("sub", CLIENT, "act", Map.of("sub", "unexpected")))).sign(algorithm));
    assertStatus(source, 502);
  }

  @ParameterizedTest
  @MethodSource("invalidExchanges")
  void it_should_reject_expanded_or_untrusted_downstream_token(String field, String value) {
    var source = source().withClaim("scope", "read:all").sign(algorithm);
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
      Arguments.of("scope", "read:all other:permission"), Arguments.of("scope", ""), Arguments.of("azp", "different-backend"),
      Arguments.of("actor", "different-backend"), Arguments.of("initiator", "different-client"), Arguments.of("sub", USER + "2"),
      Arguments.of("aud", RESOURCE), Arguments.of("iss", "https://other-tenant.auth0.com/"), Arguments.of("org_id", "org_sso"));
  }

  @Test
  void it_should_reuse_only_the_same_authorization_and_reverify_every_request() {
    var source = source().withClaim("scope", "read:all").sign(algorithm);
    var cloud = downstream().withClaim("scope", "read:all").sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all"))).thenReturn(cloud);
    service.authenticate(source);
    service.authenticate(source);
    verify(exchange).exchange(source, Set.of("read:all"));
    verify(verifier, times(2)).verifyMcp(source);
    verify(verifier, times(3)).verifyCloud(cloud);
    clock.advance(Duration.ofSeconds(30));
    service.authenticate(source);
    verify(exchange, times(2)).exchange(source, Set.of("read:all"));
  }

  @Test
  void it_should_isolate_two_authorizations_for_the_same_user() {
    var first = source().withClaim("scope", "read:all").withJWTId("first").sign(algorithm);
    var second = source().withClaim("scope", "read:all").withJWTId("second").sign(algorithm);
    when(exchange.exchange(anyString(), any())).thenReturn(downstream().withClaim("scope", "read:all").sign(algorithm));
    service.authenticate(first);
    service.authenticate(second);
    verify(exchange, times(2)).exchange(anyString(), any());
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
        for (var scope : List.of("read:all", "write:all", "read:all write:all")) {
          service.authenticate(source().withSubject(user).withClaim("azp", client).withClaim("scope", scope).sign(algorithm));
        }
      }
    }
    verify(exchange, times(12)).exchange(anyString(), any());
  }

  @Test
  void it_should_bound_cache_and_allow_disabling_it() {
    configuration = config(Duration.ofSeconds(30), 1);
    service = new Auth0OAuthAuthenticationService(configuration, verifier, exchange, clock);
    when(exchange.exchange(anyString(), any())).thenReturn(downstream().withClaim("scope", "read:all").sign(algorithm));
    var first = source().withClaim("scope", "read:all").withJWTId("first").sign(algorithm);
    var second = source().withClaim("scope", "read:all").withJWTId("second").sign(algorithm);
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
    var source = source().withExpiresAt(clock.instant().plusSeconds(10)).withClaim("scope", "read:all").sign(algorithm);
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
      .mapToObj(index -> source().withClaim("scope", "read:all").withJWTId("concurrent-" + index).sign(algorithm)).toList();
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
    var source = source().withClaim("scope", "read:all").sign(algorithm);
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
  }

  @Test
  void it_should_not_cache_failed_exchanges() {
    var source = source().withClaim("scope", "read:all").sign(algorithm);
    var cloud = downstream().withClaim("scope", "read:all").sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all"))).thenThrow(OAuthAuthenticationException.unavailable()).thenReturn(cloud);

    assertStatus(source, 503);
    assertThat(service.authenticate(source).cloudToken()).isEqualTo(cloud);
    assertThat(service.authenticate(source).cloudToken()).isEqualTo(cloud);

    verify(exchange, times(2)).exchange(source, Set.of("read:all"));
  }

  @Test
  void it_should_return_only_the_scopes_actually_granted_in_the_cloud_token() {
    var source = source().withClaim("scope", "read:all write:all").sign(algorithm);
    var cloud = downstream().withClaim("scope", "read:all").sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all", "write:all"))).thenReturn(cloud);

    assertThat(service.authenticate(source).scopes()).containsExactly("read:all");
    assertThat(service.authenticate(source).scopes()).containsExactly("read:all");
    verify(exchange).exchange(source, Set.of("read:all", "write:all"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"act", "org_id"})
  void it_should_reject_even_null_forbidden_source_claims(String claim) {
    assertStatus(source().withNullClaim(claim).withClaim("scope", "read:all").sign(algorithm), 401);
    verify(exchange, times(0)).exchange(anyString(), any());
  }

  @Test
  void it_should_reject_a_source_token_also_intended_for_cloud() {
    assertStatus(source().withAudience(RESOURCE, CLOUD).withClaim("scope", "read:all").sign(algorithm), 401);
    verify(exchange, times(0)).exchange(anyString(), any());
  }

  @ParameterizedTest
  @MethodSource("malformedActors")
  void it_should_sanitize_malformed_actor_chains(Object actor) {
    var source = source().withClaim("scope", "read:all").sign(algorithm);
    var cloud = downstream().withClaim("scope", "read:all").withPayload(Map.of("act", actor)).sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all"))).thenReturn(cloud);

    assertStatus(source, 502);
  }

  static Stream<Object> malformedActors() {
    var nullPreviousActor = new HashMap<String, Object>();
    nullPreviousActor.put("sub", CLIENT);
    nullPreviousActor.put("act", null);
    return Stream.of(true, "actor", List.of("actor"), Map.of(),
      Map.of("sub", BACKEND), Map.of("sub", BACKEND, "act", "client"),
      Map.of("sub", BACKEND, "act", Map.of("sub", 123)),
      Map.of("sub", BACKEND, "act", nullPreviousActor));
  }

  @ParameterizedTest
  @ValueSource(strings = {"act", "scope", "org_id"})
  void it_should_reject_null_downstream_authorization_claims(String claim) {
    var source = source().withClaim("scope", "read:all").sign(algorithm);
    var cloud = downstream().withClaim("scope", "read:all").withNullClaim(claim).sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all"))).thenReturn(cloud);

    assertStatus(source, 502);
  }

  @Test
  void it_should_reject_extra_downstream_audiences_and_overlong_lifetimes() {
    var source = source().withClaim("scope", "read:all").sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all")))
      .thenReturn(downstream().withAudience(CLOUD, RESOURCE).withClaim("scope", "read:all").sign(algorithm))
      .thenReturn(downstream().withExpiresAt(clock.instant().plusSeconds(300)).withClaim("scope", "read:all").sign(algorithm));

    assertStatus(source, 502);
    assertStatus(source, 502);
  }

  @Test
  void it_should_reject_source_expiration_before_reusing_or_replacing_the_cached_token() {
    var source = source().withExpiresAt(clock.instant().plusSeconds(5)).withClaim("scope", "read:all").sign(algorithm);
    var cloud = downstream().withExpiresAt(clock.instant().plusSeconds(10)).withClaim("scope", "read:all").sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all"))).thenReturn(cloud);
    service.authenticate(source);

    clock.advance(Duration.ofSeconds(5));
    assertStatus(source, 401);
    verify(exchange).exchange(source, Set.of("read:all"));
  }

  @Test
  void it_should_reject_an_expired_token_returned_by_a_replacement_exchange() {
    var source = source().withClaim("scope", "read:all").sign(algorithm);
    var cloud = downstream().withExpiresAt(clock.instant().plusSeconds(5)).withClaim("scope", "read:all").sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all"))).thenReturn(cloud);
    service.authenticate(source);

    clock.advance(Duration.ofSeconds(5));
    assertStatus(source, 502);
    verify(exchange, times(2)).exchange(source, Set.of("read:all"));
  }

  @Test
  void it_should_reject_cached_authentication_when_source_verification_stops_succeeding() {
    var source = source().withClaim("scope", "read:all").sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all"))).thenReturn(downstream().withClaim("scope", "read:all").sign(algorithm));
    service.authenticate(source);
    doThrow(OAuthAuthenticationException.unauthorized()).when(verifier).verifyMcp(source);

    assertStatus(source, 401);
    verify(exchange).exchange(source, Set.of("read:all"));
  }

  @Test
  void it_should_reject_cached_authentication_when_cloud_verification_stops_succeeding() {
    var source = source().withClaim("scope", "read:all").sign(algorithm);
    var cloud = downstream().withClaim("scope", "read:all").sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all"))).thenReturn(cloud);
    service.authenticate(source);
    doThrow(OAuthAuthenticationException.invalidExchange()).when(verifier).verifyCloud(cloud);

    assertStatus(source, 502);
    verify(exchange).exchange(source, Set.of("read:all"));
  }

  @Test
  void it_should_keep_request_authentication_immutable_and_redacted() {
    var scopes = new HashSet<>(Set.of("read:all"));
    var authentication = new OAuthRequestAuthentication("private-cloud-token", scopes);
    scopes.clear();

    assertThat(authentication.scopes()).containsExactly("read:all");
    assertThatThrownBy(() -> authentication.scopes().add("write:all")).isInstanceOf(UnsupportedOperationException.class);
    assertThat(authentication.toString()).doesNotContain("private-cloud-token");
  }

  private OAuthConfiguration config(Duration ttl, int size) {
    return new OAuthConfiguration(ISSUER, RESOURCE, CLOUD, BACKEND, "test-client-secret", ttl, size);
  }

  private JWTCreator.Builder source() {
    var now = clock.instant();
    return JWT.create().withIssuer(ISSUER).withAudience(RESOURCE, "https://sonarsource-dev9.eu.auth0.com/userinfo")
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
