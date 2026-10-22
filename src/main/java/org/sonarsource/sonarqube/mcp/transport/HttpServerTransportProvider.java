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
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import jakarta.annotation.Nullable;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.SSLContext;
import nl.altindag.ssl.SSLFactory;
import org.apache.commons.lang3.SystemUtils;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.sonarsource.sonarqube.mcp.authentication.AuthenticationFilter;
import org.sonarsource.sonarqube.mcp.authentication.AuthenticationFilter.OAuthSupport;
import org.sonarsource.sonarqube.mcp.authentication.HttpRequestAuthentication;
import org.sonarsource.sonarqube.mcp.authentication.OAuthAuthenticationException;
import org.sonarsource.sonarqube.mcp.authentication.OAuthProtectedResourceMetadata;
import org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfiguration;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;
import reactor.core.publisher.Mono;

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
  public static final String CONTEXT_TOOLSETS_KEY = "sonarqube-toolsets";
  public static final String CONTEXT_READ_ONLY_KEY = "sonarqube-read-only";

  private final int port;
  private final String host;
  @Nullable
  private final String serverOrg;
  private final boolean isSonarQubeCloud;
  @Nullable
  private final OAuthSupport oauth;
  private final boolean httpsEnabled;
  private final Path httpsKeystorePath;
  private final String httpsKeystorePassword;
  private final String httpsKeystoreType;
  private final Path httpsTruststorePath;
  private final String httpsTruststorePassword;
  private final String httpsTruststoreType;
  private final List<String> allowedOrigins;
  private final String appVersion;
  private final HttpServletStatelessServerTransport mcpTransportProvider;
  private Server httpServer;

  public HttpServerTransportProvider(HttpTransportSettings settings, @Nullable OAuthSupport oauth) {
    this.port = settings.port();
    this.host = settings.host();
    this.isSonarQubeCloud = settings.isSonarQubeCloud();
    this.serverOrg = isSonarQubeCloud ? settings.serverOrg() : null;
    if (this.serverOrg != null) {
      LOG.warn("Configuring SONARQUBE_ORG on an HTTP server is deprecated. Remove it and use the optional SONARQUBE_ORG request header or tool organization argument instead.");
    }
    this.oauth = oauth;
    this.httpsEnabled = settings.tls().enabled();
    this.httpsKeystorePath = settings.tls().keystorePath();
    this.httpsKeystorePassword = settings.tls().keystorePassword();
    this.httpsKeystoreType = settings.tls().keystoreType();
    this.httpsTruststorePath = settings.tls().truststorePath();
    this.httpsTruststorePassword = settings.tls().truststorePassword();
    this.httpsTruststoreType = settings.tls().truststoreType();
    this.allowedOrigins = List.copyOf(settings.requests().allowedOrigins());
    this.appVersion = settings.requests().appVersion();

    this.mcpTransportProvider = HttpServletStatelessServerTransport.builder()
      .messageEndpoint(MCP_ENDPOINT)
      .jsonMapper(McpJsonMappers.DEFAULT)
      .contextExtractor(this::extractTransportContext)
      .build();

    var protocol = httpsEnabled ? "https" : "http";
    LOG.info("Created " + protocol.toUpperCase(Locale.getDefault()) + " transport provider for "
      + protocol + "://" + host + ":" + port + MCP_ENDPOINT);

    // Warn about security risk when binding to all interfaces outside a container.
    // In containers, 0.0.0.0 is required for port mapping to work; the host-side flag controls exposure.
    // Outside a container (e.g. JAR), 0.0.0.0 exposes the server on all host interfaces and enables DNS rebinding attacks.
    if ("0.0.0.0".equals(host) && !settings.requests().isRunningInContainer()) {
      LOG.warn("SECURITY WARNING: MCP HTTP server is configured to bind to all network interfaces (0.0.0.0). " +
        "This exposes the server to your entire network and is susceptible to DNS rebinding attacks. " +
        "For local use, consider using 127.0.0.1 instead.");
    }

    // Warn about HTTP without HTTPS
    if (!httpsEnabled) {
      LOG.warn("SECURITY WARNING: MCP server is using HTTP without SSL/TLS encryption. " +
        "Tokens and data will be transmitted in plain text. " +
        "For production use, consider enabling HTTPS with SONARQUBE_TRANSPORT=https.");
    }
  }

  private McpTransportContext extractTransportContext(HttpServletRequest request) {
    try {
      var toolsets = request.getHeader(McpServerLaunchConfiguration.SONARQUBE_TOOLSETS);
      var readOnly = request.getHeader(McpServerLaunchConfiguration.SONARQUBE_READ_ONLY);
      var contextBuilder = createAuthenticationContext(request);
      if (isSonarQubeCloud && !contextBuilder.containsKey(OAuthScopeContext.CONTEXT_KEY)) {
        var org = serverOrg != null ? serverOrg : request.getHeader(McpServerLaunchConfiguration.SONARQUBE_ORG);
        if (org != null && !org.isBlank()) {
          contextBuilder.put(CONTEXT_ORG_KEY, org.trim());
        }
      }
      if (toolsets != null && !toolsets.isBlank()) {
        contextBuilder.put(CONTEXT_TOOLSETS_KEY, ToolCategory.parseCategories(toolsets.trim()));
      }
      if (readOnly != null && !readOnly.isBlank()) {
        contextBuilder.put(CONTEXT_READ_ONLY_KEY, Boolean.parseBoolean(readOnly.trim()));
      }
      return McpTransportContext.create(contextBuilder);
    } catch (Exception e) {
      LOG.error("Failed to extract MCP transport context from request for URI '" + request.getRequestURI() + "'", e);
      throw e;
    }
  }

  private Map<String, Object> createAuthenticationContext(HttpServletRequest request) {
    if (!(request.getAttribute(AuthenticationFilter.AUTHENTICATION_ATTRIBUTE) instanceof HttpRequestAuthentication(var token, var scopes))
      || (oauth == null && scopes != null)) {
      throw OAuthAuthenticationException.unauthorized();
    }
    var contextBuilder = new HashMap<String, Object>();
    contextBuilder.put(CONTEXT_TOKEN_KEY, token);
    if (scopes != null) {
      contextBuilder.put(OAuthScopeContext.CONTEXT_KEY, scopes);
    }
    return contextBuilder;
  }

  /**
   * Returns transport preloaded with {@code enabledTools}. Pass it to {@code McpServer.sync(...).build()}:
   * when the SDK calls {@code setMcpHandler}, the transport immediately installs a {@link PerRequestToolFilteringHandler} wrapping the SDK handler.
   */
  public McpStatelessServerTransport getFilteringTransport(List<Tool> enabledTools) {
    return new McpStatelessServerTransport() {
      private final List<Tool> tools = List.copyOf(enabledTools);

      @Override
      public void setMcpHandler(McpStatelessServerHandler handler) {
        mcpTransportProvider.setMcpHandler(new PerRequestToolFilteringHandler(handler, tools));
      }

      @Override
      public Mono<Void> closeGracefully() {
        return mcpTransportProvider.closeGracefully();
      }

      @Override
      public List<String> protocolVersions() {
        return mcpTransportProvider.protocolVersions();
      }
    };
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

    var errorFilter = new FilterHolder(new ErrorHandlingFilter());
    servletContextHandler.addFilter(errorFilter, "/*", EnumSet.of(DispatcherType.REQUEST));

    var securityFilter = new FilterHolder(new McpSecurityFilter(host, allowedOrigins, appVersion));
    servletContextHandler.addFilter(securityFilter, "/*", EnumSet.of(DispatcherType.REQUEST));

    var authFilter = new FilterHolder(new AuthenticationFilter(serverOrg, oauth));
    servletContextHandler.addFilter(authFilter, "/*", EnumSet.of(DispatcherType.REQUEST));

    if (oauth != null && oauth.metadata().isSelfHosted()) {
      servletContextHandler.addServlet(new ServletHolder(oauth.metadata()), OAuthProtectedResourceMetadata.PATH);
    }

    var servletHolder = new ServletHolder(mcpTransportProvider);
    servletHolder.setAsyncSupported(true);
    servletContextHandler.addServlet(servletHolder, "/*");

    // Create Jetty server
    httpServer = new Server();
    ServerConnector connector;

    if (httpsEnabled) {
      var sslContextFactory = new SslContextFactory.Server();
      var sslContext = configureSsl(httpsKeystorePath, httpsKeystorePassword, httpsKeystoreType,
        httpsTruststorePath, httpsTruststorePassword, httpsTruststoreType);
      sslContextFactory.setSslContext(sslContext);
      connector = createHttpsConnector(httpServer, sslContextFactory);
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
   * Jetty 12 rejects requests whose Host/SNI is not in the certificate (HTTP 400 Invalid SNI).
   * Kubernetes HTTPS probes connect via the pod IP, which will not match typical service DNS SANs.
   */
  private static ServerConnector createHttpsConnector(Server server, SslContextFactory.Server sslContextFactory) {
    var httpsConfig = new HttpConfiguration();
    var secureCustomizer = new SecureRequestCustomizer();
    secureCustomizer.setSniRequired(false);
    secureCustomizer.setSniHostCheck(false);
    httpsConfig.addCustomizer(secureCustomizer);
    return new ServerConnector(server, sslContextFactory, new HttpConnectionFactory(httpsConfig));
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
      try {
        sslFactoryBuilder.withSystemTrustMaterial();
      } catch (Exception e) {
        LOG.warn("Could not load system trust material, falling back to JDK defaults: " + e.getMessage());
      }
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
