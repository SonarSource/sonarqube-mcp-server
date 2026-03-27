/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
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
package org.sonarsource.sonarqube.mcp.transport;

import io.modelcontextprotocol.spec.McpServerSession;
import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StdioServerTransportProviderTest {

  /**
   * Helper to create a provider with a mock session without starting the internal transport.
   * We need to use reflection to set the session field directly to avoid the background threads.
   * Uses a shorter timeout (1 second) for faster test execution.
   */
  private StdioServerTransportProvider createProviderWithMockSession(McpServerSession mockSession) throws Exception {
    var provider = new StdioServerTransportProvider(null, Duration.ofSeconds(1));
    var sessionField = StdioServerTransportProvider.class.getDeclaredField("session");
    sessionField.setAccessible(true);
    sessionField.set(provider, mockSession);
    return provider;
  }

  @Test
  void closeGracefully_should_return_empty_mono_when_session_is_null() {
    var provider = new StdioServerTransportProvider(null);

    var result = provider.closeGracefully();

    assertThatCode(() -> result.block(Duration.ofSeconds(1))).doesNotThrowAnyException();
  }

  @Test
  void closeGracefully_should_complete_successfully_when_session_closes_quickly() throws Exception {
    var mockSession = mock(McpServerSession.class);
    when(mockSession.closeGracefully()).thenReturn(Mono.empty());
    var provider = createProviderWithMockSession(mockSession);

    var result = provider.closeGracefully();

    assertThatCode(() -> result.block(Duration.ofSeconds(2))).doesNotThrowAnyException();
    verify(mockSession, times(1)).closeGracefully();
    verify(mockSession, never()).close();
  }

  @Test
  void closeGracefully_should_timeout_and_force_close_after_configured_timeout() throws Exception {
    var mockSession = mock(McpServerSession.class);
    when(mockSession.closeGracefully()).thenReturn(Mono.never());
    var provider = createProviderWithMockSession(mockSession);

    var result = provider.closeGracefully();

    assertThatCode(() -> result.block(Duration.ofSeconds(2))).doesNotThrowAnyException();
    verify(mockSession, times(1)).closeGracefully();
    verify(mockSession, times(1)).close(); // Should be force-closed
  }

  @Test
  void closeGracefully_should_handle_error_and_complete_gracefully() throws Exception {
    var mockSession = mock(McpServerSession.class);
    when(mockSession.closeGracefully()).thenReturn(
      Mono.error(new RuntimeException("Close failed"))
    );
    var provider = createProviderWithMockSession(mockSession);

    var result = provider.closeGracefully();

    assertThatCode(() -> result.block(Duration.ofSeconds(2))).doesNotThrowAnyException();
    verify(mockSession, times(1)).closeGracefully();
  }

  @Test
  void closeGracefully_should_handle_exception_during_forced_close() throws Exception {
    var mockSession = mock(McpServerSession.class);
    when(mockSession.closeGracefully()).thenReturn(Mono.never());
    Mockito.doThrow(new RuntimeException("Force close failed"))
      .when(mockSession).close();
    var provider = createProviderWithMockSession(mockSession);

    var result = provider.closeGracefully();

    assertThatCode(() -> result.block(Duration.ofSeconds(2))).doesNotThrowAnyException();
    verify(mockSession, times(1)).closeGracefully();
    verify(mockSession, times(1)).close();
  }

  @Test
  void closeGracefully_should_complete_immediately_if_session_closes_in_5_seconds() throws Exception {
    var mockSession = mock(McpServerSession.class);
    when(mockSession.closeGracefully()).thenReturn(
      Mono.delay(Duration.ofMillis(500)).then()
    );
    var provider = createProviderWithMockSession(mockSession);

    var result = provider.closeGracefully();

    assertThatCode(() -> result.block(Duration.ofSeconds(1))).doesNotThrowAnyException();
    verify(mockSession, times(1)).closeGracefully();
    verify(mockSession, never()).close();
  }

  @Test
  void shutdown_callback_should_run_without_interrupt_flag_when_stdin_closes() throws Exception {
    // Reproduces the Docker container hang root cause:
    // When stdin closes (EOF), the inbound processing thread runs the shutdown callback.
    // Previously, handleIncomingMessages' doOnTerminate disposed the inbound scheduler
    // (calling ExecutorService.shutdownNow()), which interrupted the current thread BEFORE
    // the shutdown callback ran. This caused the entire SonarQubeMcpServer.shutdown() to fail.
    var pipedOut = new PipedOutputStream();
    var pipedIn = new PipedInputStream(pipedOut);
    var stdout = new ByteArrayOutputStream();

    var callbackInterrupted = new AtomicBoolean(false);
    var callbackExecuted = new CountDownLatch(1);
    Runnable shutdownCallback = () -> {
      callbackInterrupted.set(Thread.currentThread().isInterrupted());
      callbackExecuted.countDown();
    };

    var provider = new StdioServerTransportProvider(pipedIn, stdout, shutdownCallback, Duration.ofSeconds(1));

    // Wire up the transport by setting the session factory (this starts the inbound/outbound threads)
    var mockSession = mock(McpServerSession.class);
    when(mockSession.closeGracefully()).thenReturn(Mono.empty());
    provider.setSessionFactory(transport -> mockSession);

    // Close stdin to simulate Docker/Claude disconnecting
    pipedOut.close();

    // Wait for the shutdown callback to execute
    assertThat(callbackExecuted.await(5, TimeUnit.SECONDS))
      .as("Shutdown callback should be called within 5 seconds of stdin closing")
      .isTrue();

    // The shutdown callback must NOT see the interrupt flag
    assertThat(callbackInterrupted.get())
      .as("Shutdown callback should run without the thread's interrupt flag set")
      .isFalse();
  }

}

