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
package org.sonar.mcp.slcore;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.rpc.client.ClientJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.ConnectionRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetAllProjectsParams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackendServiceTests {

  @TempDir
  private static Path storagePath;
  @TempDir
  private static Path pluginPath;

  private SonarLintRpcServer backend;
  private BackendService service;
  private AnalysisRpcService analysisRpcService;
  private ConnectionRpcService connectionRpcService;

  @BeforeAll
  static void initAll() {
    System.setProperty("STORAGE_PATH", storagePath.toString());
    System.setProperty("PLUGIN_PATH", pluginPath.toString());
  }

  @BeforeEach
  void init() {
    backend = mock(SonarLintRpcServer.class);
    when(backend.initialize(any())).thenReturn(CompletableFuture.completedFuture(null));
    analysisRpcService = mock(AnalysisRpcService.class);
    connectionRpcService = mock(ConnectionRpcService.class);
    when(backend.getAnalysisService()).thenReturn(analysisRpcService);
    when(backend.getConnectionService()).thenReturn(connectionRpcService);

    var jsonRpcLauncher = mock(ClientJsonRpcLauncher.class);
    when(jsonRpcLauncher.getServerProxy()).thenReturn(backend);
    service = new BackendService(jsonRpcLauncher);
  }

  @AfterAll
  static void cleanup() {
    System.clearProperty("STORAGE_PATH");
    System.clearProperty("PLUGIN_PATH");
  }

  @Test
  void should_analyze_files_and_track() {
    var analysisId = UUID.randomUUID();
    var startTime = System.currentTimeMillis();

    service.analyzeFilesAndTrack(analysisId, List.of(), startTime);

    var captor = ArgumentCaptor.forClass(AnalyzeFilesAndTrackParams.class);
    verify(analysisRpcService, timeout(1000)).analyzeFilesAndTrack(captor.capture());
    assertThat(captor.getValue()).extracting(
      "configurationScopeId",
      "analysisId",
      "filesToAnalyze",
      "extraProperties",
      "shouldFetchServerIssues"
    ).containsExactly(BackendService.PROJECT_ID, analysisId, List.of(), Map.of(), false);
  }

  @Test
  void should_find_all_projects() {
    service.findAllProjects();

    var captor = ArgumentCaptor.forClass(GetAllProjectsParams.class);
    verify(connectionRpcService, timeout(1000)).getAllProjects(captor.capture());
    assertThat(captor.getValue().getTransientConnection().isRight()).isTrue();
  }

}
