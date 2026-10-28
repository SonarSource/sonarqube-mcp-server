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
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.sonarsource.sonarqube.mcp.analytics.OAuthAuthenticationResult;
import org.sonarsource.sonarqube.mcp.http.HttpClient;
import org.sonarsource.sonarqube.mcp.log.McpLogger;

/**
 * Authenticates MCP tokens and exchanges them for validated Cloud credentials.
 * The cache and exchange limit are local to this instance; authentication needs no HTTP session state.
 */
public final class Auth0OAuthAuthenticationService {
  private static final McpLogger LOG = McpLogger.getInstance();
  private static final String CLOUD_TOKEN_VALIDATION = "cloud_token_validation";
  private final OAuthConfiguration configuration;
  private final Auth0TokenVerifier verifier;
  private final Auth0TokenExchange exchange;
  private final Clock clock;
  @Nullable
  private final Consumer<OAuthAuthenticationResult> authenticationObserver;
  private final Cache<String, CacheEntry> cache;
  private final Semaphore exchanges = new Semaphore(16);

  public Auth0OAuthAuthenticationService(OAuthConfiguration configuration, HttpClient client) {
    this(configuration, client, Clock.systemUTC(), null);
  }

  public Auth0OAuthAuthenticationService(OAuthConfiguration configuration, HttpClient client, Consumer<OAuthAuthenticationResult> observer) {
    this(configuration, client, Clock.systemUTC(), observer);
  }

  private Auth0OAuthAuthenticationService(OAuthConfiguration configuration, HttpClient client, Clock clock, @Nullable Consumer<OAuthAuthenticationResult> observer) {
    this(configuration, new Auth0TokenVerifier(configuration, new Auth0JwkProvider(configuration.issuer(), client, clock), clock),
      new Auth0TokenExchange(configuration), clock, observer);
  }

  Auth0OAuthAuthenticationService(OAuthConfiguration configuration, Auth0TokenVerifier verifier, Auth0TokenExchange exchange, Clock clock) {
    this(configuration, verifier, exchange, clock, null);
  }

  Auth0OAuthAuthenticationService(OAuthConfiguration configuration, Auth0TokenVerifier verifier, Auth0TokenExchange exchange, Clock clock,
    @Nullable Consumer<OAuthAuthenticationResult> observer) {
    this.authenticationObserver = observer;
    this.configuration = configuration;
    this.verifier = verifier;
    this.exchange = exchange;
    this.clock = clock;
    this.cache = CacheBuilder.newBuilder()
      .maximumSize(configuration.cacheSize())
      .expireAfterWrite(configuration.cacheTtl())
      .build();
  }

