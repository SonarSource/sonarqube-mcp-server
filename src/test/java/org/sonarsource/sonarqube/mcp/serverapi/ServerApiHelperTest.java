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
package org.sonarsource.sonarqube.mcp.serverapi;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServerApiHelperTest {

  @Test
  void it_should_return_the_response() {
    var response = mock(HttpClient.Response.class);

    assertThat(ServerApiHelper.awaitResponse(CompletableFuture.completedFuture(response))).isSameAs(response);
  }

  @Test
  void it_should_preserve_request_failure() {
    var failure = new IOException("Connection closed");
    var request = CompletableFuture.<HttpClient.Response>failedFuture(failure);

    assertThatThrownBy(() -> ServerApiHelper.awaitResponse(request))
      .isInstanceOf(CompletionException.class)
      .hasCause(failure);
  }

  @Test
  void it_should_propagate_request_cancellation() {
    var request = new CompletableFuture<HttpClient.Response>();
    request.cancel(true);

    assertThatThrownBy(() -> ServerApiHelper.awaitResponse(request)).isInstanceOf(CancellationException.class);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void it_should_cancel_delete_when_the_caller_is_interrupted(boolean apiSubdomain) throws InterruptedException {
    var request = new CompletableFuture<HttpClient.Response>();
    var client = mock(HttpClient.class);
    when(client.deleteAsync("http://localhost/directive")).thenReturn(request);
    var helper = new ServerApiHelper(new EndpointParams("http://localhost", null, null, false), client);
    var failure = new AtomicReference<Throwable>();
    var caller = new Thread(() -> {
      try {
        Thread.currentThread().interrupt();
        if (apiSubdomain) {
          helper.deleteApiSubdomain("/directive");
        } else {
          helper.delete("/directive");
        }
      } catch (Throwable error) {
        failure.set(error);
      }
    });
    try {
      caller.start();
      caller.join(1000);
      assertThat(caller.isAlive()).isFalse();
      assertThat(failure.get()).isInstanceOf(CancellationException.class);
      assertThat(request.isCancelled()).isTrue();
    } finally {
      request.cancel(true);
      caller.join(1000);
    }
  }

  @Test
  void it_should_cancel_the_request_when_interrupted() {
    var request = new CompletableFuture<HttpClient.Response>();
    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(() -> ServerApiHelper.awaitResponse(request)).isInstanceOf(CancellationException.class);
      assertThat(request.isCancelled()).isTrue();
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

}
