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

import io.micrometer.context.ContextRegistry;
import reactor.core.publisher.Hooks;

/**
 * Initializes Micrometer Context Propagation for proper ThreadLocal context
 * propagation across Reactor's boundedElastic scheduler.
 * <p>
 * This must be called early in application startup, before any Reactor
 * schedulers are created, to ensure context is properly propagated when
 * threads are reused from the boundedElastic pool.
 * <p>
 * <b>Background:</b> The MCP Java SDK uses Reactor's boundedElastic scheduler
 * for tool execution. This scheduler creates a pool of threads (size = CPUs * 10).
 * When InheritableThreadLocal is used for request context (like authentication
 * tokens), context is only inherited when new threads are created, not when
 * existing threads are reused. This causes context loss after ~N tool calls
 * where N is the pool size.
 * <p>
 * By enabling automatic context propagation and registering our
 * {@link RequestContextThreadLocalAccessor}, the RequestContext is properly
 * captured from Reactor's Context and restored to ThreadLocal when threads
 * are borrowed from the pool.
 *
 * @see RequestContextThreadLocalAccessor
 * @see <a href="https://github.com/modelcontextprotocol/java-sdk/issues/704">MCP Java SDK Issue #704</a>
 * @see <a href="https://micrometer.io/docs/contextPropagation">Micrometer Context Propagation</a>
 */
public final class ContextPropagationInitializer {

  private static volatile boolean initialized = false;

  private ContextPropagationInitializer() {
    // Utility class
  }

  /**
   * Initialize context propagation. This method is idempotent and can be called
   * multiple times safely - initialization will only happen once.
   * <p>
   * This should be called at application startup before any HTTP requests are
   * processed.
   */
  public static synchronized void initialize() {
    if (initialized) {
      return;
    }

    // Register our ThreadLocalAccessor for RequestContext
    ContextRegistry.getInstance().registerThreadLocalAccessor(new RequestContextThreadLocalAccessor());

    // Enable automatic context propagation in Reactor
    // This captures ThreadLocal values into Reactor Context when subscribing
    // and restores them when executing on different threads
    Hooks.enableAutomaticContextPropagation();

    initialized = true;
  }

  /**
   * Check if context propagation has been initialized.
   *
   * @return true if {@link #initialize()} has been called successfully
   */
  public static boolean isInitialized() {
    return initialized;
  }

}
