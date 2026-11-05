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
package org.sonarsource.sonarqube.mcp.tools.architecture;

import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;
import org.sonarsource.sonarqube.mcp.serverapi.branches.ProjectBranchesApi;
import org.sonarsource.sonarqube.mcp.serverapi.exception.ForbiddenException;
import org.sonarsource.sonarqube.mcp.tools.Tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArchitectureReadConcurrencyTest {
  private final ArchitectureApi architecture = mock(ArchitectureApi.class);
  private ArchitectureGetTool tool;
  private ArchitectureSetTool setTool;

  @BeforeEach
  void setUp() {
    var provider = mock(ServerApiProvider.class);
    var server = mock(ServerApi.class);
    var branches = mock(ProjectBranchesApi.class);
    when(provider.get(null)).thenReturn(server);
    when(server.isSonarQubeCloud()).thenReturn(true);
    when(server.getOrganization()).thenReturn("org");
    when(server.resolveOrganizationUuid(any())).thenReturn("organization-id");
    when(server.projectBranchesApi()).thenReturn(branches);
    when(branches.getProjectId(anyString())).thenReturn(Optional.of("branch-id"));
    when(server.architectureApi()).thenReturn(architecture);
    when(architecture.getIntended(anyString(), anyString())).thenReturn(List.of());
    var tools = ArchitectureTools.create(provider, true, null);
    tool = (ArchitectureGetTool) tools.get(0);
    setTool = (ArchitectureSetTool) tools.get(1);
  }

  @Test
  void it_should_time_out_excess_reads_before_fetching_and_leave_intended_reads_available() throws Exception {
    var entered = new CountDownLatch(2);
    var release = new CountDownLatch(1);
    when(architecture.getStructure(anyString(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
      entered.countDown();
      if (!release.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Test did not release the backend");
      }
      return List.of();
    });
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> tool.execute(currentArguments()));
      var second = executor.submit(() -> tool.execute(currentArguments()));
      try {
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        var excessRead = currentArguments();
        assertThatThrownBy(() -> tool.execute(excessRead)).isInstanceOf(IllegalStateException.class)
          .hasMessage("Architecture reads are busy. Try again shortly.");
        verify(architecture, times(2)).getStructure(anyString(), anyString(), anyString(), anyString());
        assertThat(tool.execute(new Tool.Arguments(Map.of("target", "intended", "projectKey", "project"), Map.of())).isError()).isFalse();
      } finally {
        release.countDown();
      }
      assertThat(first.get(5, TimeUnit.SECONDS).isError()).isFalse();
      assertThat(second.get(5, TimeUnit.SECONDS).isError()).isFalse();
      assertThat(tool.execute(currentArguments()).isError()).isFalse();
      verify(architecture, times(3)).getStructure(anyString(), anyString(), anyString(), anyString());
    }
  }

  @Test
  void it_should_report_unavailable_diagnostics_when_current_capacity_stays_busy() throws Exception {
    var entered = new CountDownLatch(2);
    var release = new CountDownLatch(1);
    when(architecture.getStructure(anyString(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
      entered.countDown();
      if (!release.await(10, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Test did not release the backend");
      }
      return List.of();
    });
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> tool.execute(currentArguments()));
      var second = executor.submit(() -> tool.execute(currentArguments()));
      try {
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        var preview = setTool.execute(dryRunArguments());
        assertThat(preview.isError()).isFalse();
        assertThat(((TextContent) preview.toCallToolResult().content().getFirst()).text()).contains("Architecture reads are busy", "Structural validation still applies");
        verify(architecture, times(2)).getStructure(anyString(), anyString(), anyString(), anyString());
        assertThat(tool.execute(new Tool.Arguments(Map.of("target", "intended", "projectKey", "project"), Map.of())).isError()).isFalse();
      } finally {
        release.countDown();
      }
      assertThat(first.get(5, TimeUnit.SECONDS).isError()).isFalse();
      assertThat(second.get(5, TimeUnit.SECONDS).isError()).isFalse();
      assertThat(tool.execute(currentArguments()).isError()).isFalse();
      verify(architecture, times(3)).getStructure(anyString(), anyString(), anyString(), anyString());
    }
  }

  @Test
  void it_should_wait_for_capacity_only_once_for_a_five_language_dry_run() throws Exception {
    var entered = new CountDownLatch(2);
    var release = new CountDownLatch(1);
    when(architecture.getStructure(anyString(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
      entered.countDown();
      if (!release.await(30, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Test did not release the backend");
      }
      return List.of();
    });
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> tool.execute(currentArguments()));
      var second = executor.submit(() -> tool.execute(currentArguments()));
      try {
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        var diagnostic = executor.submit(() -> setTool.execute(multilingualDryRunArguments()));
        var preview = diagnostic.get(8, TimeUnit.SECONDS);
        assertThat(preview.isError()).isFalse();
        var response = ((TextContent) preview.toCallToolResult().content().getFirst()).text();
        assertThat(response).contains("Architecture reads are busy", "Structural validation still applies");
        for (var language : List.of("java", "cs", "js", "ts", "py")) {
          assertThat(response).contains(language + ": pattern and physical hierarchy checks unavailable");
        }
        verify(architecture, times(2)).getStructure(anyString(), anyString(), anyString(), anyString());
        assertThat(tool.execute(new Tool.Arguments(Map.of("target", "intended", "projectKey", "project"), Map.of())).isError()).isFalse();
      } finally {
        release.countDown();
      }
      assertThat(first.get(5, TimeUnit.SECONDS).isError()).isFalse();
      assertThat(second.get(5, TimeUnit.SECONDS).isError()).isFalse();
      assertThat(tool.execute(currentArguments()).isError()).isFalse();
      verify(architecture, times(3)).getStructure(anyString(), anyString(), anyString(), anyString());
    }
  }

  @Test
  void it_should_queue_a_third_read_until_capacity_is_available() throws Exception {
    var entered = new CountDownLatch(2);
    var release = new CountDownLatch(1);
    var active = new java.util.concurrent.atomic.AtomicInteger();
    var maximum = new java.util.concurrent.atomic.AtomicInteger();
    when(architecture.getStructure(anyString(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
      maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
      entered.countDown();
      try {
        if (!release.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Test did not release the backend");
        }
        return List.of();
      } finally {
        active.decrementAndGet();
      }
    });
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> tool.execute(currentArguments()));
      var second = executor.submit(() -> tool.execute(currentArguments()));
      try {
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        var queued = executor.submit(() -> tool.execute(currentArguments()));
        assertThatThrownBy(() -> queued.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        verify(architecture, times(2)).getStructure(anyString(), anyString(), anyString(), anyString());
        release.countDown();
        assertThat(queued.get(5, TimeUnit.SECONDS).isError()).isFalse();
      } finally {
        release.countDown();
      }
      assertThat(first.get(5, TimeUnit.SECONDS).isError()).isFalse();
      assertThat(second.get(5, TimeUnit.SECONDS).isError()).isFalse();
      assertThat(maximum.get()).isEqualTo(2);
      verify(architecture, times(3)).getStructure(anyString(), anyString(), anyString(), anyString());
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void it_should_share_capacity_between_current_reads_and_intended_dry_runs(boolean diagnosticsFirst) throws Exception {
    var entered = new CountDownLatch(2);
    var release = new CountDownLatch(1);
    var active = new java.util.concurrent.atomic.AtomicInteger();
    var maximum = new java.util.concurrent.atomic.AtomicInteger();
    when(architecture.getStructure(anyString(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
      maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
      entered.countDown();
      try {
        if (!release.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Test did not release the backend");
        }
        return List.of();
      } finally {
        active.decrementAndGet();
      }
    });
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> diagnosticsFirst ? setTool.execute(dryRunArguments()) : tool.execute(currentArguments()));
      var second = executor.submit(() -> diagnosticsFirst ? setTool.execute(dryRunArguments()) : tool.execute(currentArguments()));
      try {
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        var queued = executor.submit(() -> diagnosticsFirst ? tool.execute(currentArguments()) : setTool.execute(dryRunArguments()));
        assertThatThrownBy(() -> queued.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        verify(architecture, times(2)).getStructure(anyString(), anyString(), anyString(), anyString());
        release.countDown();
        assertThat(queued.get(5, TimeUnit.SECONDS).isError()).isFalse();
      } finally {
        release.countDown();
      }
      assertThat(first.get(5, TimeUnit.SECONDS).isError()).isFalse();
      assertThat(second.get(5, TimeUnit.SECONDS).isError()).isFalse();
      assertThat(maximum.get()).isEqualTo(2);
      verify(architecture, never()).createIntended(anyString(), anyString(), any());
      verify(architecture, never()).patchIntended(anyString(), anyString(), any());
      verify(architecture, times(3)).getStructure(anyString(), anyString(), anyString(), anyString());
    }
  }

  @ParameterizedTest
  @CsvSource({"false,false", "true,false", "false,true", "true,true"})
  void it_should_stop_interrupted_diagnostics_before_replacement(boolean dryRun, boolean duringGraphRead) {
    var arguments = new HashMap<>(dryRunArguments().toMap());
    arguments.put("dryRun", dryRun);
    var request = new Tool.Arguments(arguments, Map.of());
    if (duringGraphRead) {
      when(architecture.getStructure(anyString(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted discovery read", new InterruptedException());
      });
    } else {
      when(architecture.getIntended(anyString(), anyString())).thenAnswer(invocation -> {
        Thread.currentThread().interrupt();
        return List.of();
      });
    }
    try {
      assertThatThrownBy(() -> setTool.execute(request)).isInstanceOf(IllegalStateException.class)
        .hasCauseInstanceOf(InterruptedException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      verify(architecture, times(1)).getIntended(anyString(), anyString());
      verify(architecture, never()).createIntended(anyString(), anyString(), any());
      verify(architecture, never()).patchIntended(anyString(), anyString(), any());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void it_should_preserve_interruption_without_releasing_an_unacquired_slot() throws Exception {
    var entered = new CountDownLatch(2);
    var release = new CountDownLatch(1);
    when(architecture.getStructure(anyString(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
      entered.countDown();
      if (!release.await(15, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Test did not release the backend");
      }
      return List.of();
    });
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> tool.execute(currentArguments()));
      var second = executor.submit(() -> tool.execute(currentArguments()));
      try {
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        var interrupted = executor.submit(() -> {
          var arguments = currentArguments();
          Thread.currentThread().interrupt();
          try {
            assertThatThrownBy(() -> tool.execute(arguments)).isInstanceOf(IllegalStateException.class)
              .hasMessage("Architecture read interrupted while waiting for capacity.")
              .hasCauseInstanceOf(InterruptedException.class);
            return Thread.currentThread().isInterrupted();
          } finally {
            Thread.interrupted();
          }
        });
        assertThat(interrupted.get(5, TimeUnit.SECONDS)).isTrue();
        var arguments = currentArguments();
        assertThatThrownBy(() -> tool.execute(arguments)).isInstanceOf(IllegalStateException.class)
          .hasMessage("Architecture reads are busy. Try again shortly.");
        verify(architecture, times(2)).getStructure(anyString(), anyString(), anyString(), anyString());
      } finally {
        release.countDown();
      }
      assertThat(first.get(5, TimeUnit.SECONDS).isError()).isFalse();
      assertThat(second.get(5, TimeUnit.SECONDS).isError()).isFalse();
      assertThat(tool.execute(currentArguments()).isError()).isFalse();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void it_should_release_slots_after_permission_errors_and_timeouts(boolean timeout) {
    RuntimeException failure = timeout ? new CompletionException(new TimeoutException("Read timed out")) : new ForbiddenException("Access denied");
    when(architecture.getStructure(anyString(), anyString(), anyString(), anyString())).thenThrow(failure);
    var arguments = currentArguments();
    for (var attempt = 0; attempt < 3; attempt++) {
      assertThatThrownBy(() -> tool.execute(arguments)).isSameAs(failure);
    }
    doReturn(List.of()).when(architecture).getStructure(anyString(), anyString(), anyString(), anyString());
    assertThat(tool.execute(currentArguments()).isError()).isFalse();
  }

  private static Tool.Arguments multilingualDryRunArguments() {
    var models = List.of("java", "cs", "js", "ts", "py").stream()
      .map(language -> "{\"type\":\"namespace\",\"language\":\"" + language
        + "\",\"groups\":[{\"label\":\"App\",\"patterns\":[\"app:**\"]}],\"constraints\":[]}")
      .collect(Collectors.joining(",", "[", "]"));
    return new Tool.Arguments(Map.of("target", "intended", "projectKey", "project", "dryRun", true, "model", models), Map.of());
  }

  private static Tool.Arguments dryRunArguments() {
    return new Tool.Arguments(Map.of("target", "intended", "projectKey", "project", "dryRun", true, "model", """
      [{"type":"namespace","language":"java","groups":[{"label":"App","patterns":["app:**"]}],"constraints":[]}]
      """), Map.of());
  }

  private static Tool.Arguments currentArguments() {
    return new Tool.Arguments(Map.of("target", "current", "projectKey", "project", "language", "java"), Map.of());
  }
}
