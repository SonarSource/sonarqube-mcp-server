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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorShutdownException;
import org.apache.hc.core5.reactor.IOReactorStatus;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
  void it_should_recover_existing_clients_after_http_reactor_stops() {
    var provider = new HttpClientProvider(USER_AGENT);
    var authenticatedClient = provider.getHttpClient("token");
    var anonymousClient = provider.getAnonymousHttpClient();
    var stoppedClient = provider.getRunningClient();
    stoppedClient.close(CloseMode.IMMEDIATE);

    try (var ignored = authenticatedClient.getAsync(sonarqubeMock.url("/recovered")).join()) {
      // nothing
    }

    assertThat(provider.getRunningClient()).isNotSameAs(stoppedClient);
    sonarqubeMock.verify(getRequestedFor(urlEqualTo("/recovered"))
      .withHeader("Authorization", equalTo("Bearer token"))
      .withHeader("User-Agent", equalTo(USER_AGENT)));

    try (var ignored = anonymousClient.postAsync(sonarqubeMock.url("/recovered-post"), "text/plain", "body").join()) {
      // nothing
    }

    sonarqubeMock.verify(postRequestedFor(urlEqualTo("/recovered-post")));
    provider.shutdown();
  }

  @Test
  void it_should_not_recreate_client_after_provider_shutdown() {
    var provider = new HttpClientProvider(USER_AGENT);
    var client = provider.getHttpClient("token");
    provider.shutdown();
    var url = sonarqubeMock.url("/test");

    assertThat(assertThrows(IllegalStateException.class, () -> client.getAsync(url)))
      .hasMessageContaining("shut down");
  }

  @Test
  void it_should_recover_when_client_is_stale_but_reactor_status_is_active() {
    var provider = new HttpClientProvider(USER_AGENT);
    try (var ignored = provider.getHttpClient("token").getAsync(sonarqubeMock.url("/before-stale")).join()) {
      // nothing
    }
    var staleClient = provider.getRunningClient();
    assertThat(staleClient.getStatus()).isEqualTo(IOReactorStatus.ACTIVE);
    provider.markClientStale(staleClient);

    try (var ignored = provider.getHttpClient("token").getAsync(sonarqubeMock.url("/recovered-active")).join()) {
      // nothing
    }

    assertThat(provider.getRunningClient()).isNotSameAs(staleClient);
    sonarqubeMock.verify(getRequestedFor(urlEqualTo("/recovered-active")));
    provider.shutdown();
  }

  @Test
  void it_should_mark_client_stale_when_a_request_reports_reactor_shutdown() {
    var provider = mock(HttpClientProvider.class);
    var apacheClient = mock(CloseableHttpAsyncClient.class);
    when(provider.getRunningClient()).thenReturn(apacheClient);
    when(apacheClient.execute(any(SimpleHttpRequest.class), any())).thenAnswer(invocation -> {
      FutureCallback<SimpleHttpResponse> callback = invocation.getArgument(1);
      callback.failed(new IOReactorShutdownException("I/O reactor has been shut down"));
      return CompletableFuture.<SimpleHttpResponse>completedFuture(null);
    });
    var adapter = new HttpClientAdapter(provider, null, false);
    var url = sonarqubeMock.url("/test");

    assertThatThrownBy(() -> adapter.getAsync(url).join())
      .hasCauseInstanceOf(IOReactorShutdownException.class);
    verify(provider).markClientStale(apacheClient);
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
