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
package org.sonarsource.sonarqube.mcp.transport;

import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import jakarta.servlet.DispatcherType;
import java.time.Duration;
import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.sonarsource.sonarqube.mcp.authentication.AuthMode;
import org.sonarsource.sonarqube.mcp.authentication.AuthenticationFilter;
import org.sonarsource.sonarqube.mcp.log.McpLogger;

/**
 * HTTP transport provider for MCP server using the SDK's built-in servlet transport.
 * This implementation follows the MCP Streamable HTTP specification and uses Jetty 
 * as the servlet container to host the SDK's HttpServletStreamableServerTransportProvider.
 */
public class HttpServerTransportProvider {

  private static final McpLogger LOG = McpLogger.getInstance();
  private static final String MCP_ENDPOINT = "/mcp";
  
  private final int port;
  private final String host;
  private final AuthMode authMode;
  private final HttpServletStreamableServerTransportProvider mcpTransportProvider;
  private Server httpServer;
  private volatile boolean serverReady = false;

  /**
   * Create HTTP transport provider with custom host binding and authentication.
   * 
   * @param port HTTP port (e.g., 8080)
   * @param host Host to bind to (127.0.0.1 for localhost, 0.0.0.0 for all interfaces)
   * @param authMode Authentication mode (e.g., TOKEN, OAUTH)
   */
  public HttpServerTransportProvider(int port, String host, AuthMode authMode) {
    this.port = port;
    this.host = host;
    this.authMode = authMode;

    this.mcpTransportProvider = HttpServletStreamableServerTransportProvider.builder()
        .mcpEndpoint(MCP_ENDPOINT)
        .keepAliveInterval(Duration.ofSeconds(30))
        .build();
        
    LOG.info("Created HTTP transport provider for " + host + ":" + port + MCP_ENDPOINT + " with authentication: " + authMode);
    
    // Warn about security risk when binding to all interfaces
    if ("0.0.0.0".equals(host)) {
      LOG.warn("SECURITY WARNING: MCP HTTP server is configured to bind to all network interfaces (0.0.0.0). " +
                  "This exposes the server to your entire network. " +
                  "For local development, consider using 127.0.0.1 instead.");
    }
  }

  public HttpServletStreamableServerTransportProvider getTransportProvider() {
    return mcpTransportProvider;
  }

  /**
   * Start the HTTP server with the MCP transport.
   * 
   * @return CompletableFuture that completes when server is ready
   */
  public CompletableFuture<Void> startServer() {
    if (httpServer != null && httpServer.isRunning()) {
      LOG.warn("HTTP server is already running on " + host + ":" + port);
      return CompletableFuture.completedFuture(null);
    }

    var startupFuture = new CompletableFuture<Void>();

    var servletContextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
    servletContextHandler.setContextPath("/");

    var authFilter = new FilterHolder(new AuthenticationFilter(authMode));
    servletContextHandler.addFilter(authFilter, "/*", EnumSet.of(DispatcherType.REQUEST));

    var securityFilter = new FilterHolder(new McpSecurityFilter(host));
    servletContextHandler.addFilter(securityFilter, "/*", EnumSet.of(DispatcherType.REQUEST));

    var servletHolder = new ServletHolder(mcpTransportProvider);
    // Required for Server-Sent Events
    servletHolder.setAsyncSupported(true);
    servletContextHandler.addServlet(servletHolder, "/*");

    // Create Jetty server
    httpServer = new Server();
    var connector = new ServerConnector(httpServer);
    connector.setHost(host);
    connector.setPort(port);
    httpServer.addConnector(connector);
    httpServer.setHandler(servletContextHandler);

    CompletableFuture.runAsync(() -> {
      try {
        httpServer.start();
        serverReady = true;
        LOG.info("MCP HTTP server started successfully on http://" + host + ":" + port + MCP_ENDPOINT);
        startupFuture.complete(null);
        httpServer.join();
      } catch (InterruptedException e) {
        LOG.info("MCP HTTP server was interrupted - this is normal during shutdown");
        Thread.currentThread().interrupt();
        if (!startupFuture.isDone()) {
          startupFuture.completeExceptionally(e);
        }
      } catch (Exception e) {
        LOG.error("Error starting MCP HTTP server", e);
        serverReady = false;
        if (!startupFuture.isDone()) {
          startupFuture.completeExceptionally(e);
        }
      }
    });

    return startupFuture;
  }

  public CompletableFuture<Void> stopServer() {
    if (httpServer == null) {
      LOG.info("HTTP server is not running");
      return CompletableFuture.completedFuture(null);
    }

    return CompletableFuture.runAsync(() -> {
      LOG.info("Stopping MCP HTTP server...");
      serverReady = false;
      
      try {
        httpServer.stop();
        httpServer = null;
        LOG.info("MCP HTTP server stopped successfully");
      } catch (Exception e) {
        LOG.error("Error stopping HTTP server", e);
      }
    });
  }

  public boolean isServerReady() {
    return httpServer != null && httpServer.isRunning() && serverReady;
  }

  public String getServerUrl() {
    return "http://" + host + ":" + port + MCP_ENDPOINT;
  }

  @Override
  public String toString() {
    return "HttpServerTransportProvider{" +
        "url=" + getServerUrl() +
        ", ready=" + isServerReady() +
        '}';
  }

}
