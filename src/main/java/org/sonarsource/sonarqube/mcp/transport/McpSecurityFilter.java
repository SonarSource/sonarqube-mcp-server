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
package org.sonarsource.sonarqube.mcp.transport;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import org.sonarsource.sonarqube.mcp.log.McpLogger;

/**
 * Security filter for MCP HTTP transport to prevent DNS rebinding attacks
 * and enforce security best practices per MCP specification.
 */
public class McpSecurityFilter implements Filter {

  private static final McpLogger LOG = McpLogger.getInstance();

  static final String HEALTH_ENDPOINT = "/health";

  // Allowed hosts for localhost deployments (exact match only)
  private static final Set<String> ALLOWED_LOCALHOST_HOSTS = Set.of(
    "localhost",
    "127.0.0.1",
    "[::1]"
  );

  private final String hostBinding;
  private final boolean allowAllOrigins;

  public McpSecurityFilter(String hostBinding) {
    this.hostBinding = hostBinding;
    // Only allow all origins if explicitly bound to all interfaces (0.0.0.0)
    // Otherwise, restrict to localhost origins
    this.allowAllOrigins = "0.0.0.0".equals(hostBinding);
    
    if (allowAllOrigins) {
      LOG.warn("MCP HTTP server is bound to all network interfaces (0.0.0.0). " +
                  "This is less secure. Consider binding to 127.0.0.1 for local use only.");
    }
  }

  @Override
  public void init(FilterConfig config) {
    // No initialization needed
  }

  @Override
  public void doFilter(ServletRequest req, ServletResponse resp, FilterChain filterChain) throws IOException, ServletException {
    var httpRequest = (HttpServletRequest) req;
    var httpResponse = (HttpServletResponse) resp;

    if (HEALTH_ENDPOINT.equals(httpRequest.getRequestURI())) {
      httpResponse.setStatus(HttpServletResponse.SC_OK);
      return;
    }

    var origin = httpRequest.getHeader("Origin");
    boolean isOptionsRequest = "OPTIONS".equals(httpRequest.getMethod());

    if (origin != null && !isOriginAllowed(origin)) {
      LOG.warn("Rejected request from disallowed origin: " + origin);
      httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
      httpResponse.setContentType("application/json");
      httpResponse.getWriter().write(jsonRpcError("Origin not allowed"));
      return;
    }

    if (origin != null && isOriginAllowed(origin)) {
      httpResponse.setHeader("Access-Control-Allow-Origin", origin);
    } else if (allowAllOrigins || isOptionsRequest) {
      httpResponse.setHeader("Access-Control-Allow-Origin", "*");
    }

    httpResponse.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
    httpResponse.setHeader("Access-Control-Allow-Headers",
      "Content-Type, Accept, SONARQUBE_TOKEN, SONARQUBE_ORG, MCP-Protocol-Version");
    httpResponse.setHeader("Access-Control-Max-Age", "3600");

    if (isOptionsRequest) {
      httpResponse.setStatus(HttpServletResponse.SC_OK);
      return;
    }

    filterChain.doFilter(req, resp);
  }

  @Override
  public void destroy() {
    // No cleanup needed
  }

  /**
   * Builds a JSON-RPC 2.0 error response with no id, as required by the MCP spec for transport-level errors.
   * Uses -32000 (server-defined error) since these are HTTP transport-layer rejections, not JSON-RPC payload errors.
   */
  private static String jsonRpcError(String message) {
    var escapedMessage = message.replace("\\", "\\\\").replace("\"", "\\\"");
    return String.format("{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32000,\"message\":\"%s\"}}", escapedMessage);
  }

  /**
   * Check if the given origin is allowed based on the server's host binding.
   */
  private boolean isOriginAllowed(String origin) {
    if (allowAllOrigins) {
      return true;
    }
    
    // For localhost bindings, only allow localhost origins
    if ("127.0.0.1".equals(hostBinding) || "localhost".equals(hostBinding)) {
      return isLocalhostOrigin(origin);
    }
    
    // For other specific host bindings, be restrictive
    return false;
  }
  
  /**
   * Check if the origin is a valid localhost origin.
   * Parses the URL to extract the host and checks for exact match.
   */
  private static boolean isLocalhostOrigin(String origin) {
    try {
      var uri = URI.create(origin);
      var host = uri.getHost();
      var scheme = uri.getScheme();
      
      // Must be http or https
      if (!"http".equals(scheme) && !"https".equals(scheme)) {
        return false;
      }
      
      // Host must exactly match allowed localhost hosts
      return host != null && ALLOWED_LOCALHOST_HOSTS.contains(host);
    } catch (IllegalArgumentException e) {
      LOG.warn("Rejected malformed origin: " + origin);
      return false;
    }
  }

}

