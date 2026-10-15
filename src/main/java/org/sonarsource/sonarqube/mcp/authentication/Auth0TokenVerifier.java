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
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import jakarta.annotation.Nullable;
import java.time.Clock;
import java.time.DateTimeException;

final class Auth0TokenVerifier {
  private static final long CLOCK_SKEW_SECONDS = 60;

  private final JWTVerifier mcpVerifier;
  private final JWTVerifier cloudVerifier;

  Auth0TokenVerifier(OAuthConfiguration configuration, RSAKeyProvider provider, Clock clock) {
    var algorithm = Algorithm.RSA256(provider);
    this.mcpVerifier = verifier(algorithm, configuration.issuer(), configuration.resource(), clock);
    this.cloudVerifier = verifier(algorithm, configuration.issuer(), configuration.cloudAudience(), clock);
  }

  private static JWTVerifier verifier(Algorithm algorithm, String issuer, String audience, Clock clock) {
    var verification = (JWTVerifier.BaseVerification) JWT.require(algorithm).withIssuer(issuer).withAudience(audience)
      .acceptIssuedAt(CLOCK_SKEW_SECONDS).acceptNotBefore(CLOCK_SKEW_SECONDS)
      .withClaimPresence("exp").withClaimPresence("iat").withClaimPresence("sub").withClaimPresence("azp");
    return verification.build(clock);
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
      var verified = verifier.verify(token);
      if (verified.getExpiresAtAsInstant() == null || verified.getIssuedAtAsInstant() == null
        || verified.getClaim("sub").asString() == null || verified.getClaim("azp").asString() == null) {
        throw OAuthAuthenticationException.unauthorized();
      }
      return verified;
    } catch (JWTVerificationException | DateTimeException e) {
      throw OAuthAuthenticationException.unauthorized();
    }
  }
}
