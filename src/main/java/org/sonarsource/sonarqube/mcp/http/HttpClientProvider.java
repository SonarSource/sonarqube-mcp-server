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

import java.net.ProxySelector;
import javax.net.ssl.SSLContext;
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
    this.httpClient = HttpAsyncClients.custom()
      .setConnectionManager(asyncConnectionManager)
      .addResponseInterceptorFirst(new RedirectInterceptor())
      .setUserAgent(userAgent)
      .setRoutePlanner(new SystemDefaultRoutePlanner(ProxySelector.getDefault()))
      .setDefaultCredentialsProvider(new SystemDefaultCredentialsProvider())
      .build();

    httpClient.start();
  }

  public HttpClient getHttpClient(String sonarqubeCloudToken) {
    return new HttpClientAdapter(httpClient, sonarqubeCloudToken);
  }

  public HttpClient getHttpClientForBridge() {
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

}
