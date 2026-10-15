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

import com.google.gson.Gson;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OAuthProtectedResourceMetadata extends HttpServlet {

  private static final Logger LOG = LoggerFactory.getLogger(OAuthProtectedResourceMetadata.class);

  // RFC 9728 discovery path for /mcp; deployments can configure an external metadata URL.
  @SuppressWarnings("java:S1075")
  public static final String PATH = "/.well-known/oauth-protected-resource/mcp";
  private static final Set<String> SUPPORTED_SCOPES = Set.of("read:all", "write:all");
  private final String resource;
  private final String issuer;
  private final URI metadataUri;
  private final boolean selfHosted;

  public OAuthProtectedResourceMetadata(String resource, String issuer) {
    this(resource, issuer, null);
  }

  public OAuthProtectedResourceMetadata(String resource, String issuer, @Nullable String metadataUrl) {
    var resourceUri = OAuthConfiguration.validatedResourceUri(resource, issuer);
    this.resource = resource;
    this.issuer = issuer;
    this.metadataUri = metadataUrl == null ? resourceUri.resolve(PATH) : OAuthConfiguration.validatedHttpsUri(metadataUrl);
    this.selfHosted = metadataUrl == null;
  }

  public boolean isSelfHosted() {
    return selfHosted;
  }

  public String challenge() {
    return challenge(SUPPORTED_SCOPES);
  }

  public String challenge(Set<String> requiredScopes) {
    if (requiredScopes.isEmpty() || !SUPPORTED_SCOPES.containsAll(requiredScopes)) {
      throw new IllegalArgumentException("OAuth challenges require supported delegation scopes");
    }
    var scopes = requiredScopes.stream().sorted().collect(Collectors.joining(" "));
    return "Bearer resource_metadata=\"" + metadataUri + "\", scope=\"" + scopes + "\"";
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.setContentType("application/json");
    response.setHeader("Cache-Control", "public, max-age=300");
    var body = new Gson().toJson(Map.of(
      "resource", resource,
      "authorization_servers", List.of(issuer),
      "scopes_supported", List.of("read:all", "write:all"),
      "bearer_methods_supported", List.of("header")));
    try {
      response.getWriter().write(body);
    } catch (IOException e) {
      LOG.warn("Unable to write OAuth resource metadata response", e);
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }
}
