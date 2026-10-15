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
package org.sonarsource.sonarqube.mcp.authentication;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class JwksHttpClientAdapterTest {
  private static final String JWKS_URL = "https://tenant.auth0.com/.well-known/jwks.json";
  private final HttpClient client = mock(HttpClient.class);
  private final JwksHttpClientAdapter adapter = new JwksHttpClientAdapter(client);

  @Test
  void it_should_fetch_anonymously_and_close_the_response() throws Exception {
    var response = mock(HttpClient.Response.class);
    when(response.isSuccessful()).thenReturn(true);
    when(response.bodyAsString()).thenReturn("{\"keys\":[]}");
    when(client.getAsyncAnonymousWithoutRedirects(JWKS_URL)).thenReturn(CompletableFuture.completedFuture(response));

    assertThat(adapter.fetch(url()).getBody()).isEqualTo("{\"keys\":[]}");

    verify(client).getAsyncAnonymousWithoutRedirects(JWKS_URL);
    verifyNoMoreInteractions(client);
    verify(response).close();
  }

  @ParameterizedTest
  @ValueSource(ints = {302, 401, 403, 500, 503})
  void it_should_reject_http_errors_and_close_the_response(int status) throws Exception {
    var response = mock(HttpClient.Response.class);
    when(response.isSuccessful()).thenCallRealMethod();
    when(response.code()).thenReturn(status);
    when(client.getAsyncAnonymousWithoutRedirects(JWKS_URL)).thenReturn(CompletableFuture.completedFuture(response));

    assertThatThrownBy(() -> adapter.fetch(url())).isInstanceOf(IOException.class).hasMessage("JWKS request failed");

    verify(response).close();
  }

  @Test
  void it_should_report_network_failures() {
    when(client.getAsyncAnonymousWithoutRedirects(JWKS_URL)).thenReturn(CompletableFuture.failedFuture(new IOException("private upstream details")));

    assertThatThrownBy(() -> adapter.fetch(url())).isInstanceOf(IOException.class).hasMessage("JWKS request failed");
  }

  @Test
  void it_should_cancel_a_request_after_the_deadline() {
    var pending = new CompletableFuture<HttpClient.Response>();
    when(client.getAsyncAnonymousWithoutRedirects(JWKS_URL)).thenReturn(pending);

    assertThatThrownBy(() -> adapter.fetch(url())).isInstanceOf(IOException.class).hasMessage("JWKS request failed");

    assertThat(pending).isCancelled();
  }

  @Test
  void it_should_cancel_on_interruption_and_preserve_the_interrupt_flag() {
    var pending = new CompletableFuture<HttpClient.Response>();
    when(client.getAsyncAnonymousWithoutRedirects(JWKS_URL)).thenReturn(pending);
    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(() -> adapter.fetch(url())).isInstanceOf(IOException.class).hasMessage("JWKS request interrupted");
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      assertThat(pending).isCancelled();
    } finally {
      Thread.interrupted();
    }
  }

  private static URL url() throws Exception {
    return URI.create(JWKS_URL).toURL();
  }
}
