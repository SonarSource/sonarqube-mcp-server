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
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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

  private static final ScheduledThreadPoolExecutor DEADLINES = createDeadlineScheduler();
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
  private final Duration operationTimeout;
  @Nullable
  private final String apiKey;

  static ScheduledThreadPoolExecutor createDeadlineScheduler() {
    var scheduler = new ScheduledThreadPoolExecutor(1, runnable -> {
      var thread = new Thread(runnable, "sonarqube-mcp-http-deadlines");
      thread.setDaemon(true);
      return thread;
    });
    scheduler.setRemoveOnCancelPolicy(true);
    return scheduler;
  }

  HttpClientAdapter(CloseableHttpAsyncClient apacheClient, @Nullable String sonarqubeCloudToken, boolean isBridgeClient, Duration operationTimeout,
    RequestConfig defaultRequestConfig, @Nullable String apiKey) {
    this.apacheClient = apacheClient;
    this.defaultRequestConfig = defaultRequestConfig;
    this.token = sonarqubeCloudToken;
    this.isBridgeClient = isBridgeClient;
    this.operationTimeout = operationTimeout;
    this.apiKey = apiKey;
  }

  @Override
  public CompletableFuture<Response> postAsync(String url, String contentType, String body) {
    return executeAsync(buildPostRequest(url, contentType, body), token, operationTimeout);
  }

  @Override
  public CompletableFuture<Response> postAsync(String url, String contentType, String body, Duration timeout) {
    var request = buildPostRequest(url, contentType, body);
    // Long-running operations also need a response timeout beyond the client's default.
    request.setConfig(RequestConfig.copy(defaultRequestConfig).setResponseTimeout(Timeout.of(timeout)).build());
    return executeAsync(request, token, timeout);
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
    return getAsync(url, operationTimeout);
  }

  @Override
  public CompletableFuture<Response> getAsync(String url, Duration timeout) {
    return executeAsync(buildGetRequest(url), token, timeout);
  }

  @Override
  public CompletableFuture<Response> patchAsync(String url, String contentType, String body) {
    var requestBuilder = SimpleRequestBuilder.patch(url)
      .setBody(body, ContentType.parse(contentType));

    if (isBridgeClient) {
      requestBuilder
        .addHeader(HOST_HEADER, LOCALHOST)
        .addHeader(ORIGIN_HEADER, LOCALHOST_ORIGIN);
    }

    return executeAsync(requestBuilder.build(), token, operationTimeout);
  }

  @Override
  public CompletableFuture<Response> deleteAsync(String url) {
    var requestBuilder = SimpleRequestBuilder.delete(url);
    if (isBridgeClient) {
      requestBuilder.addHeader(HOST_HEADER, LOCALHOST).addHeader(ORIGIN_HEADER, LOCALHOST_ORIGIN);
    }
    return executeAsync(requestBuilder.build(), token, operationTimeout);
  }

  @Override
  public CompletableFuture<Response> getAsyncAnonymous(String url) {
    return getAsyncAnonymous(url, operationTimeout);
  }

  @Override
  public CompletableFuture<Response> getAsyncAnonymousWithoutRedirects(String url) {
    var request = buildGetRequest(url);
    request.setConfig(RequestConfig.copy(defaultRequestConfig).setRedirectsEnabled(false).build());
    return executeAsync(request, null, operationTimeout);
  }

  @Override
  public CompletableFuture<Response> getAsyncAnonymous(String url, Duration timeout) {
    return executeAsync(buildGetRequest(url), null, timeout);
  }

  private SimpleHttpRequest buildGetRequest(String url) {
    var requestBuilder = SimpleRequestBuilder.get(url);

    if (isBridgeClient) {
      requestBuilder
        .addHeader(HOST_HEADER, LOCALHOST)
        .addHeader(ORIGIN_HEADER, LOCALHOST_ORIGIN);
    }
    return requestBuilder.build();
  }

  private class CompletableFutureWrappingFuture extends CompletableFuture<Response> {

    private final CompletableFuture<Response> outcome = new CompletableFuture<>();
    private final Future<SimpleHttpResponse> wrapped;

    private CompletableFutureWrappingFuture(SimpleHttpRequest httpRequest, Duration timeout) {
      this.wrapped = apacheClient.execute(httpRequest, new FutureCallback<>() {
        @Override
        public void completed(SimpleHttpResponse result) {
          try {
            var uri = httpRequest.getUri().toString();
            outcome.complete(new HttpResponse(uri, result));
          } catch (URISyntaxException e) {
            outcome.complete(new HttpResponse(httpRequest.getRequestUri(), result));
          }
        }

        @Override
        public void failed(Exception ex) {
          outcome.completeExceptionally(ex);
        }

        @Override
        public void cancelled() {
          outcome.cancel(true);
        }
      });
      var deadline = DEADLINES.schedule(() -> {
        if (outcome.completeExceptionally(new HttpTimeoutException("HTTP request exceeded its operation deadline"))) {
          wrapped.cancel(true);
        }
      }, timeout.toMillis(), TimeUnit.MILLISECONDS);
      outcome.whenComplete((response, error) -> deadline.cancel(false));
      // Caller callbacks must not block the deadline scheduler or Apache's I/O threads.
      outcome.whenCompleteAsync((response, error) -> {
        if (outcome.isCancelled()) {
          super.cancel(true);
        } else if (error != null) {
          completeExceptionally(error);
        } else {
          complete(response);
        }
      });
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      if (!outcome.cancel(mayInterruptIfRunning)) {
        return false;
      }
      wrapped.cancel(mayInterruptIfRunning);
      super.cancel(mayInterruptIfRunning);
      return true;
    }
  }

  private CompletableFuture<Response> executeAsync(SimpleHttpRequest httpRequest, @Nullable String tokenToUse, Duration timeout) {
    try {
      if (tokenToUse != null) {
        httpRequest.setHeader(AUTHORIZATION_HEADER, bearer(tokenToUse));
      }
      if (apiKey != null) {
        httpRequest.setHeader(X_API_KEY_HEADER, apiKey);
      }
      return new CompletableFutureWrappingFuture(httpRequest, timeout);
    } catch (Exception e) {
      throw new IllegalStateException("Unable to execute request: " + e.getMessage(), e);
    }
  }

  private static String bearer(String token) {
    return String.format("Bearer %s", token);
  }

}
