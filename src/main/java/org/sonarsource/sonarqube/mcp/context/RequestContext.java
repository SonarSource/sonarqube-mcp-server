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
package org.sonarsource.sonarqube.mcp.context;

/**
 * Thread-local context for storing request-scoped information.
 * <p>
 * In HTTP mode, this stores the client's SonarQube token extracted from the Authorization header.
 * In stdio mode, this is not used (token comes from server configuration).
 * <p>
 * This enables tools to access per-request authentication credentials without having direct
 * access to the HTTP servlet request.
 * <p>
 * <b>IMPORTANT:</b> Context must be properly cleaned up after request processing to avoid
 * thread pool contamination and memory leaks.
 * <p>
 * <b>Note on Thread Safety:</b> This class uses InheritableThreadLocal which only propagates
 * context when new threads are created, not when threads are reused from a pool. For proper
 * context propagation across Reactor's boundedElastic scheduler, see
 * {@link RequestContextThreadLocalAccessor} which integrates with Micrometer Context Propagation.
 *
 * @see RequestContextThreadLocalAccessor
 * @see <a href="https://github.com/modelcontextprotocol/java-sdk/issues/704">MCP Java SDK Issue #704</a>
 */
public record RequestContext(String sonarQubeToken) {

  private static final ThreadLocal<RequestContext> CONTEXT = new InheritableThreadLocal<>();

  public static RequestContext current() {
    return CONTEXT.get();
  }

  /**
   * Set the request context for the current thread.
   * This should be called by the transport layer at the start of request processing.
   *
   * @param sonarQubeToken the client's SonarQube token (from header)
   */
  public static void set(String sonarQubeToken) {
    CONTEXT.set(new RequestContext(sonarQubeToken));
  }

  /**
   * Set the request context directly for the current thread.
   * This is used by {@link RequestContextThreadLocalAccessor} during context restoration.
   *
   * @param context the RequestContext to set
   */
  public static void setContext(RequestContext context) {
    CONTEXT.set(context);
  }

  /**
   * Clear the request context for the current thread.
   * This MUST be called after request processing completes to avoid memory leaks
   * and thread pool contamination.
   */
  public static void clear() {
    CONTEXT.remove();
  }

}
