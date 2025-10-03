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
package org.sonarsource.sonarqube.mcp.http;

import javax.net.ssl.SSLContext;
import nl.altindag.ssl.SSLFactory;
import org.apache.commons.lang3.SystemUtils;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.auth.SystemDefaultCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.http2.HttpVersionPolicy;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.client5.http.config.RequestConfig;
import java.net.URL;
import java.util.concurrent.TimeUnit;

public class HttpClientProvider {

  private final CloseableHttpAsyncClient httpClient;

  public HttpClientProvider(String userAgent) {
    var asyncConnectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
      .setTlsStrategy(new DefaultClientTlsStrategy(configureSsl()))
      .setDefaultTlsConfig(TlsConfig.custom()
        // Force HTTP/1 since we know SQ/SC don't support HTTP/2 ATM
        .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1)
        .build())
      .build();
    
    var httpClientBuilder = HttpAsyncClients.custom()
      .setConnectionManager(asyncConnectionManager)
      .addResponseInterceptorFirst(new RedirectInterceptor())
      .setUserAgent(userAgent)
      .setDefaultCredentialsProvider(new SystemDefaultCredentialsProvider());
    
    // Configure proxy settings
    var proxy = configureProxy();
    if (proxy != null) {
      var requestConfig = RequestConfig.custom()
        .setProxy(proxy)
        .setConnectTimeout(30, TimeUnit.SECONDS)
        .setResponseTimeout(30, TimeUnit.SECONDS)
        .build();
      httpClientBuilder.setDefaultRequestConfig(requestConfig);
    }
    
    this.httpClient = httpClientBuilder.build();

    httpClient.start();
  }

  public HttpClient getHttpClient(String sonarqubeCloudToken) {
    return new HttpClientAdapter(httpClient, sonarqubeCloudToken);
  }

  public HttpClient getHttpClientWithoutToken() {
    return new HttpClientAdapter(httpClient);
  }

  public void shutdown() {
    httpClient.close(CloseMode.IMMEDIATE);
  }

  private static SSLContext configureSsl() {
    var sslFactoryBuilder = SSLFactory.builder()
      .withDefaultTrustMaterial();
    if (!SystemUtils.IS_OS_WINDOWS) {
      sslFactoryBuilder.withSystemTrustMaterial();
    }
    return sslFactoryBuilder.build().getSslContext();
  }
  
  private static HttpHost configureProxy() {
    // Check environment variables first
    String httpsProxy = System.getenv("HTTPS_PROXY");
    String httpProxy = System.getenv("HTTP_PROXY");
    String socksProxy = System.getenv("SOCKS_PROXY");
    
    // Prefer HTTPS_PROXY for HTTPS connections
    String proxyUrl = httpsProxy != null ? httpsProxy : httpProxy;
    
    if (proxyUrl != null && !proxyUrl.isEmpty()) {
      try {
        URL url = new URL(proxyUrl);
        // For Apache HttpClient 5, always use "http" as the protocol for HTTP proxies
        return new HttpHost("http", url.getHost(), url.getPort() > 0 ? url.getPort() : 8080);
      } catch (Exception e) {
        // Log error but continue trying other proxy configurations
      }
    }
    
    // Check for SOCKS proxy (treated as HTTP proxy for Apache HttpClient compatibility)
    if (socksProxy != null && !socksProxy.isEmpty()) {
      try {
        String[] parts = socksProxy.split(":");
        if (parts.length == 2) {
          String host = parts[0];
          int port = Integer.parseInt(parts[1]);
          return new HttpHost("http", host, port);
        }
      } catch (Exception e) {
        // Log error but continue trying other proxy configurations
      }
    }
    
    // Fall back to Java system properties
    String proxyHost = System.getProperty("https.proxyHost");
    String proxyPortStr = System.getProperty("https.proxyPort");
    
    if (proxyHost != null && !proxyHost.isEmpty()) {
      try {
        int proxyPort = proxyPortStr != null ? Integer.parseInt(proxyPortStr) : 8080;
        return new HttpHost("http", proxyHost, proxyPort);
      } catch (NumberFormatException e) {
        // Log error but continue
      }
    }
    
    return null;
  }

}
