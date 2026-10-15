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

import jakarta.annotation.Nullable;
import java.net.URI;
import java.time.Duration;

public final class OAuthConfiguration {
  public static final Duration DEFAULT_CACHE_TTL = Duration.ofSeconds(30);
  public static final int DEFAULT_CACHE_SIZE = 256;

  private final String issuer;
  private final String resource;
  private final String cloudAudience;
  private final String clientId;
  private final String clientSecret;
  private final Duration cacheTtl;
  private final int cacheSize;

  public OAuthConfiguration(String issuer, String resource, String cloudAudience, String clientId, String clientSecret) {
    this(issuer, resource, cloudAudience, clientId, clientSecret, DEFAULT_CACHE_TTL, DEFAULT_CACHE_SIZE);
  }

  public OAuthConfiguration(String issuer, String resource, String cloudAudience, String clientId, String clientSecret, Duration cacheTtl, int cacheSize) {
    var resourceUri = OAuthProtectedResourceMetadata.validatedResourceUri(resource, issuer);
    var cloudUri = URI.create(required(cloudAudience));
    if (!"https".equals(cloudUri.getScheme()) || cloudUri.getHost() == null || !"/".equals(cloudUri.getPath())
      || cloudUri.getUserInfo() != null || cloudUri.getQuery() != null || cloudUri.getFragment() != null || cloudAudience.equals(resource)) {
      throw new IllegalArgumentException("OAuth Cloud audience must be a distinct HTTPS API root ending in /");
    }
    if (!resourceUri.resolve("/").equals(cloudUri)) {
      throw new IllegalArgumentException("OAuth MCP resource and Cloud audience must share the same API origin");
    }
    if (cacheTtl == null || cacheTtl.isNegative() || cacheTtl.compareTo(DEFAULT_CACHE_TTL) > 0 || cacheSize < 1 || cacheSize > 5000) {
      throw new IllegalArgumentException("OAuth exchange cache requires a TTL between zero and 30 seconds and a size between one and 5000");
    }
    this.issuer = issuer;
    this.resource = resource;
    this.cloudAudience = cloudAudience;
    this.clientId = required(clientId);
    this.clientSecret = required(clientSecret);
    this.cacheTtl = cacheTtl;
    this.cacheSize = cacheSize;
  }

  private static String required(@Nullable String value) {
    if (value == null || value.isBlank() || value.startsWith("${")) {
      throw new IllegalArgumentException("OAuth exchange credentials and audience must be explicitly configured");
    }
    return value;
  }

  public String issuer() {
    return issuer;
  }

  public String resource() {
    return resource;
  }

  public String cloudAudience() {
    return cloudAudience;
  }

  public String clientId() {
    return clientId;
  }

  String clientSecret() {
    return clientSecret;
  }

  public Duration cacheTtl() {
    return cacheTtl;
  }

  public int cacheSize() {
    return cacheSize;
  }

  @Override
  public String toString() {
    return "OAuthConfiguration[issuer=" + issuer + ", resource=" + resource + ", cloudAudience=" + cloudAudience + ", credentials=redacted]";
  }
}
