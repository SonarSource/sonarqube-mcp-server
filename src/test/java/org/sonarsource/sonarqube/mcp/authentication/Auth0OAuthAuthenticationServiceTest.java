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
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Auth0OAuthAuthenticationServiceTest {
  private static final String ISSUER = "https://auth-dev9.sc-dev9.io/";
  private static final String RESOURCE = "https://api.sc-dev9.io/mcp";
  private static final String CLOUD = "https://api.sc-dev9.io/";
  private static final String USER = "oauth2|github|github|123456789";
  private static final String CLIENT = "mcp-client";
  private static final String BACKEND = "mcp-backend";
  private Algorithm algorithm;
  private Jwk jwk;
  private OAuthConfiguration configuration;
  private Auth0TokenVerifier verifier;
  private Auth0TokenExchange exchange;
  private Auth0OAuthAuthenticationService service;
  private MutableClock clock;

  @BeforeEach
  void setUp() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var key = generator.generateKeyPair();
    var publicKey = (RSAPublicKey) key.getPublic();
    algorithm = Algorithm.RSA256(publicKey, (RSAPrivateKey) key.getPrivate());
    jwk = mock(Jwk.class);
    when(jwk.getPublicKey()).thenReturn(publicKey);
    when(jwk.getAlgorithm()).thenReturn("RS256");
    configuration = config(Duration.ofSeconds(30), 256);
    verifier = new Auth0TokenVerifier(configuration, keyId -> jwk);
    exchange = mock(Auth0TokenExchange.class);
    clock = new MutableClock(Instant.now());
    service = new Auth0OAuthAuthenticationService(configuration, verifier, exchange, clock);
  }

  @ParameterizedTest
  @MethodSource("scopeCases")
  void should_exchange_only_explicit_independent_delegation_scopes(String scope, Set<String> expected) {
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
  void should_reject_source_without_delegation() {
    var source = source().withClaim("scope", "openid offline_access").sign(algorithm);
    assertStatus(source, 403);
    verify(exchange, times(0)).exchange(anyString(), any());
  }

  @ParameterizedTest
  @MethodSource("invalidSources")
  void should_reject_source_trust_or_organization_changes(String field, String value) {
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
  void should_reject_expired_or_missing_expiration_source() {
    assertStatus(source().withExpiresAt(Instant.now().minusSeconds(1)).withClaim("scope", "read:all").sign(algorithm), 401);
    assertStatus(JWT.create().withIssuer(ISSUER).withAudience(RESOURCE).withSubject(USER).withClaim("azp", CLIENT)
      .withIssuedAt(Instant.now()).withClaim("scope", "read:all").withKeyId("test-key").sign(algorithm), 401);
  }

  @Test
  void should_reject_signature_and_algorithm_confusion() throws Exception {
    var other = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    assertStatus(source().withClaim("scope", "read:all").sign(Algorithm.RSA256((RSAPublicKey) other.getPublic(), (RSAPrivateKey) other.getPrivate())), 401);
    assertStatus(source().withClaim("scope", "read:all").sign(Algorithm.HMAC256("not-an-rsa-key")), 401);
  }

  @Test
  void should_reject_unknown_signing_key_and_accept_rotation_keys_from_trusted_provider() {
    var unavailable = new Auth0TokenVerifier(configuration, keyId -> { throw new com.auth0.jwk.SigningKeyNotFoundException("unknown", null); });
    service = new Auth0OAuthAuthenticationService(configuration, unavailable, exchange, clock);
    assertStatus(source().withClaim("scope", "read:all").sign(algorithm), 401);
    var rotated = source().withKeyId("rotated-key").withClaim("scope", "read:all").sign(algorithm);
    service = new Auth0OAuthAuthenticationService(configuration, verifier, exchange, clock);
    when(exchange.exchange(rotated, Set.of("read:all"))).thenReturn(downstream().withClaim("scope", "read:all").sign(algorithm));
    assertThat(service.authenticate(rotated).scopes()).containsExactly("read:all");
  }

  @Test
  void should_reject_overlong_lifetimes_and_wrong_actor_chain_depth() {
    assertStatus(source().withExpiresAt(Instant.now().plusSeconds(600)).withClaim("scope", "read:all").sign(algorithm), 401);
    var source = source().withClaim("scope", "read:all").sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all"))).thenReturn(downstream().withClaim("scope", "read:all")
      .withClaim("act", Map.of("sub", BACKEND, "act", Map.of("sub", CLIENT, "act", Map.of("sub", "unexpected")))).sign(algorithm));
    assertStatus(source, 502);
  }

  @Test
  void should_report_jwks_outage_separately_from_invalid_credentials() {
    var unavailable = new Auth0TokenVerifier(configuration, keyId -> { throw new com.auth0.jwk.NetworkException("outage", null); });
    service = new Auth0OAuthAuthenticationService(configuration, unavailable, exchange, clock);
    assertStatus(source().withClaim("scope", "read:all").sign(algorithm), 503);
  }

  @ParameterizedTest
  @MethodSource("invalidExchanges")
  void should_reject_expanded_or_untrusted_downstream_token(String field, String value) {
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
  void should_reuse_only_the_same_authorization_and_reverify_every_request() {
    var source = source().withClaim("scope", "read:all").sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all"))).thenReturn(downstream().withClaim("scope", "read:all").sign(algorithm));
    service.authenticate(source);
    service.authenticate(source);
    verify(exchange).exchange(source, Set.of("read:all"));
    clock.advance(Duration.ofSeconds(30));
    service.authenticate(source);
    verify(exchange, times(2)).exchange(source, Set.of("read:all"));
  }

  @Test
  void should_isolate_two_authorizations_for_the_same_user() {
    var first = source().withClaim("scope", "read:all").withJWTId("first").sign(algorithm);
    var second = source().withClaim("scope", "read:all").withJWTId("second").sign(algorithm);
    when(exchange.exchange(anyString(), any())).thenReturn(downstream().withClaim("scope", "read:all").sign(algorithm));
    service.authenticate(first);
    service.authenticate(second);
    verify(exchange, times(2)).exchange(anyString(), any());
  }

  @Test
  void should_isolate_clients_users_and_scope_combinations() {
    when(exchange.exchange(anyString(), any())).thenAnswer(invocation -> {
      var original = JWT.decode(invocation.getArgument(0));
      return downstream().withSubject(original.getSubject()).withClaim("scope", String.join(" ", (Set<String>) invocation.getArgument(1)))
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
  void should_bound_cache_and_allow_disabling_it() {
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
  void should_expire_cache_at_the_earliest_token_expiration() {
    var source = source().withExpiresAt(clock.instant().plusSeconds(10)).withClaim("scope", "read:all").sign(algorithm);
    var cloud = downstream().withExpiresAt(clock.instant().plusSeconds(5)).withClaim("scope", "read:all").sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all"))).thenReturn(cloud);
    service.authenticate(source);
    clock.advance(Duration.ofSeconds(5));
    service.authenticate(source);
    verify(exchange, times(2)).exchange(source, Set.of("read:all"));
  }

  @Test
  void should_limit_concurrent_exchanges_and_release_capacity_after_failure() throws Exception {
    var started = new CountDownLatch(16);
    var released = new CountDownLatch(1);
    when(exchange.exchange(anyString(), any())).thenAnswer(invocation -> {
      started.countDown();
      released.await(5, TimeUnit.SECONDS);
      throw OAuthAuthenticationException.unavailable();
    });
    var sources = java.util.stream.IntStream.range(0, 17)
      .mapToObj(index -> source().withClaim("scope", "read:all").withJWTId("concurrent-" + index).sign(algorithm)).toList();
    var source = sources.get(16);
    var executor = java.util.concurrent.Executors.newFixedThreadPool(16);
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
  void should_share_a_single_exchange_between_concurrent_requests_for_the_same_authorization() throws Exception {
    var source = source().withClaim("scope", "read:all").sign(algorithm);
    var cloud = downstream().withClaim("scope", "read:all").sign(algorithm);
    var started = new CountDownLatch(1);
    var released = new CountDownLatch(1);
    when(exchange.exchange(source, Set.of("read:all"))).thenAnswer(invocation -> {
      started.countDown();
      released.await(5, TimeUnit.SECONDS);
      return cloud;
    });
    try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var requests = Stream.generate(() -> CompletableFuture.supplyAsync(() -> service.authenticate(source), executor)).limit(16).toList();
      try {
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
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
  void should_not_cache_failed_exchanges() {
    var source = source().withClaim("scope", "read:all").sign(algorithm);
    var cloud = downstream().withClaim("scope", "read:all").sign(algorithm);
    when(exchange.exchange(source, Set.of("read:all"))).thenThrow(OAuthAuthenticationException.unavailable()).thenReturn(cloud);

    assertStatus(source, 503);
    assertThat(service.authenticate(source).cloudToken()).isEqualTo(cloud);
    assertThat(service.authenticate(source).cloudToken()).isEqualTo(cloud);

    verify(exchange, times(2)).exchange(source, Set.of("read:all"));
  }

  @Test
  void should_not_include_secret_in_configuration_diagnostics() {
    assertThat(configuration.toString()).doesNotContain("test-client-secret");
  }

  private OAuthConfiguration config(Duration ttl, int size) {
    return new OAuthConfiguration(ISSUER, RESOURCE, CLOUD, BACKEND, "test-client-secret", ttl, size);
  }

  private JWTCreator.Builder source() {
    var now = Instant.now();
    return JWT.create().withIssuer(ISSUER).withAudience(RESOURCE, "https://sonarsource-dev9.eu.auth0.com/userinfo")
      .withSubject(USER).withClaim("azp", CLIENT).withIssuedAt(now.minusSeconds(1))
      .withExpiresAt(now.plusSeconds(299)).withKeyId("test-key");
  }

  private JWTCreator.Builder downstream() {
    var now = Instant.now();
    return JWT.create().withIssuer(ISSUER).withAudience(CLOUD).withSubject(USER).withClaim("azp", BACKEND)
      .withClaim("act", Map.of("sub", BACKEND, "act", Map.of("sub", CLIENT)))
      .withIssuedAt(now.minusSeconds(1)).withExpiresAt(now.plusSeconds(299)).withKeyId("test-key");
  }

  private void assertStatus(String token, int expected) {
    assertThatThrownBy(() -> service.authenticate(token)).isInstanceOfSatisfying(OAuthAuthenticationException.class,
      error -> assertThat(error.status()).isEqualTo(expected));
  }

  private static final class MutableClock extends Clock {
    private Instant now;
    private MutableClock(Instant now) { this.now = now; }
    void advance(Duration duration) { now = now.plus(duration); }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return now; }
  }
}

