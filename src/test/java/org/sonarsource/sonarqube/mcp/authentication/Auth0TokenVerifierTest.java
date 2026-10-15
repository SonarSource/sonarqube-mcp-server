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
import com.auth0.jwk.JwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.MutableClock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.CONFIGURATION;
import static org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.KEY;
import static org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.NOW;
import static org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.OTHER_KEY;
import static org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.keyValues;
import static org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.sign;
import static org.sonarsource.sonarqube.mcp.authentication.OAuthTestSupport.token;

class Auth0TokenVerifierTest {
  private final MutableClock clock = new MutableClock();
  private final JwkProvider keys = mock(JwkProvider.class);
  private Auth0TokenVerifier verifier;

  @BeforeEach
  void prepare() throws Exception {
    when(keys.get("key")).thenReturn(Jwk.fromValues(keyValues("key", KEY)));
    verifier = new Auth0TokenVerifier(CONFIGURATION, keys, clock);
  }

  @Test
  void it_should_verify_both_audiences_with_the_same_clock() {
    var mcp = sign(token(CONFIGURATION.resource()));
    var cloud = sign(token(CONFIGURATION.cloudAudience()));

    assertThat(verifier.verifyMcp(mcp).getSubject()).isEqualTo("oauth2|github|github|123");
    assertThat(verifier.verifyCloud(cloud).getAudience()).containsExactly(CONFIGURATION.cloudAudience());

    clock.advance(Duration.ofSeconds(299));
    assertFailure(() -> verifier.verifyMcp(mcp), 401);
    assertFailure(() -> verifier.verifyCloud(cloud), 502);
  }

  @Test
  void it_should_reject_tokens_with_the_other_audience() {
    assertFailure(() -> verifier.verifyMcp(sign(token(CONFIGURATION.cloudAudience()))), 401);
    assertFailure(() -> verifier.verifyCloud(sign(token(CONFIGURATION.resource()))), 502);
  }

  @Test
  void it_should_reject_invalid_signatures_and_untrusted_issuers() {
    var wrongSignature = token(CONFIGURATION.resource()).sign(Algorithm.RSA256((RSAPublicKey) OTHER_KEY.getPublic(), (RSAPrivateKey) OTHER_KEY.getPrivate()));

    assertFailure(() -> verifier.verifyMcp(wrongSignature), 401);
    assertFailure(() -> verifier.verifyMcp(sign(token(CONFIGURATION.resource()).withIssuer("https://untrusted.example/"))), 401);
  }

  @Test
  void it_should_reject_unexpected_algorithms_before_fetching_a_key() {
    assertFailure(() -> verifier.verifyMcp(token(CONFIGURATION.resource()).sign(Algorithm.none())), 401);
    assertFailure(() -> verifier.verifyMcp(token(CONFIGURATION.resource()).sign(Algorithm.HMAC256("secret"))), 401);
    verifyNoInteractions(keys);
  }

  @ParameterizedTest
  @ValueSource(strings = {"iss", "aud", "exp", "iat", "sub", "azp"})
  void it_should_reject_absent_null_or_wrongly_typed_required_claims(String claim) {
    var missing = token(CONFIGURATION.resource()).withClaim(claim, (String) null);
    var explicitNull = token(CONFIGURATION.resource()).withNullClaim(claim);
    var wrongType = token(CONFIGURATION.resource()).withClaim(claim, true);

    assertFailure(() -> verifier.verifyMcp(sign(missing)), 401);
    assertFailure(() -> verifier.verifyMcp(sign(explicitNull)), 401);
    assertFailure(() -> verifier.verifyMcp(sign(wrongType)), 401);
  }

  @Test
  void it_should_reject_expired_or_not_yet_valid_tokens() {
    assertFailure(() -> verifier.verifyMcp(sign(token(CONFIGURATION.resource()).withExpiresAt(NOW))), 401);
    assertFailure(() -> verifier.verifyMcp(sign(token(CONFIGURATION.resource()).withNotBefore(NOW.plusSeconds(61)))), 401);
    assertFailure(() -> verifier.verifyMcp(sign(token(CONFIGURATION.resource()).withIssuedAt(NOW.plusSeconds(61)))), 401);
    assertFailure(() -> verifier.verifyCloud(sign(token(CONFIGURATION.cloudAudience()).withExpiresAt(NOW))), 502);
    assertFailure(() -> verifier.verifyCloud(sign(token(CONFIGURATION.cloudAudience()).withNotBefore(NOW.plusSeconds(61)))), 502);
    assertFailure(() -> verifier.verifyCloud(sign(token(CONFIGURATION.cloudAudience()).withIssuedAt(NOW.plusSeconds(61)))), 502);
  }

