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
import com.auth0.jwk.SigningKeyNotFoundException;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.algorithms.Algorithm;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.google.gson.Gson;
import java.net.URI;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.MutableClock;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.CONFIGURATION;
import static org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.KEY;
import static org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.OTHER_KEY;
import static org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.keyValues;
import static org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.sign;
import static org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.token;

class Auth0JwkProviderTest {
  private static final String JWKS_PATH = "/.well-known/jwks.json";
  private final MutableClock clock = new MutableClock();
  private WireMockServer server;
  private Auth0JwkProvider provider;

  @BeforeEach
  void prepare() throws Exception {
    server = new WireMockServer(wireMockConfig().dynamicPort());
    server.start();
    provider = new Auth0JwkProvider(new UrlJwkProvider(URI.create(server.baseUrl() + JWKS_PATH).toURL(), 5000, 5000), clock);
    serveKeys(List.of(keyValues("key", KEY)));
  }

  @AfterEach
  void stop() {
    server.stop();
  }

  @Test
  void it_should_verify_a_known_key_after_unknown_key_requests_exhaust_the_refresh_budget() {
    var verifier = new Auth0TokenVerifier(CONFIGURATION, provider, clock);
    for (var i = 0; i < 10; i++) {
      var unknown = sign(token(CONFIGURATION.resource()).withKeyId("unknown-" + i));
      assertFailure(() -> verifier.verifyMcp(unknown), 401);
    }
    assertFailure(() -> provider.get("another-unknown"), 503);

    assertThat(verifier.verifyMcp(sign(token(CONFIGURATION.resource()))).getSubject()).isNotBlank();
    server.verify(10, getRequestedFor(urlEqualTo(JWKS_PATH)));
  }

  @Test
  void it_should_fetch_the_complete_key_set_once() throws Exception {
    serveKeys(List.of(keyValues("key", KEY), keyValues("other", OTHER_KEY)));

    assertThat(provider.get("key").getPublicKey()).isEqualTo(KEY.getPublic());
    assertThat(provider.get("other").getPublicKey()).isEqualTo(OTHER_KEY.getPublic());
    server.verify(1, getRequestedFor(urlEqualTo(JWKS_PATH)));
  }

  @Test
  void it_should_replace_the_snapshot_when_keys_rotate_and_remove_old_keys() {
    assertThat(provider.get("key").getId()).isEqualTo("key");
    serveKeys(List.of(keyValues("other", OTHER_KEY)));
    var verifier = new Auth0TokenVerifier(CONFIGURATION, provider, clock);
    var rotatedToken = token(CONFIGURATION.resource()).withKeyId("other")
      .sign(Algorithm.RSA256((RSAPublicKey) OTHER_KEY.getPublic(), (RSAPrivateKey) OTHER_KEY.getPrivate()));

    assertThat(verifier.verifyMcp(rotatedToken).getKeyId()).isEqualTo("other");
    assertFailure(() -> verifier.verifyMcp(sign(token(CONFIGURATION.resource()))), 401);
    server.verify(3, getRequestedFor(urlEqualTo(JWKS_PATH)));
  }

  @Test
  void it_should_refresh_known_keys_when_the_snapshot_expires() {
    provider.get("key");
    clock.advance(Duration.ofHours(1).minusNanos(1));
    provider.get("key");
    server.verify(1, getRequestedFor(urlEqualTo(JWKS_PATH)));

    clock.advance(Duration.ofNanos(1));
    provider.get("key");
    server.verify(2, getRequestedFor(urlEqualTo(JWKS_PATH)));
  }

  @Test
  void it_should_preserve_fresh_keys_but_not_extend_them_after_a_refresh_outage() {
    provider.get("key");
    clock.advance(Duration.ofMinutes(59));
    server.stubFor(get(JWKS_PATH).willReturn(aResponse().withStatus(503).withBody("private upstream details")));

    assertFailure(() -> provider.get("unknown"), 503);
    assertThat(provider.get("key").getId()).isEqualTo("key");
    clock.advance(Duration.ofMinutes(1));
    assertFailure(() -> provider.get("key"), 503);

    serveKeys(List.of(keyValues("key", KEY)));
    assertThat(provider.get("key").getId()).isEqualTo("key");
    server.verify(4, getRequestedFor(urlEqualTo(JWKS_PATH)));
  }

