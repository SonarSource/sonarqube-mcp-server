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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArchitectureReadConcurrencyTest {
  private final ArchitectureApi architecture = mock(ArchitectureApi.class);
  private ArchitectureGetTool tool;

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
    tool = new ArchitectureGetTool(provider, true, null);
  }

  @Test
  void it_should_refuse_excess_reads_before_fetching_and_leave_intended_reads_available() throws Exception {
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

  private static Tool.Arguments currentArguments() {
    return new Tool.Arguments(Map.of("target", "current", "projectKey", "project", "language", "java"), Map.of());
  }
}
