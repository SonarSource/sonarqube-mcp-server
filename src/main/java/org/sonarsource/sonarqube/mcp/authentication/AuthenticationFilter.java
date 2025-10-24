/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
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

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.sonarsource.sonarqube.mcp.context.RequestContext;
import org.sonarsource.sonarqube.mcp.log.McpLogger;

/**
 * Authentication filter for MCP HTTP transport.
 * Extracts SonarQube tokens from the SONARQUBE_TOKEN HTTP header.
 * <p>
 * Token format:
 * <ul>
 *   <li>SONARQUBE_TOKEN header - Custom header: {@code "SONARQUBE_TOKEN": "your-token"}</li>
 * </ul>
 * <p>
 * Authentication modes:
 * <ul>
 *   <li>TOKEN (default) - Client must provide SonarQube token via SONARQUBE_TOKEN header</li>
 *   <li>OAUTH - OAuth 2.1 with PKCE (not yet implemented)</li>
 * </ul>
 * <p>
 * Note: This filter is only registered in HTTP mode. Stdio mode has no HTTP authentication.
 */
public class AuthenticationFilter implements Filter {

  private static final McpLogger LOG = McpLogger.getInstance();
  private static final String SONARQUBE_TOKEN_HEADER = "SONARQUBE_TOKEN";
  
  /**
   * Request attribute key where the extracted bearer token is stored.
   * This token is the client's SonarQube token, passed through to SonarQube API calls.
   */
  public static final String SONARQUBE_TOKEN_ATTRIBUTE = "sonarqube.token";
  
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

    try {
      // Clear any existing context (from previous request on same thread) before setting new one
      RequestContext.clear();

      if (authMode == AuthMode.TOKEN) {
        var token = extractToken(httpRequest);

        if (token == null || token.isBlank()) {
          LOG.warn("Missing or empty SonarQube token from " + httpRequest.getRemoteAddr());
          sendUnauthorizedResponse(httpResponse, "SonarQube token required. Provide via SONARQUBE_TOKEN header.");
          return;
        }

        httpRequest.setAttribute(SONARQUBE_TOKEN_ATTRIBUTE, token);

        RequestContext.set(token);

        filterChain.doFilter(req, resp);

        if (httpRequest.isAsyncStarted()) {
          LOG.info("Async request detected, registering cleanup listener");
          httpRequest.getAsyncContext().addListener(new AsyncListener() {
            @Override
            public void onComplete(AsyncEvent event) {
              RequestContext.clear();
            }
            @Override
            public void onTimeout(AsyncEvent event) {
              RequestContext.clear();
            }
            @Override
            public void onError(AsyncEvent event) {
              RequestContext.clear();
            }
            @Override
            public void onStartAsync(AsyncEvent event) {
              // No action needed - context already set
            }
          });
        } else {
          // Synchronous request - clear immediately
          RequestContext.clear();
        }
        return;
      }
      
      if (authMode == AuthMode.OAUTH) {
        sendUnauthorizedResponse(httpResponse, "OAuth authentication not yet implemented");
        return;
      }

      sendUnauthorizedResponse(httpResponse, "Unsupported authentication mode");
    } catch (Exception e) {
      RequestContext.clear();
      throw e;
    }
  }

  @Override
  public void destroy() {
    // No cleanup needed
  }
  
  /**
   * Extract SonarQube token from SONARQUBE_TOKEN header.
   */
  private static String extractToken(HttpServletRequest request) {
    var sonarQubeTokenHeader = request.getHeader(SONARQUBE_TOKEN_HEADER);
    if (sonarQubeTokenHeader != null && !sonarQubeTokenHeader.trim().isEmpty()) {
      LOG.info("Extracted token from SONARQUBE_TOKEN header");
      return sonarQubeTokenHeader.trim();
    }
    
    LOG.info("No token found in SONARQUBE_TOKEN header");
    return null;
  }

  /**
   * Send 401 Unauthorized response with WWW-Authenticate header (MCP spec)
   */
  private static void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json");

    // Per MCP spec: WWW-Authenticate header for resource metadata discovery
    // For OAUTH mode, this would include the authorization server location
    response.setHeader("WWW-Authenticate", "Bearer realm=\"MCP Server\"");

    var errorJson = String.format(
      "{\"error\":\"unauthorized\",\"error_description\":\"%s\"}", 
      message
    );
    response.getWriter().write(errorJson);
  }
  
}


