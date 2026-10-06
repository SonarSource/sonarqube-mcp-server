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

import com.auth0.jwt.interfaces.DecodedJWT;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

public final class Auth0OAuthAuthenticationService implements OAuthRequestAuthenticator {
  public static final Set<String> DELEGATION_SCOPES = Set.of("read:all", "write:all");
  private final OAuthConfiguration configuration;
  private final Auth0TokenVerifier verifier;
  private final Auth0TokenExchange exchange;
  private final Clock clock;
  private final Map<CacheKey, CacheEntry> cache = new LinkedHashMap<>(16, 0.75f, true);
  private final Semaphore exchanges = new Semaphore(16);

  public Auth0OAuthAuthenticationService(OAuthConfiguration configuration) {
    this(configuration, new Auth0TokenVerifier(configuration), new Auth0TokenExchange(configuration), Clock.systemUTC());
  }

  Auth0OAuthAuthenticationService(OAuthConfiguration configuration, Auth0TokenVerifier verifier, Auth0TokenExchange exchange, Clock clock) {
    this.configuration = configuration;
    this.verifier = verifier;
    this.exchange = exchange;
    this.clock = clock;
  }

  @Override
  public OAuthRequestAuthentication authenticate(String token) {
    var source = verifier.verifyMcp(token);
    var scopes = scopes(source);
    if (!validLifetime(source) || source.getAudience().contains(configuration.cloudAudience()) || blank(source.getSubject())
      || !source.getSubject().matches("oauth2\\|github\\|github\\|[0-9]+") || blank(source.getClaim("azp").asString())
      || !source.getClaim("org_id").isMissing() || !source.getClaim("act").isMissing()) {
      throw OAuthAuthenticationException.unauthorized();
    }
    if (scopes.isEmpty()) {
      throw OAuthAuthenticationException.insufficientScope();
    }
    var key = new CacheKey(fingerprint(token), source.getIssuer(), source.getSubject(), source.getClaim("azp").asString(),
      configuration.cloudAudience(), scopes);
    var cached = cached(key);
    if (cached != null) {
      verifier.verifyCloud(cached.authentication().cloudToken());
      return cached.authentication();
    }
    if (!exchanges.tryAcquire()) {
      throw OAuthAuthenticationException.unavailable();
    }
    try {
      var cloudToken = exchange.exchange(token, scopes);
      var downstream = verifier.verifyCloud(cloudToken);
      validateExchange(source, downstream, scopes);
      var authentication = new OAuthRequestAuthentication(cloudToken, scopes(downstream));
      var expiresAt = earliest(source.getExpiresAtAsInstant(), downstream.getExpiresAtAsInstant(), clock.instant().plus(configuration.cacheTtl()));
      if (expiresAt.isAfter(clock.instant())) {
        synchronized (cache) {
          cache.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(clock.instant()));
          while (cache.size() >= configuration.cacheSize()) {
            cache.remove(cache.keySet().iterator().next());
          }
          cache.put(key, new CacheEntry(authentication, expiresAt));
        }
      }
      return authentication;
    } finally {
      exchanges.release();
    }
  }

  private CacheEntry cached(CacheKey key) {
    synchronized (cache) {
      var result = cache.get(key);
      if (result != null && !result.expiresAt().isAfter(clock.instant())) {
        cache.remove(key);
        return null;
      }
      return result;
    }
  }

  private void validateExchange(DecodedJWT source, DecodedJWT downstream, Set<String> allowed) {
    var actor = downstream.getClaim("act").asMap();
    var previousActor = actor != null && actor.get("act") instanceof Map<?, ?> previous ? previous : null;
    var downstreamScope = downstream.getClaim("scope").asString();
    var granted = downstreamScope == null ? Set.<String>of() : Set.copyOf(Arrays.asList(downstreamScope.split(" ")));
    if (!validLifetime(downstream) || !Objects.equals(source.getSubject(), downstream.getSubject())
      || !Objects.equals(configuration.clientId(), downstream.getClaim("azp").asString())
      || !downstream.getAudience().equals(java.util.List.of(configuration.cloudAudience()))
      || !downstream.getClaim("org_id").isMissing() || granted.isEmpty() || !allowed.containsAll(granted)
      || actor == null || !configuration.clientId().equals(actor.get("sub"))
      || previousActor == null || !source.getClaim("azp").asString().equals(previousActor.get("sub")) || previousActor.containsKey("act")) {
      throw OAuthAuthenticationException.invalidExchange();
    }
  }

  private static Set<String> scopes(DecodedJWT token) {
    var value = token.getClaim("scope").asString();
    if (value == null) {
      return Set.of();
    }
    return Arrays.stream(value.split(" ")).filter(DELEGATION_SCOPES::contains).collect(Collectors.toUnmodifiableSet());
  }

  private static boolean validLifetime(DecodedJWT token) {
    var issuedAt = token.getIssuedAtAsInstant();
    var expiresAt = token.getExpiresAtAsInstant();
    return issuedAt != null && expiresAt != null && expiresAt.isAfter(issuedAt)
      && java.time.Duration.between(issuedAt, expiresAt).compareTo(java.time.Duration.ofSeconds(300)) <= 0;
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static Instant earliest(Instant... values) {
    return Arrays.stream(values).min(Instant::compareTo).orElseThrow();
  }

  private static String fingerprint(String token) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable");
    }
  }

  private record CacheKey(String authorizationFingerprint, String issuer, String subject, String client, String audience, Set<String> scopes) { }
  private record CacheEntry(OAuthRequestAuthentication authentication, Instant expiresAt) { }
}

