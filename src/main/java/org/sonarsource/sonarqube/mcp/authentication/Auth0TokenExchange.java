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
import com.auth0.net.client.DefaultHttpClient;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import okhttp3.OkHttpClient;

final class Auth0TokenExchange {
  private static final String ACCESS_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";
  private static final int MAXIMUM_RESPONSE_BYTES = 65_536;
  private final OAuthConfiguration configuration;
  private final AuthAPI client;

  Auth0TokenExchange(OAuthConfiguration configuration) {
    this(configuration, configuration.issuer());
  }

  Auth0TokenExchange(OAuthConfiguration configuration, String issuer) {
    this.configuration = configuration;
    var transport = new OkHttpClient.Builder()
      .connectTimeout(Duration.ofSeconds(5))
      .readTimeout(Duration.ofSeconds(10))
      .callTimeout(Duration.ofSeconds(10))
      .followRedirects(false)
      .followSslRedirects(false)
      .retryOnConnectionFailure(false)
      .addInterceptor(chain -> {
        var response = chain.proceed(chain.request());
        try {
          if (response.peekBody(MAXIMUM_RESPONSE_BYTES + 1L).contentLength() > MAXIMUM_RESPONSE_BYTES) {
            throw OAuthAuthenticationException.invalidExchange();
          }
          return response;
        } catch (IOException | RuntimeException e) {
          response.close();
          throw e;
        }
      }).build();
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
    try {
      var request = client.exchangeToken(token, ACCESS_TOKEN_TYPE)
        .setAudience(configuration.cloudAudience())
        .setScope(scopes.stream().sorted().collect(Collectors.joining(" ")));
      request.addParameter("requested_token_type", ACCESS_TOKEN_TYPE);
      var response = request.execute();
      var body = response.getBody();
      if (response.getStatusCode() != 200 || body == null || !"Bearer".equalsIgnoreCase(body.getTokenType())) {
        throw OAuthAuthenticationException.invalidExchange();
      }
      var exchanged = body.getAccessToken();
      if (exchanged == null || exchanged.isBlank() || exchanged.length() > 16_384) {
        throw OAuthAuthenticationException.invalidExchange();
      }
      return exchanged;
    } catch (APIException e) {
      throw exchangeFailure(e);
    } catch (Auth0Exception e) {
      if (e.getCause() instanceof InterruptedIOException interrupted && "interrupted".equals(interrupted.getMessage())) {
        Thread.currentThread().interrupt();
      }
      throw OAuthAuthenticationException.unavailable();
    } catch (OAuthAuthenticationException e) {
      throw e;
    } catch (RuntimeException e) {
      throw OAuthAuthenticationException.invalidExchange();
    }
  }

  private static OAuthAuthenticationException exchangeFailure(APIException failure) {
    if (failure.getStatusCode() >= 500 || failure.getStatusCode() == 429) {
      return OAuthAuthenticationException.unavailable();
    }
    if (failure.getStatusCode() == 400 && ("invalid_grant".equals(failure.getError()) || "invalid_token".equals(failure.getError()))) {
      return OAuthAuthenticationException.unauthorized();
    }
    return OAuthAuthenticationException.invalidExchange();
  }
}

