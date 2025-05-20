/*
 * Sonar MCP Server
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
package org.sonar.mcp.http;

import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import javax.annotation.Nullable;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;

class HttpClientAdapter implements HttpClient {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private final CloseableHttpAsyncClient apacheClient;
  @Nullable
  private final String token;

  HttpClientAdapter(CloseableHttpAsyncClient apacheClient, @Nullable String sonarqubeCloudToken) {
    this.apacheClient = apacheClient;
    this.token = sonarqubeCloudToken;
  }

  @Override
  public Response post(String url, String contentType, String bodyContent) {
    return waitFor(postAsync(url, contentType, bodyContent));
  }

  @Override
  public CompletableFuture<Response> postAsync(String url, String contentType, String body) {
    var request = SimpleRequestBuilder.post(url)
      .setBody(body, ContentType.parse(contentType))
      .build();
    return executeAsync(request);
  }

  @Override
  public Response get(String url) {
    return waitFor(getAsync(url));
  }

  @Override
  public CompletableFuture<Response> getAsync(String url) {
    return executeAsync(SimpleRequestBuilder.get(url).build());
  }

  private static Response waitFor(CompletableFuture<Response> f) {
    return f.join();
  }

  private class CompletableFutureWrappingFuture extends CompletableFuture<Response> {

    private final Future<SimpleHttpResponse> wrapped;

    private CompletableFutureWrappingFuture(SimpleHttpRequest httpRequest) {
      this.wrapped = apacheClient.execute(httpRequest, new FutureCallback<>() {
        @Override
        public void completed(SimpleHttpResponse result) {
          try {
            var uri = httpRequest.getUri().toString();
            HttpClientAdapter.CompletableFutureWrappingFuture.this.completeAsync(() ->
              new org.sonar.mcp.http.HttpResponse(uri, result));
          } catch (URISyntaxException e) {
            HttpClientAdapter.CompletableFutureWrappingFuture.this.completeAsync(() ->
              new org.sonar.mcp.http.HttpResponse(httpRequest.getRequestUri(), result));
          }
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

  private CompletableFuture<Response> executeAsync(SimpleHttpRequest httpRequest) {
    try {
      if (token != null) {
        httpRequest.setHeader(AUTHORIZATION_HEADER, bearer(token));
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
