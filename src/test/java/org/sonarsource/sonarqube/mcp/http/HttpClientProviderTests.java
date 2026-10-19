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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.util.DeadlineTimeoutException;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
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

  @Test
  void it_should_preserve_the_pool_timeout_when_overriding_the_response_timeout() throws Exception {
    sonarqubeMock.stubFor(get("/occupied").willReturn(aResponse().withFixedDelay(1500)));
    var connectionManager = PoolingAsyncClientConnectionManagerBuilder.create().setMaxConnPerRoute(1).setMaxConnTotal(1).build();
    var requestConfig = RequestConfig.custom()
      .setConnectionRequestTimeout(Timeout.ofMilliseconds(200))
      .setResponseTimeout(Timeout.ofSeconds(10))
      .build();

    try (var apacheClient = HttpAsyncClients.custom().setConnectionManager(connectionManager).setDefaultRequestConfig(requestConfig).build()) {
      apacheClient.start();
      var client = new HttpClientAdapter(apacheClient, null, false, requestConfig);
      var occupied = client.getAsync(sonarqubeMock.url("/occupied"));
      try {
        await().atMost(3, TimeUnit.SECONDS).until(() -> connectionManager.getTotalStats().getLeased() == 1);

        var future = client.postAsync(sonarqubeMock.url("/queued"), HttpClient.JSON_CONTENT_TYPE, "{}", Duration.ofSeconds(60));
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
