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
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

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
   */
  private StdioServerTransportProvider createProviderWithMockSession(McpServerSession mockSession) throws Exception {
    var provider = new StdioServerTransportProvider(null);
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
  void closeGracefully_should_timeout_and_force_close_after_10_seconds() throws Exception {
    var mockSession = mock(McpServerSession.class);
    when(mockSession.closeGracefully()).thenReturn(Mono.never());
    var provider = createProviderWithMockSession(mockSession);

    var result = provider.closeGracefully();

    assertThatCode(() -> result.block(Duration.ofSeconds(11))).doesNotThrowAnyException();
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

    assertThatCode(() -> result.block(Duration.ofSeconds(11))).doesNotThrowAnyException();
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

}

