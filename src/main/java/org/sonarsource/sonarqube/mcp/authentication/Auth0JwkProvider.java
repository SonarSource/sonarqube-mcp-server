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
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import jakarta.annotation.Nullable;
import java.net.MalformedURLException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

final class Auth0JwkProvider implements JwkProvider {
  private static final Duration SNAPSHOT_TTL = Duration.ofHours(1);
  private static final Duration REFRESH_WINDOW = Duration.ofMinutes(1);
  private static final int MAX_REFRESH_ATTEMPTS = 10;

  private final UrlJwkProvider provider;
  private final Clock clock;
  private final ArrayDeque<Instant> refreshAttempts = new ArrayDeque<>();
  private volatile Refresh refresh = new Refresh(new Snapshot(Map.of(), Instant.MIN), null);

  Auth0JwkProvider(String issuer, Clock clock) {
    this(urlProvider(issuer), clock);
  }

  Auth0JwkProvider(UrlJwkProvider provider, Clock clock) {
    this.provider = provider;
    this.clock = clock;
  }

  @Override
  public Jwk get(String keyId) {
    var observed = refresh;
    var known = observed.snapshot().find(keyId, clock.instant());
    if (known != null) {
      return known;
    }
    synchronized (this) {
      // Requests waiting for the same refresh share its result, including failures.
      if (refresh == observed) {
        refresh = refresh(observed.snapshot());
      }
      var result = refresh;
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
      var fetched = provider.getAll();
      for (var key : fetched) {
        if ("RSA".equals(key.getType()) && (key.getAlgorithm() == null || "RS256".equals(key.getAlgorithm()))) {
          key.getPublicKey();
        }
      }
      var keys = fetched.stream().collect(Collectors.toUnmodifiableMap(Jwk::getId, Function.identity()));
      return new Refresh(new Snapshot(keys, clock.instant().plus(SNAPSHOT_TTL)), null);
    } catch (JwkException | RuntimeException e) {
      // Retain a still-fresh snapshot, but never extend its lifetime after a failed fetch.
      return new Refresh(previous, OAuthAuthenticationException.unavailable());
    }
  }

  private static UrlJwkProvider urlProvider(String issuer) {
    try {
      return new UrlJwkProvider(URI.create(issuer).resolve(".well-known/jwks.json").toURL(), 5000, 5000);
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("Invalid OAuth issuer URL");
    }
  }

  private record Snapshot(Map<String, Jwk> keys, Instant expiresAt) {
    @Nullable
    Jwk find(String keyId, Instant now) {
      return now.isBefore(expiresAt) ? keys.get(keyId) : null;
    }
  }

  private record Refresh(Snapshot snapshot, @Nullable OAuthAuthenticationException failure) {
  }
}
