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

import io.micrometer.context.ThreadLocalAccessor;

/**
 * ThreadLocalAccessor for RequestContext to enable automatic context propagation
 * across thread boundaries when using Reactor's boundedElastic scheduler.
 * <p>
 * This solves the issue where InheritableThreadLocal context is lost when threads
 * from the boundedElastic pool are reused. The first thread (boundedElastic-1) is
 * typically created during KeepAliveScheduler startup from the main thread, which
 * has no HTTP request context. When this thread is later reused for tool execution,
 * the InheritableThreadLocal returns null because context is only inherited at
 * thread creation time, not when threads are reused.
 * <p>
 * By registering this accessor with Micrometer's ContextRegistry and enabling
 * Reactor's automatic context propagation, the RequestContext is properly
 * captured and restored across thread boundaries.
 *
 * @see <a href="https://github.com/modelcontextprotocol/java-sdk/issues/704">MCP Java SDK Issue #704</a>
 */
public class RequestContextThreadLocalAccessor implements ThreadLocalAccessor<RequestContext> {

  /**
   * Key used to identify this context in the Reactor Context.
   */
  public static final String KEY = "sonarqube.request.context";

  @Override
  public Object key() {
    return KEY;
  }

  @Override
  public RequestContext getValue() {
    return RequestContext.current();
  }

  @Override
  public void setValue(RequestContext value) {
    if (value != null) {
      RequestContext.setContext(value);
    }
  }

  @Override
  public void setValue() {
    // Called when no value exists in context - clear any existing ThreadLocal
    RequestContext.clear();
  }

}
