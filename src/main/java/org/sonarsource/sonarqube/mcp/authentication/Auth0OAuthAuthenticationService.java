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

import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;
import jakarta.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.sonarsource.sonarqube.mcp.http.HttpClient;

/**
 * Authenticates MCP tokens and exchanges them for validated Cloud credentials.
 * The cache and exchange limit are local to this instance; authentication needs no HTTP session state.
 */
public final class Auth0OAuthAuthenticationService implements Function<String, OAuthRequestAuthentication> {
  private final OAuthConfiguration configuration;
  private final Auth0TokenVerifier verifier;
  private final Auth0TokenExchange exchange;
  private final Clock clock;
  private final Cache<String, CacheEntry> cache;
  private final Semaphore exchanges = new Semaphore(16);

  public Auth0OAuthAuthenticationService(OAuthConfiguration configuration, HttpClient client) {
    this(configuration, client, Clock.systemUTC());
  }

  private Auth0OAuthAuthenticationService(OAuthConfiguration configuration, HttpClient client, Clock clock) {
    this(configuration, new Auth0TokenVerifier(configuration, new Auth0JwkProvider(configuration.issuer(), client, clock), clock),
      new Auth0TokenExchange(configuration), clock);
  }

  Auth0OAuthAuthenticationService(OAuthConfiguration configuration, Auth0TokenVerifier verifier, Auth0TokenExchange exchange, Clock clock) {
    this.configuration = configuration;
    this.verifier = verifier;
    this.exchange = exchange;
    this.clock = clock;
    this.cache = CacheBuilder.newBuilder()
      .maximumSize(configuration.cacheSize())
      .expireAfterWrite(configuration.cacheTtl())
      .build();
  }

  @Override
  public OAuthRequestAuthentication apply(String token) {
    return authenticate(token);
  }

  /**
   * Verifies both tokens on every call, including when the Cloud credential comes from the cache.
   */
  public OAuthRequestAuthentication authenticate(String token) {
    var source = verifier.verifyMcp(token);
    var scopes = validateSource(source);
    // The cache belongs to this service's configuration; the fingerprint includes every source claim.
    var authentication = cachedOrExchange(fingerprint(token), token, source, scopes).authentication();
    verifier.verifyCloud(authentication.cloudToken());
    return authentication;
  }

  /**
   * Requires explicit delegation from a short-lived GitHub user token with no organization binding or prior actors.
   */
  private Set<String> validateSource(DecodedJWT source) {
    var scopes = cloudScopes(source);
    if (!validLifetime(source) || source.getAudience().contains(configuration.cloudAudience()) || blank(source.getSubject())
      || !source.getSubject().matches("oauth2\\|github\\|github\\|\\d+") || blank(source.getClaim("azp").asString())
      || !source.getClaim("org_id").isMissing() || !source.getClaim("act").isMissing()) {
      throw OAuthAuthenticationException.unauthorized();
    }
    if (scopes.isEmpty()) {
      throw OAuthAuthenticationException.insufficientScope();
    }
    return scopes;
  }

  /**
   * Shares one in-flight exchange per token fingerprint. Token expiration can invalidate an entry before the cache TTL.
   */
  private CacheEntry cachedOrExchange(String key, String token, DecodedJWT source, Set<String> scopes) {
    var cached = cache.getIfPresent(key);
    if (cached != null && !cached.expiresAt().isAfter(clock.instant())) {
      cache.asMap().remove(key, cached);
    }
    try {
      var entry = cache.get(key, () -> exchange(token, source, scopes));
      if (!entry.expiresAt().isAfter(clock.instant())) {
        cache.asMap().remove(key, entry);
      }
      return entry;
    } catch (ExecutionException | UncheckedExecutionException e) {
      if (e.getCause() instanceof OAuthAuthenticationException authenticationFailure) {
        throw authenticationFailure;
      }
      throw OAuthAuthenticationException.unavailable();
    }
  }

  /**
   * Limits Auth0 load per instance and caches validated credentials until either token expires or the TTL elapses.
   */
  private CacheEntry exchange(String token, DecodedJWT source, Set<String> scopes) {
    if (!exchanges.tryAcquire()) {
      throw OAuthAuthenticationException.unavailable();
    }
    try {
      var cloudToken = exchange.exchange(token, scopes);
      var downstream = verifier.verifyCloud(cloudToken);
      var granted = validateExchange(source, downstream, scopes);
      var authentication = new OAuthRequestAuthentication(cloudToken, granted);
      var expiresAt = earliest(source.getExpiresAtAsInstant(), downstream.getExpiresAtAsInstant(), clock.instant().plus(configuration.cacheTtl()));
      return new CacheEntry(authentication, expiresAt);
    } finally {
      exchanges.release();
    }
  }

  /**
   * Checks the verified Cloud token against the original user and delegation, returning only the scopes actually granted.
   */
  private Set<String> validateExchange(DecodedJWT source, DecodedJWT downstream, Set<String> allowed) {
    try {
      var downstreamScope = downstream.getClaim("scope").asString();
      var granted = downstreamScope == null ? Set.<String>of() : Set.copyOf(Arrays.asList(downstreamScope.split(" ")));
      if (!validLifetime(downstream) || !Objects.equals(source.getSubject(), downstream.getSubject())
        || !Objects.equals(configuration.clientId(), downstream.getClaim("azp").asString())
        || !downstream.getAudience().equals(List.of(configuration.cloudAudience()))
        || !downstream.getClaim("org_id").isMissing() || granted.isEmpty() || !allowed.containsAll(granted)
        || !hasExpectedActors(downstream, source.getClaim("azp").asString())) {
        throw OAuthAuthenticationException.invalidExchange();
      }
      return granted;
    } catch (JWTDecodeException e) {
      throw OAuthAuthenticationException.invalidExchange();
    }
  }

  /**
   * Requires exactly two actors: this backend, then the client that obtained the MCP token.
   */
  private boolean hasExpectedActors(DecodedJWT downstream, String initiatingClient) {
    var actor = downstream.getClaim("act").asMap();
    return actor != null && configuration.clientId().equals(actor.get("sub"))
      && actor.get("act") instanceof Map<?, ?> previous
      && initiatingClient.equals(previous.get("sub")) && !previous.containsKey("act");
  }

  private static Set<String> cloudScopes(DecodedJWT source) {
    try {
      var value = source.getClaim("scope").asString();
      if (blank(value)) {
        return Set.of();
      }
      return Arrays.stream(value.split(" ")).map(scope -> switch (scope) {
        case "mcp:tools:read" -> "read:all";
        case "mcp:tools:write" -> "write:all";
        default -> null;
      }).filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
    } catch (JWTDecodeException e) {
      throw OAuthAuthenticationException.insufficientScope();
    }
  }

  private static boolean validLifetime(DecodedJWT token) {
    var issuedAt = token.getIssuedAtAsInstant();
    var expiresAt = token.getExpiresAtAsInstant();
    return issuedAt != null && expiresAt != null && expiresAt.isAfter(issuedAt)
      && Duration.between(issuedAt, expiresAt).compareTo(Duration.ofSeconds(300)) <= 0;
  }

  private static boolean blank(@Nullable String value) {
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

  private record CacheEntry(OAuthRequestAuthentication authentication, Instant expiresAt) { }
}
