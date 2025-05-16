/*
 * Sonar MCP Server
 * Copyright (C) 2025 SonarSource
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.mcp.http;

import javax.annotation.Nullable;
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
      .setDefaultCredentialsProvider(new SystemDefaultCredentialsProvider())
      .build();

    httpClient.start();
  }

  public HttpClient getHttpClient(@Nullable String sonarqubeCloudToken) {
    return new HttpClientAdapter(httpClient, sonarqubeCloudToken);
  }

  public HttpClient getHttpClient() {
    return getHttpClient(null);
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
