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
package org.sonarsource.sonarqube.mcp.http;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import nl.altindag.ssl.SSLFactory;
import org.apache.commons.lang3.SystemUtils;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.auth.SystemDefaultCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.sonarsource.sonarqube.mcp.log.McpLogger;

public class HttpClientProvider {

  private static final McpLogger LOG = McpLogger.getInstance();
  private final CloseableHttpAsyncClient httpClient;
  private final String userAgent;
  private final String sslProtocol;
  private final int trustedCertificates;
  private final String proxySelector;

  public HttpClientProvider(String userAgent) {
    this.userAgent = userAgent;
    var sslFactoryBuilder = SSLFactory.builder()
      .withDefaultTrustMaterial();
    if (!SystemUtils.IS_OS_WINDOWS) {
      sslFactoryBuilder.withSystemTrustMaterial();
    }
    var sslFactory = sslFactoryBuilder.build();
    var sslContext = sslFactory.getSslContext();
    this.sslProtocol = sslContext.getProtocol();
    this.trustedCertificates = sslFactory.getTrustedCertificates().size();

    var asyncConnectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
      .setTlsStrategy(new DefaultClientTlsStrategy(sslContext))
      .setDefaultTlsConfig(TlsConfig.custom()
        // Force HTTP/1 since we know SQ/SC don't support HTTP/2 ATM
        .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1)
        .build())
      .build();

    var defaultProxySelector = ProxySelector.getDefault();
    this.proxySelector = defaultProxySelector != null ? defaultProxySelector.getClass().getName() : "none";

    var httpClientBuilder = HttpAsyncClients.custom()
      .setConnectionManager(asyncConnectionManager)
      .addResponseInterceptorFirst(new RedirectInterceptor())
      .setUserAgent(userAgent)
      .setDefaultCredentialsProvider(new SystemDefaultCredentialsProvider());
    if (defaultProxySelector != null) {
      httpClientBuilder.setRoutePlanner(new SystemDefaultRoutePlanner(defaultProxySelector));
    }
    var socksConfig = buildSocksProxyConfig();
    if (socksConfig != null) {
      httpClientBuilder.setIOReactorConfig(socksConfig);
    }
    this.httpClient = httpClientBuilder.build();

    httpClient.start();
  }

  private static IOReactorConfig buildSocksProxyConfig() {
    var socksHost = System.getProperty("socksProxyHost");
    if (socksHost == null) {
      return null;
    }
    var socksPort = Integer.parseInt(System.getProperty("socksProxyPort", "1080"));
    var builder = IOReactorConfig.custom()
      .setSocksProxyAddress(new InetSocketAddress(socksHost, socksPort));
    var socksUser = System.getProperty("java.net.socks.username");
    if (socksUser != null) {
      builder.setSocksProxyUsername(socksUser);
    }
    var socksPassword = System.getProperty("java.net.socks.password");
    if (socksPassword != null) {
      builder.setSocksProxyPassword(socksPassword);
    }
    return builder.build();
  }

  public HttpClient getHttpClient(String sonarqubeCloudToken) {
    return new HttpClientAdapter(httpClient, sonarqubeCloudToken, false);
  }

  public HttpClient getAnonymousHttpClient() {
    return new HttpClientAdapter(httpClient, null, false);
  }

  /**
   * Creates an HTTP client for SonarQube for IDE bridge communication.
   * Bridge client adds special Host and Origin headers for localhost communication.
   */
  public HttpClient getHttpClientForBridge() {
    return new HttpClientAdapter(httpClient, null, true);
  }

  /**
   * Creates an HTTP client for analytics event reporting.
   * Sends an x-api-key header on every request.
   */
  public HttpClient getHttpClientForAnalytics(String apiKey) {
    return new HttpClientAdapter(httpClient, null, false, apiKey);
  }

  public void shutdown() {
    httpClient.close(CloseMode.IMMEDIATE);
  }

  public void logConnectionSettings() {
    if (!McpLogger.isDebugEnabled()) {
      return;
    }
    LOG.debug("SSL/TLS - OS: " + System.getProperty("os.name"));
    LOG.debug("SSL/TLS configured - protocol: " + sslProtocol
      + ", trusted certificates: " + trustedCertificates);
    LOG.debug("Proxy selector: " + proxySelector);
    var httpProxy = System.getProperty("http.proxyHost");
    var httpsProxy = System.getProperty("https.proxyHost");
    var socksProxy = System.getProperty("socksProxyHost");
    if (httpProxy != null) {
      LOG.debug("HTTP proxy: " + httpProxy + ":" + System.getProperty("http.proxyPort", "80"));
    }
    if (httpsProxy != null) {
      LOG.debug("HTTPS proxy: " + httpsProxy + ":" + System.getProperty("https.proxyPort", "443"));
    }
    if (socksProxy != null) {
      LOG.debug("SOCKS proxy: " + socksProxy + ":" + System.getProperty("socksProxyPort", "1080"));
    }
    if (httpProxy == null && httpsProxy == null && socksProxy == null) {
      LOG.debug("No proxy system properties configured");
    }
    LOG.debug("HTTP client user agent: " + userAgent);
  }

}
