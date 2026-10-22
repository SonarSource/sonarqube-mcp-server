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

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.function.Function;
import jakarta.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfiguration;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.transport.OAuthScopeContext;
import org.sonarsource.sonarqube.mcp.transport.OAuthScopeResponseWrapper;

/**
 * Authentication filter for the stateless MCP HTTP transport.
 * Validates that each request carries a token, accepting two header formats:
 * <ol>
 *   <li>{@code Authorization: Bearer <token>} — preferred, standard HTTP authentication</li>
 *   <li>{@code SONARQUBE_TOKEN: <token>} — deprecated, accepted for backward compatibility only</li>
 * </ol>
 * No session state is created or maintained — every request is authenticated independently,
 * enabling horizontal scaling without sticky sessions.
 * <p>
 * OAuth is accepted alongside SonarQube tokens when trusted Cloud OAuth settings are configured.
 * <p>
 * For SonarQube token requests, the optional SONARQUBE_ORG header selects a fixed request organization when no server organization is configured.
 * A configured server organization rejects client organization headers.
 */
public class AuthenticationFilter implements Filter {

  private static final McpLogger LOG = McpLogger.getInstance();
  private static final String APPLICATION_JSON = "application/json";
  static final String AUTHORIZATION_HEADER = "Authorization";
  static final String BEARER_PREFIX = "Bearer ";
  private static final String SONARQUBE_TOKEN_HEADER = McpServerLaunchConfiguration.SONARQUBE_TOKEN;
  private static final String SONARQUBE_READ_ONLY_HEADER = McpServerLaunchConfiguration.SONARQUBE_READ_ONLY;

  public static final String AUTHENTICATION_ATTRIBUTE = AuthenticationFilter.class.getName() + ".authentication";

  private final boolean oauthEnabled;
  @Nullable
  private final String serverOrg;
  @Nullable
  private final OAuthProtectedResourceMetadata oauthMetadata;
  @Nullable
  private final Function<String, OAuthRequestAuthentication> oauthAuthenticator;

  public AuthenticationFilter(@Nullable String serverOrg, boolean oauthEnabled,
    @Nullable OAuthProtectedResourceMetadata oauthMetadata, @Nullable Function<String, OAuthRequestAuthentication> oauthAuthenticator) {
    this.oauthEnabled = oauthEnabled;
    this.serverOrg = serverOrg;
    this.oauthMetadata = oauthMetadata;
    this.oauthAuthenticator = oauthAuthenticator;
  }

  @Override
  public void init(FilterConfig filterConfig) {
    // No initialization needed
  }

  @Override
  public void doFilter(ServletRequest req, ServletResponse resp, FilterChain filterChain)
    throws IOException, ServletException {
    var httpRequest = (HttpServletRequest) req;
    var httpResponse = (HttpServletResponse) resp;

    if ("OPTIONS".equals(httpRequest.getMethod()) || isLocalMetadataRequest(httpRequest)) {
      filterChain.doFilter(req, resp);
      return;
    }

    if (oauthEnabled) {
      httpResponse.setHeader("Cache-Control", "no-store");
      if (oauthMetadata == null || oauthAuthenticator == null) {
        sendOAuthError(httpResponse, OAuthAuthenticationException.unavailable(), false);
        return;
      }
      String token;
      try {
        token = selectToken(httpRequest);
      } catch (OAuthAuthenticationException e) {
        sendOAuthError(httpResponse, e, true);
        return;
      }
      if (token == null) {
        sendOAuthError(httpResponse, OAuthAuthenticationException.unauthorized(), false);
      } else if (isOAuthCandidate(token)) {
        authenticateOAuth(httpRequest, httpResponse, filterChain, token);
      } else {
        authenticateToken(httpRequest, httpResponse, filterChain, token);
      }
      return;
    }

    var token = extractToken(httpRequest);
    if (token == null || token.isBlank()) {
      LOG.warn("Missing or empty SonarQube token for URI '" + httpRequest.getRequestURI() + "'");
      sendUnauthorizedResponse(httpResponse, "SonarQube token required. Provide via Authorization: Bearer <token> header.");
      return;
    }
    authenticateToken(httpRequest, httpResponse, filterChain, token);
  }

  private void authenticateToken(HttpServletRequest request, HttpServletResponse response, FilterChain chain, String token)
    throws IOException, ServletException {
    var org = request.getHeader(McpServerLaunchConfiguration.SONARQUBE_ORG);
    if (serverOrg != null && org != null && !org.isBlank()) {
      sendBadRequestResponse(response, "The SONARQUBE_ORG header is not allowed: this server is already configured with an organization. "
        + "Remove the SONARQUBE_ORG header from your request.");
      return;
    }
    if (!validateReadOnly(request, response)) {
      return;
    }
    request.setAttribute(AUTHENTICATION_ATTRIBUTE, new HttpRequestAuthentication(token, null));
    chain.doFilter(request, response);
  }

  @Nullable
  private static String selectToken(HttpServletRequest request) {
    var authorization = request.getHeader(AUTHORIZATION_HEADER);
    if (authorization != null) {
      var token = bearerToken(authorization);
      if (hasMultipleHeaders(request, AUTHORIZATION_HEADER) || !isTokenSyntaxValid(token)) {
        throw OAuthAuthenticationException.unauthorized();
      }
      return token;
    }
    var legacyToken = request.getHeader(SONARQUBE_TOKEN_HEADER);
    if (legacyToken == null) {
      return null;
    }
    if (hasMultipleHeaders(request, SONARQUBE_TOKEN_HEADER) || !isTokenSyntaxValid(legacyToken) || isOAuthCandidate(legacyToken)) {
      throw OAuthAuthenticationException.unauthorized();
    }
    LOG.warn("Deprecated header SONARQUBE_TOKEN used from " + request.getRemoteAddr() +
      ". Please migrate to: Authorization: Bearer <token>");
    return legacyToken;
  }

