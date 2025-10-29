/*
 * SonarQube MCP Server
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
package org.sonarsource.sonarqube.mcp.http;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpClientProxyTests {

  private static final String USER_AGENT = "SonarQube MCP Server Proxy Tests";

  @RegisterExtension
  static WireMockExtension targetServer = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  private ProxySelector originalProxySelector;

  @AfterEach
  void tearDown() {
    // Restore original proxy selector
    if (originalProxySelector != null) {
      ProxySelector.setDefault(originalProxySelector);
    }
    // Clear proxy system properties
    System.clearProperty("http.proxyHost");
    System.clearProperty("http.proxyPort");
    System.clearProperty("https.proxyHost");
    System.clearProperty("https.proxyPort");
    System.clearProperty("http.nonProxyHosts");
  }

  @Test
  void should_invoke_proxy_selector_when_making_requests() {
    var trackingProxySelector = new TrackingProxySelector();
    originalProxySelector = ProxySelector.getDefault();
    ProxySelector.setDefault(trackingProxySelector);

    targetServer.stubFor(get("/test")
      .willReturn(aResponse()
        .withStatus(HttpStatus.SC_OK)
        .withBody("success")));

    var underTest = new HttpClientProvider(USER_AGENT);

    // Verify ProxySelector wasn't called during client creation
    assertThat(trackingProxySelector.getSelectCallCount()).isZero();
    // Make a request
    try (var response = underTest.getHttpClient("token").getAsync(targetServer.url("/test")).join()) {
      assertThat(response.code()).isEqualTo(HttpStatus.SC_OK);
    }
    assertThat(trackingProxySelector.getSelectCallCount())
      .as("ProxySelector.select() should be invoked when making HTTP requests")
      .isGreaterThan(0);
    // Verify the URI passed to the proxy selector matches our target
    assertThat(trackingProxySelector.getSelectCalls())
      .as("ProxySelector should be called with the target server URI")
      .anyMatch(uri -> uri.getHost().equals("localhost"));
  }

  @Test
  void should_use_custom_proxy_selector_for_each_request() {
    // Create a proxy selector that tracks all calls
    var customProxySelector = new TrackingProxySelector();
    originalProxySelector = ProxySelector.getDefault();
    ProxySelector.setDefault(customProxySelector);

    targetServer.stubFor(get("/endpoint1")
      .willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
    targetServer.stubFor(get("/endpoint2")
      .willReturn(aResponse().withStatus(HttpStatus.SC_OK)));

    var underTest = new HttpClientProvider(USER_AGENT);

    underTest.getHttpClient("token").getAsync(targetServer.url("/endpoint1")).join().close();
    underTest.getHttpClient("token").getAsync(targetServer.url("/endpoint2")).join().close();

    assertThat(customProxySelector.getSelectCallCount())
      .as("ProxySelector should be called for each HTTP request")
      .isEqualTo(2);
  }

  @Test
  void should_fail_when_proxy_selector_returns_invalid_proxy() {
    // Create a proxy selector that returns an invalid proxy
    originalProxySelector = ProxySelector.getDefault();
    ProxySelector.setDefault(new ProxySelector() {
      @Override
      public List<Proxy> select(URI uri) {
        // Return a proxy that points to a non-existent server
        return List.of(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("localhost", 19999)));
      }

      @Override
      public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
        // Not used
      }
    });

    targetServer.stubFor(get("/test")
      .willReturn(aResponse().withStatus(HttpStatus.SC_OK)));

    var underTest = new HttpClientProvider(USER_AGENT);

    var httpClient = underTest.getHttpClient("token").getAsync(targetServer.url("/test"));
    assertThatThrownBy(httpClient::join)
      .as("Request should fail when proxy is unreachable, proving proxy is being used")
      .isInstanceOf(CompletionException.class);
  }

  /**
   * A ProxySelector that tracks calls to select() to prove that proxy
   * selection is actually being invoked during HTTP requests.
   */
  private static class TrackingProxySelector extends ProxySelector {
    private final List<URI> selectCalls = new ArrayList<>();

    @Override
    public List<Proxy> select(URI uri) {
      selectCalls.add(uri);
      return List.of(Proxy.NO_PROXY);
    }

    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
      // Not used in tests
    }

    public int getSelectCallCount() {
      return selectCalls.size();
    }

    public List<URI> getSelectCalls() {
      return new ArrayList<>(selectCalls);
    }
  }

}
