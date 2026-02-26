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

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import jakarta.servlet.DispatcherType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.SSLContext;
import nl.altindag.ssl.SSLFactory;
import org.apache.commons.lang3.SystemUtils;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import javax.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.authentication.AuthMode;
import org.sonarsource.sonarqube.mcp.authentication.AuthenticationFilter;
import org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfiguration;
import org.sonarsource.sonarqube.mcp.log.McpLogger;

/**
 * HTTP transport for the MCP server using the stateless servlet transport.
 * Each POST request is handled independently — no session state is maintained,
 * enabling horizontal scaling without sticky sessions.
 */
public class HttpServerTransportProvider {

  private static final McpLogger LOG = McpLogger.getInstance();
  private static final String MCP_ENDPOINT = "/mcp";
  public static final String CONTEXT_TOKEN_KEY = "sonarqube-token";
  public static final String CONTEXT_ORG_KEY = "sonarqube-org";

  private final int port;
  private final String host;
  private final AuthMode authMode;
  private final boolean isSonarCloud;
  @Nullable
  private final String serverOrg;
  private final boolean httpsEnabled;
  private final Path httpsKeystorePath;
  private final String httpsKeystorePassword;
  private final String httpsKeystoreType;
  private final Path httpsTruststorePath;
  private final String httpsTruststorePassword;
  private final String httpsTruststoreType;
  private final HttpServletStatelessServerTransport mcpTransportProvider;
  private Server httpServer;

  /**
   * Create HTTP transport provider with custom host binding and authentication.
   * 
   * @param port HTTP port (e.g., 8080 for HTTP, 8443 for HTTPS)
   * @param host Host to bind to (127.0.0.1 for localhost, 0.0.0.0 for all interfaces)
   * @param authMode Authentication mode (e.g., TOKEN, OAUTH)
   * @param isSonarCloud Whether this server connects to SonarQube Cloud
   * @param serverOrg Server-level organization key (null when not configured at startup)
   * @param httpsEnabled Whether to enable HTTPS/TLS
   * @param httpsKeystorePath Path to keystore file (contains server certificate and private key)
   * @param httpsKeystorePassword Keystore password
   * @param httpsKeystoreType Keystore type (e.g., PKCS12, JKS)
   * @param httpsTruststorePath Path to truststore file (optional, contains trusted CA certificates)
   * @param httpsTruststorePassword Truststore password (optional)
   * @param httpsTruststoreType Truststore type (optional)
   */
  public HttpServerTransportProvider(int port, String host, AuthMode authMode, boolean isSonarCloud, @Nullable String serverOrg,
    boolean httpsEnabled, Path httpsKeystorePath, String httpsKeystorePassword, String httpsKeystoreType,
    Path httpsTruststorePath, String httpsTruststorePassword, String httpsTruststoreType) {
    this.port = port;
    this.host = host;
    this.authMode = authMode;
    this.isSonarCloud = isSonarCloud;
    this.serverOrg = serverOrg;
    this.httpsEnabled = httpsEnabled;
    this.httpsKeystorePath = httpsKeystorePath;
    this.httpsKeystorePassword = httpsKeystorePassword;
    this.httpsKeystoreType = httpsKeystoreType;
    this.httpsTruststorePath = httpsTruststorePath;
    this.httpsTruststorePassword = httpsTruststorePassword;
    this.httpsTruststoreType = httpsTruststoreType;

    this.mcpTransportProvider = HttpServletStatelessServerTransport.builder()
      .messageEndpoint(MCP_ENDPOINT)
      .jsonMapper(McpJsonMappers.DEFAULT)
      .contextExtractor(request -> {
        var token = request.getHeader(McpServerLaunchConfiguration.SONARQUBE_TOKEN);
        var org = request.getHeader(McpServerLaunchConfiguration.SONARQUBE_ORG);
        var contextBuilder = new HashMap<String, Object>();
        contextBuilder.put(CONTEXT_TOKEN_KEY, token != null ? token : "");
        if (org != null && !org.isBlank()) {
          contextBuilder.put(CONTEXT_ORG_KEY, org);
        }
        return McpTransportContext.create(contextBuilder);
      })
      .build();

    var protocol = httpsEnabled ? "https" : "http";
    LOG.info("Created " + protocol.toUpperCase(Locale.getDefault()) + " transport provider for "
      + protocol + "://" + host + ":" + port + MCP_ENDPOINT + " with authentication: " + authMode);

    // Warn about security risk when binding to all interfaces
    if ("0.0.0.0".equals(host)) {
      LOG.warn("SECURITY WARNING: MCP HTTP server is configured to bind to all network interfaces (0.0.0.0). " +
        "This exposes the server to your entire network. " +
        "For local development, consider using 127.0.0.1 instead.");
    }

    // Warn about HTTP without HTTPS
    if (!httpsEnabled) {
      LOG.warn("SECURITY WARNING: MCP server is using HTTP without SSL/TLS encryption. " +
        "Tokens and data will be transmitted in plain text. " +
        "For production use, consider enabling HTTPS with SONARQUBE_TRANSPORT=https.");
    }
  }

