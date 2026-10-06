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

import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import jakarta.annotation.Nullable;
import java.net.MalformedURLException;
import java.net.URI;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.concurrent.TimeUnit;

final class Auth0TokenVerifier {
  private final JWTVerifier mcpVerifier;
  private final JWTVerifier cloudVerifier;

  Auth0TokenVerifier(OAuthConfiguration configuration) {
    this(configuration, jwks(configuration));
  }

  Auth0TokenVerifier(OAuthConfiguration configuration, JwkProvider provider) {
    var algorithm = Algorithm.RSA256(new RSAKeyProvider() {
      @Override
      public RSAPublicKey getPublicKeyById(@Nullable String keyId) {
        if (keyId == null || keyId.isBlank() || keyId.length() > 256) {
          throw OAuthAuthenticationException.unauthorized();
        }
        return signingKey(provider, keyId);
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
    });
    this.mcpVerifier = JWT.require(algorithm).withIssuer(configuration.issuer()).withAudience(configuration.resource())
      .withClaimPresence("exp").withClaimPresence("iat").withClaimPresence("sub").withClaimPresence("azp").build();
    this.cloudVerifier = JWT.require(algorithm).withIssuer(configuration.issuer()).withAudience(configuration.cloudAudience())
      .withClaimPresence("exp").withClaimPresence("iat").withClaimPresence("sub").withClaimPresence("azp").build();
  }

  DecodedJWT verifyMcp(@Nullable String token) {
    return verify(mcpVerifier, token);
  }

  DecodedJWT verifyCloud(@Nullable String token) {
    try {
      return verify(cloudVerifier, token);
    } catch (OAuthAuthenticationException e) {
      if (e.status() == 503) {
        throw e;
      }
      throw OAuthAuthenticationException.invalidExchange();
    }
  }

  private static DecodedJWT verify(JWTVerifier verifier, @Nullable String token) {
    if (token == null || token.isBlank() || token.length() > 16384) {
      throw OAuthAuthenticationException.unauthorized();
    }
    try {
      return verifier.verify(token);
    } catch (JWTVerificationException e) {
      throw OAuthAuthenticationException.unauthorized();
    }
  }

  private static RSAPublicKey signingKey(JwkProvider provider, String keyId) {
    try {
      var jwk = provider.get(keyId);
      if (jwk.getAlgorithm() != null && !"RS256".equals(jwk.getAlgorithm())) {
        throw OAuthAuthenticationException.unauthorized();
      }
      if (!(jwk.getPublicKey() instanceof RSAPublicKey key)) {
        throw OAuthAuthenticationException.unauthorized();
      }
      return key;
    } catch (OAuthAuthenticationException e) {
      throw e;
    } catch (Exception e) {
      if (e instanceof com.auth0.jwk.SigningKeyNotFoundException
        && !(e instanceof com.auth0.jwk.NetworkException) && !(e instanceof com.auth0.jwk.RateLimitReachedException)) {
        throw OAuthAuthenticationException.unauthorized();
      }
      throw OAuthAuthenticationException.unavailable();
    }
  }

  private static JwkProvider jwks(OAuthConfiguration configuration) {
    try {
      return new JwkProviderBuilder(URI.create(configuration.issuer()).resolve(".well-known/jwks.json").toURL())
        .cached(10, 1, TimeUnit.HOURS).rateLimited(10, 1, TimeUnit.MINUTES)
        .timeouts(5000, 5000).build();
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException("Invalid OAuth issuer URL");
    }
  }
}


