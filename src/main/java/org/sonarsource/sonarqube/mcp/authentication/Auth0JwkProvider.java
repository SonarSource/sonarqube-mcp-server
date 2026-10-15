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

import com.auth0.jwk.InvalidPublicKeyException;
import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import jakarta.annotation.Nullable;
import java.net.MalformedURLException;
import java.net.URI;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.sonarsource.sonarqube.mcp.http.HttpClient;

final class Auth0JwkProvider implements RSAKeyProvider {
  private static final Duration SNAPSHOT_TTL = Duration.ofHours(1);
  private static final Duration REFRESH_WINDOW = Duration.ofMinutes(1);
  private static final int MAX_REFRESH_ATTEMPTS = 10;

  private final UrlJwkProvider provider;
  private final Clock clock;
  private final ArrayDeque<Instant> refreshAttempts = new ArrayDeque<>();
  private final AtomicReference<Refresh> refresh = new AtomicReference<>(new Refresh(new Snapshot(Map.of(), Set.of(), Instant.MIN), null));

  Auth0JwkProvider(String issuer, HttpClient client, Clock clock) {
    this.provider = urlProvider(issuer, client);
    this.clock = clock;
  }

  @Override
  public RSAPublicKey getPublicKeyById(@Nullable String keyId) {
    if (keyId == null || keyId.isBlank() || keyId.length() > 256) {
      throw OAuthAuthenticationException.unauthorized();
    }
    var observed = refresh.get();
    var known = observed.snapshot().find(keyId, clock.instant());
    if (known != null) {
      return known;
    }
    synchronized (this) {
      // Requests waiting for the same refresh share its result, including failures.
      if (refresh.get() == observed) {
        refresh.set(refresh(observed.snapshot()));
      }
      var result = refresh.get();
      var key = result.snapshot().find(keyId, clock.instant());
      if (key != null) {
        return key;
      }
      if (result.failure() != null) {
        throw result.failure();
      }
      throw OAuthAuthenticationException.unauthorized();
    }
  }

  @Override
  @Nullable
  public RSAPrivateKey getPrivateKey() {
    return null;
  }

  @Override
  @Nullable
  public String getPrivateKeyId() {
    return null;
  }

  private Refresh refresh(Snapshot previous) {
    var now = clock.instant();
    while (!refreshAttempts.isEmpty() && !refreshAttempts.getFirst().isAfter(now.minus(REFRESH_WINDOW))) {
      refreshAttempts.removeFirst();
    }
    if (refreshAttempts.size() >= MAX_REFRESH_ATTEMPTS) {
      return new Refresh(previous, OAuthAuthenticationException.unavailable());
    }
    refreshAttempts.addLast(now);
    try {
      var fetched = provider.getAll().stream().collect(Collectors.toUnmodifiableMap(Jwk::getId, Function.identity()));
      var keys = new HashMap<String, RSAPublicKey>();
      for (var jwk : fetched.values()) {
        if ("RSA".equals(jwk.getType()) && (jwk.getAlgorithm() == null || "RS256".equals(jwk.getAlgorithm()))) {
          if (!(jwk.getPublicKey() instanceof RSAPublicKey key)) {
            throw new InvalidPublicKeyException("Invalid RSA signing key");
          }
          keys.put(jwk.getId(), key);
        }
      }
      return new Refresh(new Snapshot(Map.copyOf(keys), Set.copyOf(fetched.keySet()), clock.instant().plus(SNAPSHOT_TTL)), null);
    } catch (JwkException | RuntimeException e) {
      // Retain a still-fresh snapshot, but never extend its lifetime after a failed fetch.
      return new Refresh(previous, OAuthAuthenticationException.unavailable());
    }
  }

  private static UrlJwkProvider urlProvider(String issuer, HttpClient client) {
    try {
      return new UrlJwkProvider(URI.create(issuer).resolve(".well-known/jwks.json").toURL(), new JwksHttpClientAdapter(client));
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("Invalid OAuth issuer URL");
    }
  }

  private record Snapshot(Map<String, RSAPublicKey> keys, Set<String> knownKeyIds, Instant expiresAt) {
    @Nullable
    RSAPublicKey find(String keyId, Instant now) {
      if (!now.isBefore(expiresAt)) {
        return null;
      }
      var key = keys.get(keyId);
      if (key == null && knownKeyIds.contains(keyId)) {
        throw OAuthAuthenticationException.unauthorized();
      }
      return key;
    }
  }

  private record Refresh(Snapshot snapshot, @Nullable OAuthAuthenticationException failure) {
  }
}
