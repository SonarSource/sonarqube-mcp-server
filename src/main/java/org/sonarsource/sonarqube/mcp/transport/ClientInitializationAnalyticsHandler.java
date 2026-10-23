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

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.function.Function;
import org.sonarsource.sonarqube.mcp.analytics.AnalyticsService;
import org.sonarsource.sonarqube.mcp.analytics.HttpAnalyticsContext;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import reactor.core.publisher.Mono;

/** Observes successful HTTP handshakes without storing session or client state. */
final class ClientInitializationAnalyticsHandler implements McpStatelessServerHandler {
  private static final McpLogger LOG = McpLogger.getInstance();
  private final McpStatelessServerHandler delegate;
  private final AnalyticsService analytics;
  private final Function<McpTransportContext, ServerApi> apiFactory;

  ClientInitializationAnalyticsHandler(McpStatelessServerHandler delegate, AnalyticsService analytics, Function<McpTransportContext, ServerApi> apiFactory) {
    this.delegate = delegate;
    this.analytics = analytics;
    this.apiFactory = apiFactory;
  }

  @Override
  public Mono<McpSchema.JSONRPCResponse> handleRequest(McpTransportContext context, McpSchema.JSONRPCRequest request) {
    var response = delegate.handleRequest(context, request);
    if (!McpSchema.METHOD_INITIALIZE.equals(request.method())) {
      return response;
    }
    return response.doOnNext(result -> {
      if (result.error() == null && result.result() != null) {
        try {
          var attribution = (HttpAnalyticsContext) context.get(HttpAnalyticsContext.CONTEXT_KEY);
          var client = McpJsonMappers.DEFAULT.convertValue(request.params(), McpSchema.InitializeRequest.class).clientInfo();
          if (attribution != null) {
            analytics.notifyClientInitialized(attribution, client == null ? null : client.name(), client == null ? null : client.version(),
              System.currentTimeMillis(), () -> apiFactory.apply(context));
          }
        } catch (RuntimeException e) {
          LOG.debug("Unable to report MCP initialization (" + e.getClass().getSimpleName() + ")");
        }
      }
    });
  }

  @Override
  public Mono<Void> handleNotification(McpTransportContext context, McpSchema.JSONRPCNotification notification) {
    return delegate.handleNotification(context, notification);
  }
}
