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
package org.sonarsource.sonarqube.mcp.slcore;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.client.ClientJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackendServiceTest {

  @TempDir
  Path tempDir;

  private ClientJsonRpcLauncher mockLauncher;
  private SonarLintRpcServer mockServer;
  private BackendService backendService;

  @BeforeEach
  void setUp() {
    mockLauncher = mock(ClientJsonRpcLauncher.class);
    mockServer = mock(SonarLintRpcServer.class);
    when(mockLauncher.getServerProxy()).thenReturn(mockServer);
    when(mockServer.initialize(any())).thenReturn(CompletableFuture.completedFuture(null));
    when(mockServer.shutdown()).thenReturn(CompletableFuture.completedFuture(null));
    
    backendService = new BackendService(mockLauncher, tempDir, "1.0", "TestApp");
  }

  @Test
  void restartWithAnalyzers_should_initialize_when_not_yet_initialized() {
    // Backend is not initialized (isInitialized = false by default)
    var analyzers = new BackendService.AnalyzersAndLanguagesEnabled(Set.of(), EnumSet.noneOf(Language.class));

    backendService.restartWithAnalyzers(analyzers);

    verify(mockServer).initialize(any());
    verify(mockServer, never()).shutdown();
  }

  @Test
  void restartWithAnalyzers_should_shutdown_and_reinitialize_when_already_initialized() {
    // Backend is already initialized
    var initialAnalyzers = new BackendService.AnalyzersAndLanguagesEnabled(Set.of(), EnumSet.noneOf(Language.class));
    backendService.initialize(initialAnalyzers);

    var newAnalyzers = new BackendService.AnalyzersAndLanguagesEnabled(Set.of(tempDir.resolve("plugin.jar")), EnumSet.of(Language.JAVA));
    backendService.restartWithAnalyzers(newAnalyzers);

    verify(mockServer).shutdown();
    verify(mockLauncher).close();
  }

  @Test
  void restartWithAnalyzers_should_proceed_when_shutdown_throws_exception() {
    var failedFuture = new CompletableFuture<Void>();
    failedFuture.completeExceptionally(new RuntimeException("Shutdown failed"));
    when(mockServer.shutdown()).thenReturn(failedFuture);

    var initialAnalyzers = new BackendService.AnalyzersAndLanguagesEnabled(Set.of(), EnumSet.noneOf(Language.class));
    backendService.initialize(initialAnalyzers);

    var newAnalyzers = new BackendService.AnalyzersAndLanguagesEnabled(Set.of(), EnumSet.noneOf(Language.class));
    backendService.restartWithAnalyzers(newAnalyzers);

    // Should still proceed with restart despite shutdown exception
    verify(mockServer).shutdown();
    verify(mockLauncher).close();
  }

  @Test
  void restartWithAnalyzers_should_proceed_when_launcher_close_throws_exception() {
    doThrow(new RuntimeException("Close failed")).when(mockLauncher).close();
    var initialAnalyzers = new BackendService.AnalyzersAndLanguagesEnabled(Set.of(), EnumSet.noneOf(Language.class));
    backendService.initialize(initialAnalyzers);

    var newAnalyzers = new BackendService.AnalyzersAndLanguagesEnabled(Set.of(), EnumSet.noneOf(Language.class));
    backendService.restartWithAnalyzers(newAnalyzers);

    // Should still proceed with restart despite close exception
    verify(mockLauncher).close();
  }

  @Test
  void shutdown_should_handle_exception_during_backend_shutdown() {
    var failedFuture = new CompletableFuture<Void>();
    failedFuture.completeExceptionally(new RuntimeException("Shutdown failed"));
    when(mockServer.shutdown()).thenReturn(failedFuture);

    var initialAnalyzers = new BackendService.AnalyzersAndLanguagesEnabled(Set.of(), EnumSet.noneOf(Language.class));
    backendService.initialize(initialAnalyzers);

    // Should not throw, exception is handled
    backendService.shutdown();

    verify(mockServer).shutdown();
    verify(mockLauncher).close();
  }

  @Test
  void shutdown_should_handle_exception_when_closing_launcher() {
    doThrow(new RuntimeException("Close failed")).when(mockLauncher).close();

    var initialAnalyzers = new BackendService.AnalyzersAndLanguagesEnabled(Set.of(), EnumSet.noneOf(Language.class));
    backendService.initialize(initialAnalyzers);

    // Should not throw, exception is handled
    backendService.shutdown();

    verify(mockLauncher).close();
  }

  @Test
  void shutdown_should_close_launcher_even_when_thread_is_interrupted() {
    // Reproduces the Docker container hang: the stdio inbound scheduler thread gets interrupted
    // by handleIncomingMessages' doOnTerminate before the shutdown callback runs.
    // The real ClientJsonRpcLauncher.close() calls awaitTermination() which throws
    // InterruptedException when the thread's interrupt flag is set, preventing cleanup.
    var interruptFlagWhenCloseCalled = new boolean[]{false};
    doAnswer(invocation -> {
      interruptFlagWhenCloseCalled[0] = Thread.currentThread().isInterrupted();
      if (Thread.currentThread().isInterrupted()) {
        // Simulate real ClientJsonRpcLauncher.close() behavior: awaitTermination throws
        throw new IllegalStateException("Interrupted!", new InterruptedException());
      }
      return null;
    }).when(mockLauncher).close();

    var initialAnalyzers = new BackendService.AnalyzersAndLanguagesEnabled(Set.of(), EnumSet.noneOf(Language.class));
    backendService.initialize(initialAnalyzers);

    // Simulate the interrupt that the inbound scheduler disposal sets on this thread
    Thread.currentThread().interrupt();

    backendService.shutdown();

    // The launcher must be called with interrupt flag cleared so awaitTermination succeeds
    verify(mockLauncher).close();
    assertThat(interruptFlagWhenCloseCalled[0])
      .as("clientLauncher.close() should be called with interrupt flag cleared")
      .isFalse();
    // Clean up interrupt flag for test framework
    Thread.interrupted();
  }

}