  public HttpServletStatelessServerTransport getTransportProvider() {
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

    var servletContextHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
    servletContextHandler.setContextPath("/");

    var securityFilter = new FilterHolder(new McpSecurityFilter(host));
    servletContextHandler.addFilter(securityFilter, "/*", EnumSet.of(DispatcherType.REQUEST));

    var authFilter = new FilterHolder(new AuthenticationFilter(authMode, isSonarCloud, serverOrg));
    servletContextHandler.addFilter(authFilter, "/*", EnumSet.of(DispatcherType.REQUEST));

    var servletHolder = new ServletHolder(mcpTransportProvider);
    servletHolder.setAsyncSupported(true);
    servletContextHandler.addServlet(servletHolder, "/*");

    // Create Jetty server
    httpServer = new Server();
    ServerConnector connector;

    if (httpsEnabled) {
      // Configure HTTPS with SSL/TLS
      var sslContextFactory = new SslContextFactory.Server();
      var sslContext = configureSsl(httpsKeystorePath, httpsKeystorePassword, httpsKeystoreType,
        httpsTruststorePath, httpsTruststorePassword, httpsTruststoreType);
      sslContextFactory.setSslContext(sslContext);
      connector = new ServerConnector(httpServer, sslContextFactory);
    } else {
      // Plain HTTP connector
      connector = new ServerConnector(httpServer);
    }

    connector.setHost(host);
    connector.setPort(port);
    httpServer.addConnector(connector);
    httpServer.setHandler(servletContextHandler);

    CompletableFuture.runAsync(() -> {
      try {
        httpServer.start();
        var protocol = httpsEnabled ? "https" : "http";
        LOG.info("MCP " + protocol.toUpperCase(Locale.getDefault()) + " server started successfully on " + protocol + "://" + host + ":" + port + MCP_ENDPOINT);
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

      try {
        httpServer.stop();
        httpServer = null;
        LOG.info("MCP HTTP server stopped successfully");
      } catch (Exception e) {
        LOG.error("Error stopping HTTP server", e);
      }
    });
  }

  public String getServerUrl() {
    var protocol = httpsEnabled ? "https" : "http";
    return protocol + "://" + host + ":" + port + MCP_ENDPOINT;
  }

  /**
   * @param keystorePath Path to keystore file (server certificate and private key)
   * @param keystorePassword Keystore password
   * @param keystoreType Keystore type (e.g., PKCS12, JKS)
   * @param truststorePath Optional path to truststore file (trusted CA certificates)
   * @param truststorePassword Optional truststore password
   * @param truststoreType Optional truststore type
   * @return Configured SSLContext
   */
  private static SSLContext configureSsl(Path keystorePath, String keystorePassword, String keystoreType,
    Path truststorePath, String truststorePassword, String truststoreType) {

    var sslFactoryBuilder = SSLFactory.builder()
      .withDefaultTrustMaterial();

    if (!SystemUtils.IS_OS_WINDOWS) {
      sslFactoryBuilder.withSystemTrustMaterial();
    }

    if (Files.exists(keystorePath)) {
      LOG.info("Configuring SSL with keystore: " + keystorePath + " (type: " + keystoreType + ")");
      sslFactoryBuilder.withIdentityMaterial(keystorePath, keystorePassword.toCharArray(), keystoreType);
    } else {
      LOG.warn("HTTPS enabled but keystore file not found at: " + keystorePath);
      LOG.warn("To use HTTPS, create a keystore file or the server will use default JVM certificates");
    }

    if (Files.exists(truststorePath)) {
      LOG.info("Configuring SSL with truststore: " + truststorePath + " (type: " + truststoreType + ")");
      sslFactoryBuilder.withInflatableTrustMaterial(truststorePath, truststorePassword.toCharArray(), truststoreType, null);
    }

    return sslFactoryBuilder.build().getSslContext();
  }

}
