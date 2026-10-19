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
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.DeadlineTimeoutException;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.bridge.SonarQubeIdeBridgeClient;
import org.sonarsource.sonarqube.mcp.serverapi.EndpointParams;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
  void it_should_fail_when_the_response_exceeds_the_timeout() {
    sonarqubeMock.stubFor(get("/stalled")
      .willReturn(aResponse().withFixedDelay(10_000)));

    var underTest = new HttpClientProvider(USER_AGENT, Timeout.ofMilliseconds(500));
    try {
      var future = underTest.getHttpClient("token").getAsync(sonarqubeMock.url("/stalled"));
      var error = assertThrows(ExecutionException.class, () -> future.get(3, TimeUnit.SECONDS));

      assertThat(future).isCompletedExceptionally();
      assertThat(isTimeout(error)).isTrue();
    } finally {
      underTest.shutdown();
    }
  }

  @Test
  void it_should_allow_analysis_to_exceed_the_api_response_timeout() throws Exception {
    sonarqubeMock.stubFor(post(SonarQubeIdeBridgeClient.ANALYSIS_FILES_PATH)
      .willReturn(aResponse().withBody("{\"findings\":[]}").withFixedDelay(1200)));
    sonarqubeMock.stubFor(get("/stalled-api").willReturn(aResponse().withFixedDelay(3000)));
    var underTest = new HttpClientProvider(USER_AGENT, Timeout.ofMilliseconds(500));
    var helper = new ServerApiHelper(new EndpointParams(sonarqubeMock.baseUrl(), null, null, false), underTest.getHttpClientForBridge());
    var bridge = new SonarQubeIdeBridgeClient(helper);

    try {
      assertThat(bridge.requestAnalyzeFileList(List.of("file.java"))).isPresent();
      sonarqubeMock.verify(1, postRequestedFor(urlEqualTo(SonarQubeIdeBridgeClient.ANALYSIS_FILES_PATH))
        .withHeader("Host", equalTo("localhost"))
        .withHeader("Origin", equalTo("http://localhost"))
        .withRequestBody(equalTo("{\"fileAbsolutePaths\":[\"file.java\"]}")));
      var future = underTest.getHttpClient("token").getAsync(sonarqubeMock.url("/stalled-api"));
      var error = assertThrows(ExecutionException.class, () -> future.get(3, TimeUnit.SECONDS));
      assertThat(isTimeout(error)).isTrue();
    } finally {
      underTest.shutdown();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void it_should_preserve_the_pool_timeout_when_overriding_request_settings(boolean customPostTimeout) throws Exception {
    sonarqubeMock.stubFor(get("/occupied").willReturn(aResponse().withFixedDelay(1500)));
    var connectionManager = PoolingAsyncClientConnectionManagerBuilder.create().setMaxConnPerRoute(1).setMaxConnTotal(1).build();
    var requestConfig = RequestConfig.custom()
      .setConnectionRequestTimeout(Timeout.ofMilliseconds(200))
      .setResponseTimeout(Timeout.ofSeconds(10))
      .build();

    try (var apacheClient = HttpAsyncClients.custom().setConnectionManager(connectionManager).setDefaultRequestConfig(requestConfig).build()) {
      apacheClient.start();
      var client = new HttpClientAdapter(apacheClient, null, false, Duration.ofSeconds(10), requestConfig, null);
      var occupied = client.getAsync(sonarqubeMock.url("/occupied"));
      try {
        await().atMost(3, TimeUnit.SECONDS).until(() -> connectionManager.getTotalStats().getLeased() == 1);

        var future = customPostTimeout
          ? client.postAsync(sonarqubeMock.url("/queued"), HttpClient.JSON_CONTENT_TYPE, "{}", Duration.ofSeconds(60))
          : client.getAsyncAnonymousWithoutRedirects(sonarqubeMock.url("/queued"));
        var error = assertThrows(ExecutionException.class, () -> future.get(3, TimeUnit.SECONDS));
        assertThat(error).hasCauseInstanceOf(DeadlineTimeoutException.class);
      } finally {
        occupied.cancel(true);
      }
    }
  }

  private static boolean isTimeout(Throwable error) {
    var current = error;
    while (current != null) {
      var typeName = current.getClass().getName();
      if (typeName.contains("Timeout") || typeName.contains("SocketTimeout")) {
        return true;
      }
      current = current.getCause();
    }
    return false;
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
  void it_should_fetch_anonymously_without_following_redirects() {
    sonarqubeMock.stubFor(get("/issuer-keys").willReturn(aResponse().withStatus(302)
      .withHeader("Location", sonarqubeMock.url("/redirected-keys"))));
    var provider = new HttpClientProvider(USER_AGENT);
    try {
      try (var response = provider.getHttpClient("private-token").getAsyncAnonymousWithoutRedirects(sonarqubeMock.url("/issuer-keys")).join()) {
        assertThat(response.code()).isEqualTo(302);
      }
      sonarqubeMock.verify(getRequestedFor(urlEqualTo("/issuer-keys"))
        .withoutHeader("Authorization"));
      sonarqubeMock.verify(0, getRequestedFor(urlEqualTo("/redirected-keys")));
    } finally {
      provider.shutdown();
    }
  }

  @Test
  void it_should_remove_cancelled_deadlines_from_the_queue() {
    var scheduler = HttpClientAdapter.createDeadlineScheduler();
    try {
      var deadline = scheduler.schedule(() -> { }, 5, TimeUnit.MINUTES);
      assertThat(scheduler.getQueue()).hasSize(1);
      assertThat(deadline.cancel(false)).isTrue();
      assertThat(scheduler.getQueue()).isEmpty();
    } finally {
      scheduler.shutdownNow();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void it_should_time_out_stalled_response_headers(boolean anonymousWithoutRedirects) {
    sonarqubeMock.stubFor(get("/stalled-headers")
      .willReturn(aResponse().withFixedDelay(2000)));
    var underTest = new HttpClientProvider(USER_AGENT, Duration.ofMillis(200), Duration.ofMillis(200));

    try {
      var client = underTest.getHttpClient("token");
      var url = sonarqubeMock.url("/stalled-headers");
      var future = anonymousWithoutRedirects ? client.getAsyncAnonymousWithoutRedirects(url) : client.getAsync(url);
      var error = assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
      assertThat(error.getCause()).isInstanceOf(HttpTimeoutException.class);
      sonarqubeMock.verify(1, getRequestedFor(urlEqualTo("/stalled-headers")));
    } finally {
      underTest.shutdown();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void it_should_cancel_requests_and_enforce_deadlines_while_a_callback_is_blocked(boolean callerCancellation) throws Exception {
    var apacheClient = mock(CloseableHttpAsyncClient.class);
    var firstRequest = new CompletableFuture<SimpleHttpResponse>();
    var secondRequest = new CompletableFuture<SimpleHttpResponse>();
    when(apacheClient.execute(any(SimpleHttpRequest.class), any())).thenReturn(firstRequest).thenReturn(secondRequest);
    var client = new HttpClientAdapter(apacheClient, null, false, Duration.ofSeconds(1));
    var callbackStarted = new CompletableFuture<Void>();
    var releaseCallback = new CompletableFuture<Void>();
    var first = client.getAsync("http://localhost/first");
    first.whenComplete((response, error) -> {
      callbackStarted.complete(null);
      assertThat(releaseCallback).succeedsWithin(Duration.ofSeconds(5));
    });

    try {
      if (callerCancellation) {
        CompletableFuture.runAsync(() -> first.cancel(true));
      }
      callbackStarted.get(2, TimeUnit.SECONDS);
      assertThrows(CancellationException.class, () -> firstRequest.get(2, TimeUnit.SECONDS));
      var second = client.getAsync("http://localhost/second");
      var error = assertThrows(ExecutionException.class, () -> second.get(2, TimeUnit.SECONDS));
      assertThat(error.getCause()).isInstanceOf(HttpTimeoutException.class);
      assertThrows(CancellationException.class, () -> secondRequest.get(2, TimeUnit.SECONDS));
    } finally {
      releaseCallback.complete(null);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void it_should_cancel_a_request_with_a_stalled_response_body(boolean customPostTimeout) throws Exception {
    try (var worker = Executors.newSingleThreadExecutor(); var server = new ServerSocket(0)) {
      server.setSoTimeout(3000);
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
        var client = underTest.getHttpClient("token");
        var url = "http://localhost:" + server.getLocalPort() + "/body";
        var future = customPostTimeout
          ? client.postAsync(url, "text/plain", "", Duration.ofMillis(250))
          : client.getAsync(url);
        var error = assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
        assertThat(error.getCause()).isInstanceOfAny(HttpTimeoutException.class, SocketTimeoutException.class);
        assertThat(connectionClosed.get(2, TimeUnit.SECONDS)).isTrue();
      } finally {
        underTest.shutdown();
      }
    }
  }

  @Test
  void it_should_isolate_analysis_api_and_analytics_deadlines() throws Exception {
    sonarqubeMock.stubFor(post(SonarQubeIdeBridgeClient.ANALYSIS_FILES_PATH)
      .willReturn(aResponse().withBody("{\"findings\":[]}").withFixedDelay(700)));
    sonarqubeMock.stubFor(get("/stalled-api").willReturn(aResponse().withFixedDelay(2000)));
    sonarqubeMock.stubFor(get(SonarQubeIdeBridgeClient.STATUS_PATH).willReturn(aResponse().withFixedDelay(2000)));
    sonarqubeMock.stubFor(post("/stalled-analytics").willReturn(aResponse().withFixedDelay(2000)));
    var underTest = new HttpClientProvider(USER_AGENT, Duration.ofMillis(200), Duration.ofMillis(100));
    var bridgeHttpClient = underTest.getHttpClientForBridge();
    var helper = new ServerApiHelper(new EndpointParams(sonarqubeMock.baseUrl(), null, null, false), bridgeHttpClient);
    var bridge = new SonarQubeIdeBridgeClient(helper);

    try {
      var analysis = CompletableFuture.supplyAsync(() -> bridge.requestAnalyzeFileList(List.of("file.java")));
      var api = underTest.getHttpClient("token").getAsync(sonarqubeMock.url("/stalled-api"));
      var status = bridgeHttpClient.getAsync(sonarqubeMock.url(SonarQubeIdeBridgeClient.STATUS_PATH));
      var analytics = underTest.getHttpClientForAnalytics("api-key")
        .postAsync(sonarqubeMock.url("/stalled-analytics"), "application/json", "{}");

      for (var future : List.of(api, status, analytics)) {
        var error = assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
        assertThat(error.getCause()).isInstanceOf(HttpTimeoutException.class);
      }
      assertThat(analysis.get(3, TimeUnit.SECONDS)).isPresent();
      sonarqubeMock.verify(1, postRequestedFor(urlEqualTo(SonarQubeIdeBridgeClient.ANALYSIS_FILES_PATH))
        .withHeader("Host", equalTo("localhost"))
        .withHeader("Origin", equalTo("http://localhost"))
        .withRequestBody(equalTo("{\"fileAbsolutePaths\":[\"file.java\"]}")));
    } finally {
      underTest.shutdown();
    }
  }

  @Test
  void it_should_override_the_socket_timeout_only_for_the_explicit_post() throws Exception {
    sonarqubeMock.stubFor(post("/slow-analysis").willReturn(aResponse().withBody("findings").withFixedDelay(700)));
    sonarqubeMock.stubFor(get("/stalled-api").willReturn(aResponse().withFixedDelay(2000)));
    try (var apacheClient = HttpAsyncClients.custom()
      .setDefaultRequestConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofMilliseconds(200)).build())
      .setIOReactorConfig(IOReactorConfig.custom().setSoTimeout(Timeout.ofMilliseconds(200)).build())
      .build()) {
      apacheClient.start();
      var client = new HttpClientAdapter(apacheClient, null, true, Duration.ofSeconds(5));

      try (var response = client.postAsync(sonarqubeMock.url("/slow-analysis"), "text/plain", "", Duration.ofSeconds(3))
        .get(4, TimeUnit.SECONDS)) {
        assertThat(response.bodyAsString()).isEqualTo("findings");
      }
      var future = client.getAsync(sonarqubeMock.url("/stalled-api"));
      var error = assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
      assertThat(error.getCause()).isInstanceOf(SocketTimeoutException.class);
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

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void it_should_not_retry_a_timed_out_post(boolean customTimeout) throws Exception {
    try (var worker = Executors.newSingleThreadExecutor(); var server = new ServerSocket(0)) {
      server.setSoTimeout(3000);
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
        var client = underTest.getHttpClient("token");
        var url = "http://localhost:" + server.getLocalPort() + "/stalled-post";
        var future = customTimeout
          ? client.postAsync(url, "text/plain", "payload", Duration.ofMillis(250))
          : client.postAsync(url, "text/plain", "payload");
        var error = assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
        assertThat(error.getCause()).isInstanceOfAny(HttpTimeoutException.class, SocketTimeoutException.class);
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
