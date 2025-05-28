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
package org.sonarsource.sonarqube.mcp.slcore;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarlint.core.rpc.client.ClientJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalysisRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidUpdateFileSystemParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.FileRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.telemetry.TelemetryRpcService;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.ToolCalledParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackendServiceTests {

  @TempDir
  private static Path storagePath;

  private BackendService service;
  private AnalysisRpcService analysisRpcService;
  private TelemetryRpcService telemetryRpcService;
  private FileRpcService fileRpcService;

  @BeforeEach
  void init() {
    var backend = mock(SonarLintRpcServer.class);
    when(backend.initialize(any())).thenReturn(CompletableFuture.completedFuture(null));
    analysisRpcService = mock(AnalysisRpcService.class);
    telemetryRpcService = mock(TelemetryRpcService.class);
    fileRpcService = mock(FileRpcService.class);
    when(backend.getAnalysisService()).thenReturn(analysisRpcService);
    when(backend.getTelemetryService()).thenReturn(telemetryRpcService);
    when(backend.getFileService()).thenReturn(fileRpcService);

    var jsonRpcLauncher = mock(ClientJsonRpcLauncher.class);
    when(jsonRpcLauncher.getServerProxy()).thenReturn(backend);
    service = new BackendService(jsonRpcLauncher, storagePath.toString(), null, System.getProperty("sonarqube.mcp.server.version"),
      "SonarQube MCP Server Tests");
    service.initialize();
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
  void should_notify_tool_called() {
    var toolName = "tool_name";

    service.notifyToolCalled(toolName, true);

    var captor = ArgumentCaptor.forClass(ToolCalledParams.class);
    verify(telemetryRpcService, timeout(1000)).toolCalled(captor.capture());
    assertThat(captor.getValue()).extracting(
      "toolName",
      "succeeded"
    ).containsExactly(toolName, true);
  }

  @Test
  void should_remove_file() {
    var file = URI.create("file:///path/to/file.java");

    service.removeFile(file);

    var captor = ArgumentCaptor.forClass(DidUpdateFileSystemParams.class);
    verify(fileRpcService, timeout(1000)).didUpdateFileSystem(captor.capture());
    assertThat(captor.getValue()).extracting(
      "addedFiles",
      "changedFiles",
      "removedFiles"
    ).containsExactly(List.of(), List.of(), List.of(file));
  }

  @Test
  void should_add_file() {
    var clientFileDto = mock(ClientFileDto.class);

    service.addFile(clientFileDto);

    var captor = ArgumentCaptor.forClass(DidUpdateFileSystemParams.class);
    verify(fileRpcService, timeout(1000)).didUpdateFileSystem(captor.capture());
    assertThat(captor.getValue()).extracting(
      "addedFiles",
      "changedFiles",
      "removedFiles"
    ).containsExactly(List.of(clientFileDto), List.of(), List.of());
  }

}
