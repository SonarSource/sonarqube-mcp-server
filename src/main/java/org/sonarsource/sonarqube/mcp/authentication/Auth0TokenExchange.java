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

import com.auth0.client.auth.AuthAPI;
import com.auth0.exception.APIException;
import com.auth0.exception.Auth0Exception;
import com.auth0.json.auth.TokenHolder;
import com.auth0.net.Response;
import com.auth0.net.client.DefaultHttpClient;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import okhttp3.OkHttpClient;
import org.sonarsource.sonarqube.mcp.log.McpLogger;

/**
 * Performs Auth0's native token exchange and sanitizes upstream failures.
 * The caller must verify the returned JWT and its delegated claims before using it.
 */
final class Auth0TokenExchange {
  private static final McpLogger LOG = McpLogger.getInstance();
  private static final String ACCESS_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";
  private final OAuthConfiguration configuration;
  private final AuthAPI client;

  Auth0TokenExchange(OAuthConfiguration configuration) {
    this(configuration, configuration.issuer().toString());
  }

  Auth0TokenExchange(OAuthConfiguration configuration, String issuer) {
    this.configuration = configuration;
    var transport = new OkHttpClient.Builder()
      .connectTimeout(Duration.ofSeconds(5))
      .callTimeout(Duration.ofSeconds(10))
      .followRedirects(false)
      .retryOnConnectionFailure(false)
      .build();
    var http = DefaultHttpClient.newBuilder().withClient(transport).withMaxRetries(0).build();
    this.client = AuthAPI.newBuilder(issuer, configuration.clientId(), configuration.clientSecret()).withHttpClient(http).build();
  }

  String exchange(String token, Set<String> scopes) {
    if (Thread.currentThread().isInterrupted()) {
      throw OAuthAuthenticationException.unavailable();
    }
    if (scopes.isEmpty()) {
      throw OAuthAuthenticationException.invalidExchange();
    }
    Response<TokenHolder> response;
    try {
      var request = client.exchangeToken(token, ACCESS_TOKEN_TYPE)
        .setAudience(configuration.cloudAudience())
        .setScope(scopes.stream().sorted().collect(Collectors.joining(" ")));
      request.addParameter("requested_token_type", ACCESS_TOKEN_TYPE);
      response = request.execute();
    } catch (APIException e) {
      throw exchangeFailure(e);
    } catch (Auth0Exception e) {
      if (e.getCause() instanceof InterruptedIOException interrupted && "interrupted".equals(interrupted.getMessage())) {
        Thread.currentThread().interrupt();
      }
      // SDK messages and response bodies can contain credentials; log only the failure type.
      var cause = e.getCause() == null ? e : e.getCause();
      LOG.warn("Auth0 token exchange unavailable (" + cause.getClass().getSimpleName() + ")");
      throw OAuthAuthenticationException.unavailable();
    } catch (RuntimeException e) {
      LOG.warn("Auth0 token exchange failed (" + e.getClass().getSimpleName() + ")");
      throw OAuthAuthenticationException.invalidExchange();
    }
    var body = response.getBody();
    if (response.getStatusCode() != 200 || body == null || !"Bearer".equalsIgnoreCase(body.getTokenType())) {
      throw OAuthAuthenticationException.invalidExchange();
    }
    var exchanged = body.getAccessToken();
    if (exchanged == null || exchanged.isBlank() || exchanged.length() > 16_384) {
      throw OAuthAuthenticationException.invalidExchange();
    }
    return exchanged;
  }

  private static OAuthAuthenticationException exchangeFailure(APIException failure) {
    if (failure.getStatusCode() >= 500 || failure.getStatusCode() == 429) {
      LOG.warn("Auth0 token exchange failed (HTTP " + failure.getStatusCode() + ")");
      return OAuthAuthenticationException.unavailable();
    }
    if (failure.getStatusCode() == 400 && ("invalid_grant".equals(failure.getError()) || "invalid_token".equals(failure.getError()))) {
      LOG.debug("Auth0 rejected the source token (HTTP {}, {})", failure.getStatusCode(), failure.getError());
      return OAuthAuthenticationException.unauthorized();
    }
    LOG.warn("Auth0 token exchange failed (HTTP " + failure.getStatusCode() + ")");
    return OAuthAuthenticationException.invalidExchange();
  }
}
