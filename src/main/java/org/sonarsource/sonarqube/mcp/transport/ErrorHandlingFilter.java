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
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.sonarsource.sonarqube.mcp.log.McpLogger;

/**
 * Catch-all error handling filter that wraps the entire servlet pipeline.
 * Converts unhandled exceptions into spec-compliant JSON-RPC error responses.
 *
 * <p>Per the MCP Streamable HTTP spec, the server MUST return {@code Content-Type: application/json}
 * with a JSON object for JSON-RPC requests. Without this filter, unhandled exceptions
 * produce Jetty's default HTML error page, violating this requirement.
 *
 * <p>This filter is registered as the outermost filter in the chain, so it catches
 * exceptions from all layers: security filter, authentication filter, context extraction, and the SDK servlet itself.
 */
public class ErrorHandlingFilter implements Filter {

  private static final McpLogger LOG = McpLogger.getInstance();

  @Override
  public void init(FilterConfig filterConfig) {
    // No initialization needed
  }

  @Override
  public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) {
    try {
      chain.doFilter(req, resp);
    } catch (Exception e) {
      LOG.error("Unhandled exception in request pipeline", e);
      try {
        var httpResponse = (HttpServletResponse) resp;
        if (!httpResponse.isCommitted()) {
          httpResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
          httpResponse.setContentType("application/json");
          httpResponse.setCharacterEncoding("UTF-8");
          httpResponse.getWriter().write("{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}");
        }
      } catch (Exception writeError) {
        LOG.error("Failed to write error response", writeError);
      }
    }
  }

  @Override
  public void destroy() {
    // No cleanup needed
  }

}
