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

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpClientProviderTests {

  private static final String USER_AGENT = "SonarQube MCP Server Tests";

  @RegisterExtension
  static WireMockExtension sonarqubeMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  @Test
  void it_should_use_user_agent() {
    var underTest = new HttpClientProvider(USER_AGENT);

    try (var ignored = underTest.getHttpClient("token").getAsync(sonarqubeMock.url("/test")).join()) {
      // nothing
    }

    sonarqubeMock.verify(getRequestedFor(urlEqualTo("/test"))
      .withHeader("User-Agent", equalTo(USER_AGENT)));
  }

  @Test
  void it_should_support_cancellation() {
    sonarqubeMock.stubFor(get("/delayed")
      .willReturn(aResponse()
        .withFixedDelay(20000)));

    var underTest = new HttpClientProvider(USER_AGENT);

    var future = underTest.getHttpClient("token").getAsync(sonarqubeMock.url("/delayed"));
    assertThrows(TimeoutException.class, () -> future.get(100, TimeUnit.MILLISECONDS));
    assertThat(future.cancel(true)).isTrue();
    assertThat(future).isCancelled();
  }

  @Test
  void it_should_time_out_stalled_response_headers() {
    sonarqubeMock.stubFor(get("/stalled-headers")
      .willReturn(aResponse().withFixedDelay(2000)));
    var underTest = new HttpClientProvider(USER_AGENT, Duration.ofMillis(200), Duration.ofMillis(200));

    try {
      var future = underTest.getHttpClient("token").getAsync(sonarqubeMock.url("/stalled-headers"));
      var error = assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
      assertThat(error.getCause()).isInstanceOf(HttpTimeoutException.class);
      sonarqubeMock.verify(1, getRequestedFor(urlEqualTo("/stalled-headers")));
    } finally {
      underTest.shutdown();
    }
  }

  @Test
  void it_should_cancel_a_request_with_a_stalled_response_body() throws Exception {
    try (var server = new ServerSocket(0); var worker = Executors.newSingleThreadExecutor()) {
      var connectionClosed = worker.submit(() -> {
        try (var socket = server.accept()) {
          socket.setSoTimeout(3000);
          var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
          while (!reader.readLine().isEmpty()) {
            // Consume request headers before checking for the cancelled connection.
          }
          var output = socket.getOutputStream();
          output.write("HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\nx".getBytes(StandardCharsets.US_ASCII));
          output.flush();
          try {
            return reader.read() == -1;
          } catch (SocketException e) {
            return true;
          }
        }
      });
      var underTest = new HttpClientProvider(USER_AGENT, Duration.ofMillis(250), Duration.ofMillis(250));
      try {
        var future = underTest.getHttpClient("token").getAsync("http://localhost:" + server.getLocalPort() + "/body");
        var error = assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
        assertThat(error.getCause()).isInstanceOf(HttpTimeoutException.class);
        assertThat(connectionClosed.get(2, TimeUnit.SECONDS)).isTrue();
      } finally {
        underTest.shutdown();
      }
    }
  }

  @Test
  void it_should_allow_a_slow_download_within_its_budget() throws Exception {
    sonarqubeMock.stubFor(get("/slow-download")
      .willReturn(aResponse().withBody("plugin").withFixedDelay(700)));
    var underTest = new HttpClientProvider(USER_AGENT, Duration.ofMillis(200), Duration.ofMillis(200));

    try (var response = underTest.getAnonymousHttpClient()
      .getAsyncAnonymous(sonarqubeMock.url("/slow-download"), Duration.ofSeconds(2))
      .get(3, TimeUnit.SECONDS)) {
      assertThat(response.bodyAsString()).isEqualTo("plugin");
    } finally {
      underTest.shutdown();
    }
  }

  @Test
  void it_should_not_retry_a_timed_out_post() throws Exception {
    try (var server = new ServerSocket(0); var worker = Executors.newSingleThreadExecutor()) {
      var requests = worker.submit(() -> {
        try (var socket = server.accept()) {
          socket.setSoTimeout(2000);
          var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
          assertThat(reader.readLine()).startsWith("POST /stalled-post ");
          while (!reader.readLine().isEmpty()) {
            // Consume request headers so the server can observe a retry.
          }
          server.setSoTimeout(700);
          try (var ignored = server.accept()) {
            return 2;
          } catch (SocketTimeoutException e) {
            return 1;
          }
        }
      });
      var underTest = new HttpClientProvider(USER_AGENT, Duration.ofMillis(250), Duration.ofMillis(250));
      try {
        var future = underTest.getHttpClient("token")
          .postAsync("http://localhost:" + server.getLocalPort() + "/stalled-post", "text/plain", "payload");
        var error = assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
        assertThat(error.getCause()).isInstanceOf(HttpTimeoutException.class);
        assertThat(requests.get(2, TimeUnit.SECONDS)).isEqualTo(1);
      } finally {
        underTest.shutdown();
      }
    }
  }

  @Test
  void it_should_preserve_post_on_permanent_moved_status() {
    sonarqubeMock.stubFor(post("/afterMove").willReturn(aResponse()));
    sonarqubeMock.stubFor(post("/permanentMoved")
      .willReturn(aResponse()
        .withStatus(HttpStatus.SC_MOVED_PERMANENTLY)
        .withHeader("Location", sonarqubeMock.url("/afterMove"))));

    try (var ignored = new HttpClientProvider(USER_AGENT).getHttpClient("token").postAsync(sonarqubeMock.url("/permanentMoved"), "text/html", "Foo").join()) {
      // nothing
    }

    sonarqubeMock.verify(postRequestedFor(urlEqualTo("/afterMove")));
  }

  @Test
  void it_should_preserve_post_on_temporarily_moved_status() {
    sonarqubeMock.stubFor(post("/afterMove").willReturn(aResponse()));
    sonarqubeMock.stubFor(post("/tempMoved")
      .willReturn(aResponse()
        .withStatus(HttpStatus.SC_MOVED_TEMPORARILY)
        .withHeader("Location", sonarqubeMock.url("/afterMove"))));

    try (var ignored = new HttpClientProvider(USER_AGENT).getHttpClient("token").postAsync(sonarqubeMock.url("/tempMoved"), "text/html", "Foo").join()) {
      // nothing
    }

    sonarqubeMock.verify(postRequestedFor(urlEqualTo("/afterMove")));
  }

  @Test
  void it_should_preserve_post_on_see_other_status() {
    sonarqubeMock.stubFor(post("/afterMove").willReturn(aResponse()));
    sonarqubeMock.stubFor(post("/seeOther")
      .willReturn(aResponse()
        .withStatus(HttpStatus.SC_SEE_OTHER)
        .withHeader("Location", sonarqubeMock.url("/afterMove"))));

    try (var ignored = new HttpClientProvider(USER_AGENT).getHttpClient("token").postAsync(sonarqubeMock.url("/seeOther"), "text/html", "Foo").join()) {
      // nothing
    }

    sonarqubeMock.verify(postRequestedFor(urlEqualTo("/afterMove")));
  }

  @Test
  void it_should_support_async_get() throws ExecutionException, InterruptedException, TimeoutException {
    var underTest = new HttpClientProvider(USER_AGENT);

    underTest.getHttpClient("token").getAsync(sonarqubeMock.url("/test")).get(2, TimeUnit.SECONDS);

    sonarqubeMock.verify(getRequestedFor(urlEqualTo("/test")));
  }

  @Test
  void it_should_support_async_post() throws ExecutionException, InterruptedException, TimeoutException {
    var underTest = new HttpClientProvider(USER_AGENT);

    underTest.getHttpClient("token").postAsync(sonarqubeMock.url("/test"), "text/html", "").get(2, TimeUnit.SECONDS);

    sonarqubeMock.verify(postRequestedFor(urlEqualTo("/test")));
  }

  @Test
  void bridge_client_should_add_host_and_origin_headers_on_get() {
    var underTest = new HttpClientProvider(USER_AGENT);

    try (var ignored = underTest.getHttpClientForBridge().getAsync(sonarqubeMock.url("/status")).join()) {
      // nothing
    }

    sonarqubeMock.verify(getRequestedFor(urlEqualTo("/status"))
      .withHeader("Host", equalTo("localhost"))
      .withHeader("Origin", equalTo("http://localhost")));
  }

  @Test
  void bridge_client_should_add_host_and_origin_headers_on_post() {
    var underTest = new HttpClientProvider(USER_AGENT);

    try (var ignored = underTest.getHttpClientForBridge().postAsync(sonarqubeMock.url("/analyze"), "application/json", "{}").join()) {
      // nothing
    }

    sonarqubeMock.verify(postRequestedFor(urlEqualTo("/analyze"))
      .withHeader("Host", equalTo("localhost"))
      .withHeader("Origin", equalTo("http://localhost")));
  }

  @Test
  void body_as_string_should_fallback_to_bytes_when_text_is_null() {
    var response = mock(SimpleHttpResponse.class);
    when(response.getBodyText()).thenReturn(null);
    when(response.getBodyBytes()).thenReturn("payload".getBytes(StandardCharsets.UTF_8));

    var underTest = new HttpResponse("http://example.com/api", response);

    assertThat(underTest.bodyAsString()).isEqualTo("payload");
  }

}
