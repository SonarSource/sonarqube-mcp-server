package org.sonar.mcp.http;

import java.io.Closeable;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

public interface HttpClient {

  interface Response extends Closeable {

    int code();

    default boolean isSuccessful() {
      return code() >= 200 && code() < 300;
    }

    String bodyAsString();

    InputStream bodyAsStream();

    /**
     * Only runtime exception
     */
    @Override
    void close();

    String url();
  }

  Response get(String url);

  CompletableFuture<Response> getAsync(String url);

  Response post(String url, String contentType, String body);

  CompletableFuture<Response> postAsync(String url, String contentType, String body);

}
