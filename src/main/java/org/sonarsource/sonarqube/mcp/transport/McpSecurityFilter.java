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
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.authentication.AuthenticationFilter;
import org.sonarsource.sonarqube.mcp.log.McpLogger;

/**
 * Security filter for MCP HTTP transport to prevent DNS rebinding attacks
 * and enforce security best practices per MCP specification.
 * <p>
 * Origin enforcement is a browser-layer backstop for local deployments. Native MCP clients
 * authenticate with Bearer tokens and may send non-loopback {@code Origin} headers; OAuth
 * bootstrap requests intentionally arrive without a token and are allowed through to receive 401.
 */
public class McpSecurityFilter implements Filter {

  private static final McpLogger LOG = McpLogger.getInstance();

  static final String HEALTH_ENDPOINT = "/health";
  static final String INFO_ENDPOINT = "/info";
  static final String MCP_ENDPOINT = "/mcp";
  private static final String WELL_KNOWN_PREFIX = "/.well-known/";

  private final String hostBinding;
  private final Set<String> extraAllowedOrigins;
  private final String serverVersion;

  public McpSecurityFilter(String hostBinding, String serverVersion) {
    this(hostBinding, List.of(), serverVersion);
  }

  public McpSecurityFilter(String hostBinding, List<String> extraAllowedOrigins, String serverVersion) {
    this.hostBinding = hostBinding;
    this.extraAllowedOrigins = Set.copyOf(extraAllowedOrigins);
    this.serverVersion = serverVersion;

    if (!this.extraAllowedOrigins.isEmpty()) {
      LOG.info("MCP HTTP server configured with additional allowed origins: " + this.extraAllowedOrigins);
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

    if (INFO_ENDPOINT.equals(httpRequest.getRequestURI())) {
      httpResponse.setStatus(HttpServletResponse.SC_OK);
      httpResponse.setContentType("application/json");
      httpResponse.getWriter().write("{\"version\":\"" + escapeJson(serverVersion) + "\"}");
      return;
    }

    var origin = httpRequest.getHeader("Origin");
    boolean isOptionsRequest = "OPTIONS".equals(httpRequest.getMethod());

    if (!isOriginAllowed(httpRequest, origin)) {
      LOG.warn("Rejected request from disallowed origin: " + origin);
      httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
      httpResponse.setContentType("application/json");
      httpResponse.getWriter().write(jsonRpcError("Origin not allowed"));
      return;
    }

    if (origin != null && isOriginInAllowlist(origin)) {
      httpResponse.setHeader("Access-Control-Allow-Origin", origin);
    } else if (isOptionsRequest) {
      httpResponse.setHeader("Access-Control-Allow-Origin", "*");
    }

    httpResponse.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    httpResponse.setHeader("Access-Control-Allow-Headers",
      "Content-Type, Accept, Authorization, SONARQUBE_TOKEN, SONARQUBE_ORG, MCP-Protocol-Version");
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
    return String.format("{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32000,\"message\":\"%s\"}}", escapeJson(message));
  }

  private static String escapeJson(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  /**
   * Whether the request may proceed given its {@code Origin} header.
   * Absent, blank, authenticated, OAuth-bootstrap, remote-binding, and allowlisted origins are permitted.
   */
  private boolean isOriginAllowed(HttpServletRequest request, @Nullable String origin) {
    if (origin == null || origin.isBlank()) {
      return true;
    }
    if (!isLocalBinding()) {
      return true;
    }
    if (hasAuthToken(request)) {
      return true;
    }
    if (isOAuthBootstrapRequest(request)) {
      return true;
    }
    return isOriginInAllowlist(origin);
  }

  /**
   * Whether the origin may be reflected in CORS response headers.
   */
  private boolean isOriginInAllowlist(String origin) {
    if (extraAllowedOrigins.contains(origin)) {
      return true;
    }
    if (isLocalBinding()) {
      return isLoopbackOrigin(origin);
    }
    return false;
  }

  private boolean isLocalBinding() {
    if ("0.0.0.0".equals(hostBinding)) {
      // Required for container port mapping; origin policy stays restrictive on local bindings.
      return true;
    }
    try {
      return InetAddress.getByName(hostBinding).isLoopbackAddress();
    } catch (UnknownHostException e) {
      LOG.warn("Could not resolve host binding '" + hostBinding + "' for origin policy: " + e.getMessage());
      return false;
    }
  }

  private static boolean hasAuthToken(HttpServletRequest request) {
    var token = AuthenticationFilter.extractToken(request);
    return token != null && !token.isBlank();
  }

  private static boolean isOAuthBootstrapRequest(HttpServletRequest request) {
    // Safe while AuthenticationFilter rejects tokenless /mcp requests (see HttpServerTransportIntegrationTest).
    var path = request.getRequestURI();
    if (path == null) {
      return false;
    }
    return MCP_ENDPOINT.equals(path) || path.startsWith(WELL_KNOWN_PREFIX);
  }

  private static boolean isLoopbackOrigin(String origin) {
    try {
      var uri = URI.create(origin);
      var scheme = uri.getScheme();
      if (!"http".equals(scheme) && !"https".equals(scheme)) {
        return false;
      }
      var host = uri.getHost();
      if (host == null) {
        return false;
      }
      return InetAddress.getByName(host).isLoopbackAddress();
    } catch (UnknownHostException | IllegalArgumentException e) {
      LOG.warn("Rejected malformed or unknown origin: " + origin);
      return false;
    }
  }

}