  /**
   * Verifies both tokens on every call, including when the Cloud credential comes from the cache.
   */
  public OAuthRequestAuthentication authenticate(String token) {
    var timestamp = clock.millis();
    var started = System.nanoTime();
    String clientKey = null;
    var stage = "mcp_token_verification";
    String failureCategory = "unexpected_error";
    String outcome = "service_failure";
    try {
      var source = verifier.verifyMcp(token);
      stage = "delegation_validation";
      clientKey = oauthClientKey(source);
      var scopes = validateSource(source);
      stage = "token_exchange";
      // The cache belongs to this service's configuration; the fingerprint includes every source claim.
      var authentication = cachedOrExchange(fingerprint(token), token, source, scopes).authentication();
      stage = CLOUD_TOKEN_VALIDATION;
      verifier.verifyCloud(authentication.cloudToken());
      outcome = "success";
      failureCategory = null;
      return authentication;
    } catch (OAuthAuthenticationException e) {
      if (e.stage() != null) {
        stage = e.stage();
      }
      outcome = e.status() == 401 || e.status() == 403 ? "rejected" : "service_failure";
      failureCategory = switch (e.status()) {
        case 401 -> "invalid_token";
        case 403 -> "insufficient_scope";
        case 503 -> "unavailable";
        default -> "invalid_exchange";
      };
      throw e;
    } finally {
      var observer = authenticationObserver;
      if (observer != null) {
        try {
          observer.accept(new OAuthAuthenticationResult(timestamp, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), outcome,
            failureCategory == null ? null : stage, failureCategory, clientKey));
        } catch (RuntimeException e) {
          LOG.debug("Unable to report OAuth authentication (" + e.getClass().getSimpleName() + ")");
        }
      }
    }
  }

  /**
   * Requires explicit delegation from a user token with no organization binding or prior actors.
   */
  private Set<String> validateSource(DecodedJWT source) {
    if (!validLifetime(source)) {
      throw sourceRejected("invalid_lifetime");
    }
    if (source.getAudience().contains(configuration.cloudAudience())) {
      throw sourceRejected("unexpected_cloud_audience");
    }
    if (blank(source.getSubject())) {
      throw sourceRejected("missing_subject");
    }
    if (blank(source.getClaim("azp").asString())) {
      throw sourceRejected("missing_authorized_party");
    }
    if (!source.getClaim("org_id").isMissing()) {
      throw sourceRejected("organization_binding");
    }
    if (!source.getClaim("act").isMissing()) {
      throw sourceRejected("unexpected_actor");
    }
    var scopes = cloudScopes(source);
    if (scopes.isEmpty()) {
      LOG.warn("OAuth source token rejected (missing_mcp_scope)");
      throw OAuthAuthenticationException.insufficientScope();
    }
    return scopes;
  }

  private static OAuthAuthenticationException sourceRejected(String reason) {
    LOG.warn("OAuth source token rejected (" + reason + ")");
    return OAuthAuthenticationException.unauthorized();
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
      var cause = e.getCause();
      if (cause instanceof OAuthAuthenticationException authenticationFailure) {
        throw authenticationFailure;
      }
      LOG.warn("OAuth exchange cache load failed (" + (cause == null ? e : cause).getClass().getSimpleName() + ")");
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
      var cloudToken = atStage("token_exchange", () -> exchange.exchange(token, scopes));
      var downstream = atStage(CLOUD_TOKEN_VALIDATION, () -> verifier.verifyCloud(cloudToken));
      var granted = atStage(CLOUD_TOKEN_VALIDATION, () -> validateExchange(source, downstream, scopes));
      var clientKey = oauthClientKey(source);
      var authentication = new OAuthRequestAuthentication(cloudToken, granted, clientKey);
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
      LOG.warn("OAuth Cloud token claim decoding failed (" + e.getClass().getSimpleName() + ")");
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
      // Only MCP scopes grant delegation; unrelated source scopes never broaden the exchange.
      return Arrays.stream(value.split(" ")).map(scope -> switch (scope) {
        case "mcp:tools:read" -> "read:all";
        case "mcp:tools:write" -> "write:all";
        default -> null;
      }).filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
    } catch (JWTDecodeException e) {
      LOG.warn("OAuth source scope decoding failed (" + e.getClass().getSimpleName() + ")");
      throw OAuthAuthenticationException.insufficientScope();
    }
  }

  private static boolean validLifetime(DecodedJWT token) {
    var issuedAt = token.getIssuedAtAsInstant();
    var expiresAt = token.getExpiresAtAsInstant();
    return issuedAt != null && expiresAt != null && expiresAt.isAfter(issuedAt);
  }

  private static boolean blank(@Nullable String value) {
    return value == null || value.isBlank();
  }

  private static Instant earliest(Instant... values) {
    return Arrays.stream(values).min(Instant::compareTo).orElseThrow();
  }

  @Nullable
  private String oauthClientKey(DecodedJWT source) {
    try {
      var clientId = source.getClaim("azp").asString();
      return blank(clientId) ? null : fingerprint(configuration.issuer() + "\0" + clientId);
    } catch (RuntimeException e) {
      // Telemetry must not change validation order or turn a rejected credential into a server error.
      LOG.debug("Unable to derive OAuth application analytics (" + e.getClass().getSimpleName() + ")");
      return null;
    }
  }

  private static <T> T atStage(String stage, Supplier<T> operation) {
    try {
      return operation.get();
    } catch (OAuthAuthenticationException e) {
      // Use an immutable copy: cache waiters may share the same failure.
      throw e.atStage(stage);
    }
  }

  private static String fingerprint(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  private record CacheEntry(OAuthRequestAuthentication authentication, Instant expiresAt) { }
}
