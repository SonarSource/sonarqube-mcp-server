/*
 * Sonar MCP Server
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
package org.sonar.mcp.slcore;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.sonar.mcp.analysis.LanguageUtils;
import org.sonar.mcp.configuration.McpServerLaunchConfiguration;
import org.sonar.mcp.log.McpLogger;
import org.sonarsource.sonarlint.core.rpc.client.ClientJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.impl.BackendJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidUpdateFileSystemParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.ClientConstantInfoDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.HttpConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.LanguageSpecificRequirements;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.SslConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.TelemetryClientConstantAttributesDto;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.ToolCalledParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;

public class BackendService {

  public static final String PROJECT_ID = "sonar-mcp-server";
  private static final McpLogger LOG = McpLogger.getInstance();

  private final CompletableFuture<SonarLintRpcServer> backendFuture = new CompletableFuture<>();
  private final String storagePath;
  @Nullable
  private final String pluginPath;
  private final String appVersion;
  private final String userAgent;
  private final String appName;
  private boolean isTelemetryEnabled;
  private ClientJsonRpcLauncher clientLauncher;

  public BackendService(McpServerLaunchConfiguration mcpConfiguration) {
    this.storagePath = mcpConfiguration.getStoragePath();
    this.pluginPath = mcpConfiguration.getPluginPath();
    this.appVersion = mcpConfiguration.getAppVersion();
    this.userAgent = mcpConfiguration.getUserAgent();
    this.appName = mcpConfiguration.getAppName();
    this.isTelemetryEnabled = mcpConfiguration.isTelemetryEnabled();
    initBackendService();
  }

  // For tests
  BackendService(ClientJsonRpcLauncher launcher, String storagePath, String pluginPath, String appVersion, String appName) {
    this.clientLauncher = launcher;
    this.storagePath = storagePath;
    this.pluginPath = pluginPath;
    this.appVersion = appVersion;
    this.userAgent = appName + " " + appVersion;
    this.appName = appName;
    initBackendService();
  }

  private void initBackendService() {
    createServiceStartingTask();
  }

  public CompletableFuture<AnalyzeFilesResponse> analyzeFilesAndTrack(UUID analysisId, List<URI> filesToAnalyze, Long startTime) {
    return backendFuture.thenComposeAsync(server ->
      server.getAnalysisService().analyzeFilesAndTrack(
        new AnalyzeFilesAndTrackParams(PROJECT_ID, analysisId, filesToAnalyze, Map.of(), false, startTime)
      ));
  }

  public void addFile(ClientFileDto clientFileDto) {
    LOG.info("Adding file " + clientFileDto.getUri());
    backendFuture.thenAcceptAsync(server -> server.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(List.of(clientFileDto), List.of(), List.of())));
  }

  public ClientFileDto toClientFileDto(Path filePath, String content, @Nullable Language language) {
    return new ClientFileDto(filePath.toUri(), filePath, PROJECT_ID, false, Charset.defaultCharset().toString(), filePath,
      content, language, true);
  }

  public void removeFile(URI file) {
    LOG.info("Removing file " + file);
    backendFuture.thenAcceptAsync(server -> server.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(List.of(), List.of(), List.of(file))));
  }

  public void notifyToolCalled(String toolName, boolean succeeded) {
    backendFuture.thenAcceptAsync(server -> server.getTelemetryService().toolCalled(new ToolCalledParams(toolName, succeeded)));
  }

  public Path getWorkDir() {
    return Paths.get(System.getProperty("user.home")).resolve(".sonarlint");
  }

  private void createServiceStartingTask() {
    try {
      LOG.info("Starting backend service");
      if (clientLauncher == null) {
        var clientToServerOutputStream = new PipedOutputStream();
        var clientToServerInputStream = new PipedInputStream(clientToServerOutputStream);
        var serverToClientOutputStream = new PipedOutputStream();
        var serverToClientInputStream = new PipedInputStream(serverToClientOutputStream);
        new BackendJsonRpcLauncher(clientToServerInputStream, serverToClientOutputStream);
        clientLauncher = new ClientJsonRpcLauncher(serverToClientInputStream, clientToServerOutputStream, new McpSonarLintRpcClient());
      }
      var backend = clientLauncher.getServerProxy();
      initRpcServer(backend).get(1, TimeUnit.MINUTES);
      backendFuture.complete(backend);
      LOG.info("Backend service initialized");
      projectOpened();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      backendFuture.cancel(true);
    }
  }

  private CompletableFuture<Void> initRpcServer(SonarLintRpcServer rpcServer) {
    var capabilities = EnumSet.of(BackendCapability.FULL_SYNCHRONIZATION, BackendCapability.PROJECT_SYNCHRONIZATION);
    if (isTelemetryEnabled) {
      capabilities.add(BackendCapability.TELEMETRY);
    }

    var analyzerPaths = Set.<Path>of();
    var enabledLanguages = EnumSet.noneOf(Language.class);
    if (getPluginPath() != null) {
      var analyzersAndLanguages = findAnalyzersFromPluginDirectory();
      analyzerPaths = analyzersAndLanguages.analyzerPaths;
      enabledLanguages = analyzersAndLanguages.enabledLanguages;
      LOG.info("Enabling languages: " + enabledLanguages);
    }

    return rpcServer.initialize(
      new InitializeParams(
        new ClientConstantInfoDto(
          appName,
          userAgent
        ),
        new TelemetryClientConstantAttributesDto("mcpserver", appName, appVersion, "MCP", emptyMap()),
        new HttpConfigurationDto(
          new SslConfigurationDto(null, null, null, null, null, null),
          null, null, null, null),
        null,
        capabilities,
        getStoragePath(),
        getWorkDir(),
        analyzerPaths,
        Map.of(),
        enabledLanguages,
        Set.of(),
        emptySet(),
        null,
        null,
        null,
        null,
        false,
        new LanguageSpecificRequirements(null, null),
        false,
        null
      )
    );
  }

  private Path getStoragePath() {
    return Paths.get(storagePath);
  }

  @Nullable
  private Path getPluginPath() {
    return pluginPath == null ? null : Paths.get(pluginPath);
  }

  private void projectOpened() {
    backendFuture.thenAcceptAsync(server -> server
      .getConfigurationService()
      .didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(PROJECT_ID, null, false, PROJECT_ID, null))
      )));
  }

  private AnalyzersAndLanguagesEnabled findAnalyzersFromPluginDirectory() {
    var pluginsPaths = new HashSet<Path>();
    var languages = EnumSet.noneOf(Language.class);
    var pluginFolder = getPluginPath();
    if (pluginFolder != null && Files.isDirectory(pluginFolder)) {
      try (var directoryStream = Files.newDirectoryStream(pluginFolder, "*.jar")) {
        var analyzers = LanguageUtils.getSupportedLanguagesPerAnalyzers();
        for (var path : directoryStream) {
          var fileName = path.getFileName().toString();
          analyzers.forEach((analyzer, supportedLanguages) -> {
            if (fileName.contains(analyzer)) {
              pluginsPaths.add(path);
              languages.addAll(supportedLanguages);
            }
          });
        }
      } catch (IOException e) {
        LOG.error("Unable to find analyzers in plugin folder", e);
      }
    }
    return new AnalyzersAndLanguagesEnabled(pluginsPaths, languages);
  }

  public void shutdown() {
    try {
      var aliveBackend = backendFuture.getNow(null);
      if (aliveBackend != null) {
        aliveBackend.shutdown().get(10, TimeUnit.SECONDS);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      LOG.error("Unable to shutdown the MCP backend", e);
    } finally {
      try {
        clientLauncher.close();
      } catch (Exception e) {
        LOG.error("Unable to stop the MCP backend launcher", e);
      }
    }
  }

  private record AnalyzersAndLanguagesEnabled(Set<Path> analyzerPaths, EnumSet<Language> enabledLanguages) {}

}
