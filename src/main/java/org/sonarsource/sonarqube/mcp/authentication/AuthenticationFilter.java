/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
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
import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfiguration;
import org.sonarsource.sonarqube.mcp.log.McpLogger;

/**
 * Authentication filter for the stateless MCP HTTP transport.
 * Validates that each request carries a SONARQUBE_TOKEN header.
 * No session state is created or maintained — every request is authenticated independently,
 * enabling horizontal scaling without sticky sessions.
 * <p>
 * Authentication modes:
 * <ul>
 *   <li>TOKEN (default) - Client must provide a SonarQube token via the SONARQUBE_TOKEN header
 *       on every request</li>
 *   <li>OAUTH - OAuth 2.1 with PKCE (not yet implemented)</li>
 * </ul>
 * <p>
 * Org validation (SonarQube Cloud only):
 * <ul>
 *   <li>If a server-level org is configured, the per-request SONARQUBE_ORG header must be absent.</li>
 *   <li>If no server-level org is configured, the per-request SONARQUBE_ORG header is required.</li>
 * </ul>
 */
public class AuthenticationFilter implements Filter {

  private static final McpLogger LOG = McpLogger.getInstance();
  private static final String SONARQUBE_TOKEN_HEADER = McpServerLaunchConfiguration.SONARQUBE_TOKEN;
  private static final String SONARQUBE_ORG_HEADER = McpServerLaunchConfiguration.SONARQUBE_ORG;

  private final AuthMode authMode;
  private final boolean isSonarCloud;
  @Nullable
  private final String serverOrg;

  public AuthenticationFilter(AuthMode authMode, boolean isSonarCloud, @Nullable String serverOrg) {
    this.authMode = authMode;
    this.isSonarCloud = isSonarCloud;
    this.serverOrg = serverOrg;
    LOG.info("Authentication filter initialized with mode: " + authMode);
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

    if ("OPTIONS".equals(httpRequest.getMethod())) {
      filterChain.doFilter(req, resp);
      return;
    }

    if (authMode == AuthMode.TOKEN) {
      var token = httpRequest.getHeader(SONARQUBE_TOKEN_HEADER);
      if (token == null || token.isBlank()) {
        LOG.warn("Missing or empty SonarQube token from " + httpRequest.getRemoteAddr());
        sendUnauthorizedResponse(httpResponse, "SonarQube token required. Provide via SONARQUBE_TOKEN header.");
        return;
      }
      if (!validateOrg(httpRequest, httpResponse)) {
        return;
      }
      filterChain.doFilter(req, resp);
      return;
    }

    if (authMode == AuthMode.OAUTH) {
      sendUnauthorizedResponse(httpResponse, "OAuth authentication not yet implemented");
      return;
    }

    sendUnauthorizedResponse(httpResponse, "Unsupported authentication mode");
  }

  @Override
  public void destroy() {
    // No cleanup needed
  }

  /**
   * Returns true if the org header is valid for this request, false if a 400 response was sent.
   */
  private boolean validateOrg(HttpServletRequest request, HttpServletResponse response) throws IOException {
    if (!isSonarCloud) {
      return true;
    }
    var org = request.getHeader(SONARQUBE_ORG_HEADER);
    if (serverOrg != null) {
      if (org != null && !org.isBlank()) {
        sendBadRequestResponse(response,
          "The SONARQUBE_ORG header is not allowed: this server is already configured with an organization. " +
            "Remove the SONARQUBE_ORG header from your request.");
        return false;
      }
    } else {
      if (org == null || org.isBlank()) {
        sendBadRequestResponse(response,
          "A SONARQUBE_ORG header is required: this server is not configured with a default organization. " +
            "Provide your SonarQube Cloud organization key in the SONARQUBE_ORG request header.");
        return false;
      }
    }
    return true;
  }

  private static void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json");
    response.setHeader("WWW-Authenticate", "Bearer realm=\"MCP Server\"");
    response.getWriter().write(jsonRpcError(message));
  }

  private static void sendBadRequestResponse(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    response.setContentType("application/json");
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