  @ParameterizedTest
  @ValueSource(longs = {1, 60})
  void it_should_tolerate_clock_skew_for_issued_at_and_not_before(long skewSeconds) {
    var future = NOW.plusSeconds(skewSeconds);
    var mcp = sign(token(CONFIGURATION.resource()).withIssuedAt(future).withNotBefore(future));
    var cloud = sign(token(CONFIGURATION.cloudAudience()).withIssuedAt(future).withNotBefore(future));

    assertThat(verifier.verifyMcp(mcp).getIssuedAtAsInstant()).isEqualTo(future);
    assertThat(verifier.verifyCloud(cloud).getNotBeforeAsInstant()).isEqualTo(future);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "not-a-jwt"})
  void it_should_reject_missing_or_malformed_tokens(String value) {
    assertFailure(() -> verifier.verifyMcp(value), 401);
    assertFailure(() -> verifier.verifyCloud(value), 502);
    verifyNoInteractions(keys);
  }

  @ParameterizedTest
  @ValueSource(strings = {"exp", "iat", "nbf"})
  void it_should_sanitize_extreme_numeric_dates_before_verifying_the_signature(String claim) {
    var encoding = Base64.getUrlEncoder().withoutPadding();
    var header = encoding.encodeToString("{\"alg\":\"RS256\",\"kid\":\"key\"}".getBytes(StandardCharsets.UTF_8));
    for (var value : new long[] {Long.MIN_VALUE, Long.MAX_VALUE}) {
      var payload = encoding.encodeToString(("{\"" + claim + "\":" + value + "}").getBytes(StandardCharsets.UTF_8));
      var untrusted = header + "." + payload + ".AA";
      assertFailure(() -> verifier.verifyMcp(untrusted), 401);
      assertFailure(() -> verifier.verifyCloud(untrusted), 502);
    }
    verifyNoInteractions(keys);
  }

  @Test
  void it_should_reject_oversized_tokens_before_fetching_a_key() {
    assertFailure(() -> verifier.verifyMcp("x".repeat(16385)), 401);
    verifyNoInteractions(keys);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void it_should_reject_missing_or_blank_key_ids(String keyId) {
    assertFailure(() -> verifier.verifyMcp(sign(token(CONFIGURATION.resource()).withKeyId(keyId))), 401);
    verifyNoInteractions(keys);
  }

  @Test
  void it_should_reject_oversized_key_ids_before_fetching_a_key() {
    assertFailure(() -> verifier.verifyMcp(sign(token(CONFIGURATION.resource()).withKeyId("k".repeat(257)))), 401);
    verifyNoInteractions(keys);
  }

  @Test
  void it_should_reject_keys_for_a_different_algorithm() throws Exception {
    var values = new HashMap<>(keyValues("key", KEY));
    values.put("alg", "RS512");
    when(keys.get("key")).thenReturn(Jwk.fromValues(values));

    assertFailure(() -> verifier.verifyMcp(sign(token(CONFIGURATION.resource()))), 401);
  }

  @Test
  void it_should_allow_rsa_keys_without_an_algorithm_declaration() throws Exception {
    var values = new HashMap<>(keyValues("key", KEY));
    values.remove("alg");
    when(keys.get("key")).thenReturn(Jwk.fromValues(values));

    assertThat(verifier.verifyMcp(sign(token(CONFIGURATION.resource()))).getSubject()).isNotBlank();
  }

  @Test
  void it_should_reject_non_rsa_public_keys() throws Exception {
    var jwk = mock(Jwk.class);
    var publicKey = mock(ECPublicKey.class);
    when(jwk.getType()).thenReturn("RSA");
    when(jwk.getPublicKey()).thenReturn(publicKey);
    when(keys.get("key")).thenReturn(jwk);

    assertFailure(() -> verifier.verifyMcp(sign(token(CONFIGURATION.resource()))), 401);
  }

  @ParameterizedTest
  @ValueSource(strings = {"n", "e"})
  void it_should_sanitize_unchecked_key_material_failures(String missingField) throws Exception {
    var incomplete = new HashMap<>(keyValues("key", KEY));
    incomplete.remove(missingField);
    when(keys.get("key")).thenReturn(Jwk.fromValues(incomplete));

    assertFailure(() -> verifier.verifyMcp(sign(token(CONFIGURATION.resource()))), 503);
    assertFailure(() -> verifier.verifyCloud(sign(token(CONFIGURATION.cloudAudience()))), 503);
  }

  @Test
  void it_should_preserve_service_unavailable_for_both_token_types() throws Exception {
    when(keys.get("key")).thenThrow(OAuthAuthenticationException.unavailable());

    assertFailure(() -> verifier.verifyMcp(sign(token(CONFIGURATION.resource()))), 503);
    assertFailure(() -> verifier.verifyCloud(sign(token(CONFIGURATION.cloudAudience()))), 503);
  }

  @Test
  void it_should_not_include_token_contents_in_authentication_errors() {
    var credential = JWT.create().withSubject("private-user-secret").sign(Algorithm.none());

    assertThatThrownBy(() -> verifier.verifyMcp(credential)).isInstanceOf(OAuthAuthenticationException.class)
      .hasMessage("A valid MCP OAuth access token is required").hasNoCause();
  }

  private static void assertFailure(Runnable operation, int status) {
    assertThatThrownBy(operation::run).isInstanceOfSatisfying(OAuthAuthenticationException.class,
      error -> assertThat(error.status()).isEqualTo(status));
  }
}
