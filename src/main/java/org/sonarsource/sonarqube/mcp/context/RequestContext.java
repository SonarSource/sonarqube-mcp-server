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
 * In HTTP mode, this stores the MCP session ID. The actual token is stored in
 * {@link org.sonarsource.sonarqube.mcp.authentication.SessionTokenStore} and looked up
 * by session ID when needed.
 * <p>
 * In stdio mode, this is not used (token comes from server configuration).
 * <p>
 * <b>IMPORTANT:</b> Context must be properly cleaned up after request processing to avoid
 * thread pool contamination and memory leaks.
 */
public record RequestContext(String sessionId) {

  private static final ThreadLocal<RequestContext> CONTEXT = new InheritableThreadLocal<>();

  public static RequestContext current() {
    return CONTEXT.get();
  }

  /**
   * Set the request context for the current thread.
   * This should be called by the tool execution handler with the session ID from the MCP exchange.
   *
   * @param sessionId the MCP session ID
   */
  public static void set(String sessionId) {
    CONTEXT.set(new RequestContext(sessionId));
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

