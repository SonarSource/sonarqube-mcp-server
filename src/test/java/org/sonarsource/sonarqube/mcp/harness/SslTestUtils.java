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
package org.sonarsource.sonarqube.mcp.harness;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public final class SslTestUtils {

  private SslTestUtils() {
    // utility class
  }

  public static SSLContext trustAllSslContext() {
    try {
      var sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, new TrustManager[] {new TrustAllManager()}, new SecureRandom());
      return sslContext;
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to create trust-all SSL context", e);
    }
  }

  public static int getResponseCodeTrustingAllCertificates(String url, Duration connectTimeout, Duration readTimeout)
    throws IOException {
    var connection = (HttpsURLConnection) URI.create(url).toURL().openConnection();
    connection.setSSLSocketFactory(trustAllSslContext().getSocketFactory());
    connection.setHostnameVerifier((hostname, session) -> true);
    connection.setConnectTimeout(Math.toIntExact(connectTimeout.toMillis()));
    connection.setReadTimeout(Math.toIntExact(readTimeout.toMillis()));
    connection.setRequestMethod("GET");
    try {
      return connection.getResponseCode();
    } finally {
      connection.disconnect();
    }
  }

  private static final class TrustAllManager implements X509TrustManager {
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {
      // Test client accepts any server certificate
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) {
      // Test client accepts any server certificate
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
    }
  }

}