  private static boolean hasMultipleHeaders(HttpServletRequest request, String name) {
    var headers = request.getHeaders(name);
    if (headers == null || !headers.hasMoreElements()) {
      return false;
    }
    headers.nextElement();
    return headers.hasMoreElements();
  }

  private static boolean isTokenSyntaxValid(@Nullable String token) {
    return token != null && token.matches("[A-Za-z0-9._~+/-]+=*");
  }

  private static boolean isOAuthCandidate(String token) {
    return token.contains(".") || token.startsWith("eyJ");
  }

  private boolean isLocalMetadataRequest(HttpServletRequest request) {
    return oauthEnabled && oauthMetadata != null && oauthMetadata.isSelfHosted()
      && OAuthProtectedResourceMetadata.PATH.equals(request.getRequestURI())
      && ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()));
  }

  private void authenticateOAuth(HttpServletRequest request, HttpServletResponse response, FilterChain chain, String token)
    throws IOException, ServletException {
    if (request.getHeader(McpServerLaunchConfiguration.SONARQUBE_ORG) != null) {
      sendBadRequestResponse(response, "SONARQUBE_ORG is not supported for OAuth requests. Use the tool organization argument.");
      return;
    }
    if (!validateReadOnly(request, response)) {
      return;
    }
    OAuthRequestAuthentication authentication;
    try {
      authentication = oauthAuthenticator.apply(token);
      if (authentication == null) {
        throw OAuthAuthenticationException.unavailable();
      }
    } catch (OAuthAuthenticationException e) {
      sendOAuthError(response, e, true);
      return;
    }
    var scopes = new OAuthScopeContext(authentication.scopes());
    request.setAttribute(AUTHENTICATION_ATTRIBUTE, new HttpRequestAuthentication(authentication.cloudToken(), scopes));
    chain.doFilter(request, new OAuthScopeResponseWrapper(response, scopes, oauthMetadata));
  }

  private void sendOAuthError(HttpServletResponse response, OAuthAuthenticationException error, boolean tokenProvided) throws IOException {
    response.setStatus(error.status());
    response.setContentType(APPLICATION_JSON);
    response.setHeader("Cache-Control", "no-store");
    if (oauthMetadata != null && (error.status() == 401 || error.status() == 403)) {
      var challenge = oauthMetadata.challenge();
      if (error.status() == 403) {
        challenge += ", error=\"insufficient_scope\"";
      } else if (tokenProvided) {
        challenge += ", error=\"invalid_token\"";
      }
      response.setHeader("WWW-Authenticate", challenge);
    }
    response.getWriter().write(jsonRpcError(error.getMessage()));
  }

  @Override
  public void destroy() {
    // No cleanup needed
  }

  private static boolean validateReadOnly(HttpServletRequest request, HttpServletResponse response) throws IOException {
    var value = request.getHeader(SONARQUBE_READ_ONLY_HEADER);
    if (value != null && !value.isBlank()) {
      var trimmed = value.trim();
      if (!"true".equalsIgnoreCase(trimmed) && !"false".equalsIgnoreCase(trimmed)) {
        LOG.warn("Rejected request with invalid SONARQUBE_READ_ONLY header value");
        sendBadRequestResponse(response, "Invalid SONARQUBE_READ_ONLY header value. Expected 'true' or 'false'.");
        return false;
      }
    }
    return true;
  }

  /**
   * Extracts the token from the request, preferring {@code Authorization: Bearer <token>}
   * and falling back to the deprecated {@code SONARQUBE_TOKEN} header with a warning.
   */
  @Nullable
  public static String extractToken(HttpServletRequest request) {
    var bearerToken = extractBearerToken(request);
    if (bearerToken != null) {
      return bearerToken;
    }
    var legacyToken = request.getHeader(SONARQUBE_TOKEN_HEADER);
    if (legacyToken != null && !legacyToken.isBlank()) {
      LOG.warn("Deprecated header SONARQUBE_TOKEN used from " + request.getRemoteAddr() +
        ". Please migrate to: Authorization: Bearer <token>");
      return legacyToken;
    }
    return null;
  }

  /**
   * Extracts the bearer token from the standard {@code Authorization: Bearer <token>} header.
   * Returns {@code null} if the header is absent or does not use the Bearer scheme.
   */
  @Nullable
  private static String extractBearerToken(HttpServletRequest request) {
    return bearerToken(request.getHeader(AUTHORIZATION_HEADER));
  }

  @Nullable
  private static String bearerToken(@Nullable String authHeader) {
    if (authHeader == null || !authHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
      return null;
    }
    return authHeader.substring(BEARER_PREFIX.length());
  }

  private static void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(APPLICATION_JSON);
    response.setHeader("WWW-Authenticate", "Bearer realm=\"MCP Server\"");
    response.getWriter().write(jsonRpcError(message));
  }

  private static void sendBadRequestResponse(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    response.setContentType(APPLICATION_JSON);
    response.getWriter().write(jsonRpcError(message));
  }

  /**
   * Builds a JSON-RPC 2.0 error response with no id, as required by the MCP spec for transport-level errors.
   * Uses -32000 (server-defined error) since these are HTTP transport-layer rejections, not JSON-RPC payload errors.
   */
  private static String jsonRpcError(String message) {
    var escapedMessage = message.replace("\\", "\\\\").replace("\"", "\\\"");
    return String.format("{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32000,\"message\":\"%s\"}}", escapedMessage);
  }

}