  @Test
  void it_should_count_failed_requests_against_the_rolling_refresh_limit_and_recover() {
    server.stubFor(get(JWKS_PATH).willReturn(aResponse().withStatus(500)));
    for (var i = 0; i < 11; i++) {
      assertFailure(() -> provider.get("key"), 503);
    }
    server.verify(10, getRequestedFor(urlEqualTo(JWKS_PATH)));
    serveKeys(List.of(keyValues("key", KEY)));
    clock.advance(Duration.ofMinutes(1).minusNanos(1));
    assertFailure(() -> provider.get("key"), 503);

    clock.advance(Duration.ofNanos(1));
    assertThat(provider.get("key").getId()).isEqualTo("key");
    server.verify(11, getRequestedFor(urlEqualTo(JWKS_PATH)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing-id", "duplicate-id", "not-json", "wrong-keys-type"})
  void it_should_sanitize_malformed_key_sets_and_preserve_the_previous_snapshot(String malformed) {
    provider.get("key");
    clock.advance(Duration.ofMinutes(59));
    var missingId = new HashMap<>(keyValues("key", KEY));
    missingId.remove("kid");
    switch (malformed) {
      case "missing-id" -> serveKeys(List.of(missingId));
      case "duplicate-id" -> serveKeys(List.of(keyValues("key", KEY), keyValues("key", OTHER_KEY)));
      case "not-json" -> server.stubFor(get(JWKS_PATH).willReturn(aResponse().withBody("private malformed response")));
      case "wrong-keys-type" -> server.stubFor(get(JWKS_PATH).willReturn(aResponse().withBody("{\"keys\":\"private malformed value\"}")));
      default -> throw new IllegalArgumentException(malformed);
    }

    assertFailure(() -> provider.get("unknown"), 503);
    assertThat(provider.get("key").getId()).isEqualTo("key");
    clock.advance(Duration.ofMinutes(1));
    assertThatThrownBy(() -> provider.get("key")).isInstanceOf(OAuthAuthenticationException.class)
      .hasMessage("OAuth authentication service is temporarily unavailable").hasNoCause();
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing-n", "missing-e", "wrong-type-n", "wrong-type-e", "invalid-base64-n", "invalid-base64-e"})
  void it_should_reject_malformed_rsa_material_before_replacing_the_snapshot(String malformed) throws Exception {
    provider.get("key");
    clock.advance(Duration.ofMinutes(59));
    var broken = new HashMap<>(keyValues("broken", OTHER_KEY));
    var field = malformed.substring(malformed.lastIndexOf('-') + 1);
    if (malformed.startsWith("missing")) {
      broken.remove(field);
    } else if (malformed.startsWith("wrong-type")) {
      broken.put(field, List.of("invalid"));
    } else {
      broken.put(field, "invalid base64!");
    }
    serveKeys(List.of(keyValues("replacement", OTHER_KEY), broken));

    assertFailure(() -> provider.get("replacement"), 503);
    assertThat(provider.get("key").getPublicKey()).isEqualTo(KEY.getPublic());
    clock.advance(Duration.ofMinutes(1));
    assertFailure(() -> provider.get("key"), 503);

    serveKeys(List.of(keyValues("replacement", OTHER_KEY)));
    assertThat(provider.get("replacement").getPublicKey()).isEqualTo(OTHER_KEY.getPublic());
  }

  @Test
  void it_should_allow_mixed_key_types_without_parsing_material_for_unsupported_algorithms() {
    serveKeys(List.of(keyValues("key", KEY), Map.of("kid", "ec", "kty", "EC", "alg", "ES256"),
      Map.of("kid", "rs512", "kty", "RSA", "alg", "RS512")));
    var verifier = new Auth0TokenVerifier(CONFIGURATION, provider, clock);

    assertThat(verifier.verifyMcp(sign(token(CONFIGURATION.resource()))).getSubject()).isNotBlank();
    assertFailure(() -> verifier.verifyMcp(sign(token(CONFIGURATION.resource()).withKeyId("ec"))), 401);
    assertFailure(() -> verifier.verifyMcp(sign(token(CONFIGURATION.resource()).withKeyId("rs512"))), 401);
    server.verify(1, getRequestedFor(urlEqualTo(JWKS_PATH)));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void it_should_share_an_in_progress_refresh_including_failures(boolean fail) throws Exception {
    var fetched = mock(UrlJwkProvider.class);
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    when(fetched.getAll()).thenAnswer(call -> {
      started.countDown();
      assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
      if (fail) {
        throw new SigningKeyNotFoundException("private upstream failure", null);
      }
      return List.of(Jwk.fromValues(keyValues("key", KEY)));
    });
    var concurrent = new Auth0JwkProvider(fetched, clock);
    var workers = new ArrayList<Thread>();
    try (var executor = Executors.newFixedThreadPool(8, runnable -> {
      var thread = new Thread(runnable);
      workers.add(thread);
      return thread;
    })) {
      Runnable lookup = () -> {
        if (fail) {
          assertFailure(() -> concurrent.get("key"), 503);
        } else {
          assertThat(concurrent.get("key").getId()).isEqualTo("key");
        }
      };
      var futures = new ArrayList<Future<?>>();
      futures.add(executor.submit(lookup));
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
      for (var i = 1; i < 8; i++) {
        futures.add(executor.submit(lookup));
      }
      try {
        await().atMost(Duration.ofSeconds(3)).until(() -> workers.stream().skip(1).allMatch(thread -> thread.getState() == Thread.State.BLOCKED));
      } finally {
        release.countDown();
      }
      for (var future : futures) {
        future.get(5, TimeUnit.SECONDS);
      }
    }
    verify(fetched, times(1)).getAll();
  }

  @Test
  void it_should_not_block_known_keys_behind_an_unknown_key_refresh() throws Exception {
    var fetched = mock(UrlJwkProvider.class);
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var keys = List.of(Jwk.fromValues(keyValues("key", KEY)));
    when(fetched.getAll()).thenReturn(keys).thenAnswer(call -> {
      started.countDown();
      assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
      return keys;
    });
    var concurrent = new Auth0JwkProvider(fetched, clock);
    concurrent.get("key");
    try (var executor = Executors.newFixedThreadPool(2)) {
      var unknown = executor.submit(() -> assertFailure(() -> concurrent.get("unknown"), 401));
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
      try {
        assertThat(executor.submit(() -> concurrent.get("key")).get(1, TimeUnit.SECONDS).getId()).isEqualTo("key");
      } finally {
        release.countDown();
      }
      unknown.get(5, TimeUnit.SECONDS);
    }
  }

  private void serveKeys(List<Map<String, Object>> keys) {
    server.stubFor(get(JWKS_PATH).willReturn(aResponse().withHeader("Content-Type", "application/json")
      .withBody(new Gson().toJson(Map.of("keys", keys)))));
  }

  private static void assertFailure(Runnable operation, int status) {
    assertThatThrownBy(operation::run).isInstanceOfSatisfying(OAuthAuthenticationException.class,
      error -> assertThat(error.status()).isEqualTo(status));
  }
}
