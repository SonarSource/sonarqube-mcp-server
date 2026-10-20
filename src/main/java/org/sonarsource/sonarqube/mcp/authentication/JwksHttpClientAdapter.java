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
package org.sonarsource.sonarqube.mcp.authentication;

import com.auth0.jwk.JwksHttpClient;
import com.auth0.jwk.JwksHttpResponse;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.sonarsource.sonarqube.mcp.http.HttpClient;

final class JwksHttpClientAdapter implements JwksHttpClient {
  private static final long REQUEST_TIMEOUT_SECONDS = 5;

  private final HttpClient client;

  JwksHttpClientAdapter(HttpClient client) {
    this.client = client;
  }

  @Override
  public JwksHttpResponse fetch(URL url) throws IOException {
    var request = client.getAsyncAnonymousWithoutRedirects(url.toString());
    try (var response = request.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
      if (!response.isSuccessful()) {
        throw new IOException("JWKS request failed");
      }
      return new JwksHttpResponse(response.bodyAsString());
    } catch (InterruptedException e) {
      request.cancel(true);
      Thread.currentThread().interrupt();
      throw new IOException("JWKS request interrupted", e);
    } catch (ExecutionException | TimeoutException e) {
      request.cancel(true);
      throw new IOException("JWKS request failed", e);
    }
  }
}
