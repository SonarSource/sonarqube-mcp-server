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

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

final class OAuthTestSupport {
  static final OAuthConfiguration CONFIGURATION = new OAuthConfiguration("https://tenant.auth0.com/", "https://api.example.com/mcp",
    "https://api.example.com/", "backend", "secret");
  static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
  static final KeyPair KEY = keyPair();
  static final KeyPair OTHER_KEY = keyPair();

  private OAuthTestSupport() {
  }

  static JWTCreator.Builder token(String audience) {
    return JWT.create().withKeyId("key").withIssuer(CONFIGURATION.issuer()).withAudience(audience)
      .withSubject("oauth2|github|github|123").withClaim("azp", "client").withIssuedAt(NOW.minusSeconds(1)).withExpiresAt(NOW.plusSeconds(299));
  }

  static String sign(JWTCreator.Builder token) {
    return token.sign(Algorithm.RSA256((RSAPublicKey) KEY.getPublic(), (RSAPrivateKey) KEY.getPrivate()));
  }

  static Map<String, Object> keyValues(String id, KeyPair key) {
    var publicKey = (RSAPublicKey) key.getPublic();
    return Map.of("kid", id, "kty", "RSA", "alg", "RS256", "use", "sig",
      "n", encoded(publicKey.getModulus()), "e", encoded(publicKey.getPublicExponent()));
  }

  private static String encoded(BigInteger value) {
    var bytes = value.toByteArray();
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes[0] == 0 ? Arrays.copyOfRange(bytes, 1, bytes.length) : bytes);
  }

  private static KeyPair keyPair() {
    try {
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  static final class MutableClock extends Clock {
    private final AtomicReference<Instant> now = new AtomicReference<>(NOW);

    void advance(Duration duration) {
      now.updateAndGet(instant -> instant.plus(duration));
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return Clock.fixed(instant(), zone);
    }

    @Override
    public Instant instant() {
      return now.get();
    }
  }
}
