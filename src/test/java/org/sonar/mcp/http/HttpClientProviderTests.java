/*
 * Sonar MCP Server
 * Copyright (C) 2025 SonarSource
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.mcp.http;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

class HttpClientProviderTests {

  private static final String USER_AGENT = "Sonar MCP Server Tests";

  @RegisterExtension
  static WireMockExtension sonarqubeMock = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

  @Test
  void it_should_use_user_agent() {
    var underTest = new HttpClientProvider(USER_AGENT);

    underTest.getHttpClient().get(sonarqubeMock.url("/test"));

    sonarqubeMock.verify(getRequestedFor(urlEqualTo("/test"))
      .withHeader("User-Agent", equalTo(USER_AGENT)));
  }

  @Test
  void it_should_support_cancellation() {
    sonarqubeMock.stubFor(get("/delayed")
      .willReturn(aResponse()
        .withFixedDelay(20000)));

    var underTest = new HttpClientProvider(USER_AGENT);

    var future = underTest.getHttpClient().getAsync(sonarqubeMock.url("/delayed"));
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

    new HttpClientProvider(USER_AGENT).getHttpClient().post(sonarqubeMock.url("/permanentMoved"), "text/html", "Foo");

    sonarqubeMock.verify(postRequestedFor(urlEqualTo("/afterMove")));
  }

  @Test
  void it_should_preserve_post_on_temporarily_moved_status() {
    sonarqubeMock.stubFor(post("/afterMove").willReturn(aResponse()));
    sonarqubeMock.stubFor(post("/tempMoved")
      .willReturn(aResponse()
        .withStatus(HttpStatus.SC_MOVED_TEMPORARILY)
        .withHeader("Location", sonarqubeMock.url("/afterMove"))));

    new HttpClientProvider(USER_AGENT).getHttpClient().post(sonarqubeMock.url("/tempMoved"), "text/html", "Foo");

    sonarqubeMock.verify(postRequestedFor(urlEqualTo("/afterMove")));
  }

  @Test
  void it_should_preserve_post_on_see_other_status() {
    sonarqubeMock.stubFor(post("/afterMove").willReturn(aResponse()));
    sonarqubeMock.stubFor(post("/seeOther")
      .willReturn(aResponse()
        .withStatus(HttpStatus.SC_SEE_OTHER)
        .withHeader("Location", sonarqubeMock.url("/afterMove"))));

    new HttpClientProvider(USER_AGENT).getHttpClient().post(sonarqubeMock.url("/seeOther"), "text/html", "Foo");

    sonarqubeMock.verify(postRequestedFor(urlEqualTo("/afterMove")));
  }

  @Test
  void it_should_support_async_get() throws ExecutionException, InterruptedException, TimeoutException {
    var underTest = new HttpClientProvider(USER_AGENT);

    underTest.getHttpClient().getAsync(sonarqubeMock.url("/test")).get(2, TimeUnit.SECONDS);

    sonarqubeMock.verify(getRequestedFor(urlEqualTo("/test")));
  }

  @Test
  void it_should_support_async_post() throws ExecutionException, InterruptedException, TimeoutException {
    var underTest = new HttpClientProvider(USER_AGENT);

    underTest.getHttpClient().postAsync(sonarqubeMock.url("/test"), "text/html", "").get(2, TimeUnit.SECONDS);

    sonarqubeMock.verify(postRequestedFor(urlEqualTo("/test")));
  }

}
