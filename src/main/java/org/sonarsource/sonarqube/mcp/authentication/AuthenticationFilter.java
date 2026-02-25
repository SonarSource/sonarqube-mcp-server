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
 */
public class AuthenticationFilter implements Filter {

  private static final McpLogger LOG = McpLogger.getInstance();
  private static final String SONARQUBE_TOKEN_HEADER = McpServerLaunchConfiguration.SONARQUBE_TOKEN;

  private final AuthMode authMode;

  public AuthenticationFilter(AuthMode authMode) {
    this.authMode = authMode;
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

  private static void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json");
    response.setHeader("WWW-Authenticate", "Bearer realm=\"MCP Server\"");
    var errorJson = String.format("{\"error\":\"unauthorized\",\"error_description\":\"%s\"}", message);
    response.getWriter().write(errorJson);
  }

}
