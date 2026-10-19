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
package org.sonarsource.sonarqube.mcp.http;

import java.net.URISyntaxException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import jakarta.annotation.Nullable;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.util.Timeout;

class HttpClientAdapter implements HttpClient {

  private static final String ORIGIN_HEADER = "Origin";
  private static final String HOST_HEADER = "Host";
  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String X_API_KEY_HEADER = "x-api-key";
  private static final String LOCALHOST = "localhost";
  private static final String LOCALHOST_ORIGIN = "http://localhost";
  private final CloseableHttpAsyncClient apacheClient;
  private final RequestConfig defaultRequestConfig;
  private final String token;
  private final boolean isBridgeClient;
  @Nullable
  private final String apiKey;

  HttpClientAdapter(CloseableHttpAsyncClient apacheClient, @Nullable String sonarqubeCloudToken, boolean isBridgeClient, RequestConfig defaultRequestConfig) {
    this(apacheClient, sonarqubeCloudToken, isBridgeClient, defaultRequestConfig, null);
  }

  HttpClientAdapter(CloseableHttpAsyncClient apacheClient, @Nullable String sonarqubeCloudToken, boolean isBridgeClient, RequestConfig defaultRequestConfig,
    @Nullable String apiKey) {
    this.apacheClient = apacheClient;
    this.defaultRequestConfig = defaultRequestConfig;
    this.token = sonarqubeCloudToken;
    this.isBridgeClient = isBridgeClient;
    this.apiKey = apiKey;
  }

  @Override
  public CompletableFuture<Response> postAsync(String url, String contentType, String body) {
    return executeAsync(buildPostRequest(url, contentType, body), token);
  }

  @Override
  public CompletableFuture<Response> postAsync(String url, String contentType, String body, Duration timeout) {
    var request = buildPostRequest(url, contentType, body);
    // Long-running operations also need a response timeout beyond the client's default.
    request.setConfig(RequestConfig.copy(defaultRequestConfig).setResponseTimeout(Timeout.of(timeout)).build());
    return executeAsync(request, token);
  }

  private SimpleHttpRequest buildPostRequest(String url, String contentType, String body) {
    var requestBuilder = SimpleRequestBuilder.post(url)
      .setBody(body, ContentType.parse(contentType));

    if (isBridgeClient) {
      requestBuilder
        .addHeader(HOST_HEADER, LOCALHOST)
        .addHeader(ORIGIN_HEADER, LOCALHOST_ORIGIN);
    }
    
    return requestBuilder.build();
  }

  @Override
  public CompletableFuture<Response> getAsync(String url) {
    var requestBuilder = SimpleRequestBuilder.get(url);

    if (isBridgeClient) {
      requestBuilder
        .addHeader(HOST_HEADER, LOCALHOST)
        .addHeader(ORIGIN_HEADER, LOCALHOST_ORIGIN);
    }
    
    return executeAsync(requestBuilder.build(), token);
  }

  @Override
  public CompletableFuture<Response> getAsyncAnonymous(String url) {
    var requestBuilder = SimpleRequestBuilder.get(url);

    if (isBridgeClient) {
      requestBuilder
        .addHeader(HOST_HEADER, LOCALHOST)
        .addHeader(ORIGIN_HEADER, LOCALHOST_ORIGIN);
    }
    
    return executeAsync(requestBuilder.build(), null);
  }

  private class CompletableFutureWrappingFuture extends CompletableFuture<Response> {

    private final Future<SimpleHttpResponse> wrapped;

    private CompletableFutureWrappingFuture(SimpleHttpRequest httpRequest) {
      this.wrapped = apacheClient.execute(httpRequest, new FutureCallback<>() {
        @Override
        public void completed(SimpleHttpResponse result) {
          // Complete on this callback thread. completeAsync() would queue the completion on
          // ForkJoinPool.commonPool, which can stay blocked for the life of the HTTP server.
          String uri;
          try {
            uri = httpRequest.getUri().toString();
          } catch (URISyntaxException e) {
            uri = httpRequest.getRequestUri();
          }
          HttpClientAdapter.CompletableFutureWrappingFuture.this.complete(new HttpResponse(uri, result));
        }

        @Override
        public void failed(Exception ex) {
          HttpClientAdapter.CompletableFutureWrappingFuture.this.completeExceptionally(ex);
        }

        @Override
        public void cancelled() {
          HttpClientAdapter.CompletableFutureWrappingFuture.this.cancel();
        }
      });
    }

    private void cancel() {
      super.cancel(true);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return wrapped.cancel(mayInterruptIfRunning);
    }
  }

  private CompletableFuture<Response> executeAsync(SimpleHttpRequest httpRequest, @Nullable String tokenToUse) {
    try {
      if (tokenToUse != null) {
        httpRequest.setHeader(AUTHORIZATION_HEADER, bearer(tokenToUse));
      }
      if (apiKey != null) {
        httpRequest.setHeader(X_API_KEY_HEADER, apiKey);
      }
      return new CompletableFutureWrappingFuture(httpRequest);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to execute request: " + e.getMessage(), e);
    }
  }

  private static String bearer(String token) {
    return String.format("Bearer %s", token);
  }

}
