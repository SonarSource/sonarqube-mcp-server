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

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfiguration;
import org.sonarsource.sonarqube.mcp.log.McpClientLogbackAppender;
import org.sonarsource.sonarqube.mcp.log.McpLogger;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;

public class BackendService {

  public static final String PROJECT_ID = "sonarqube-mcp-server";
  private static final McpLogger LOG = McpLogger.getInstance();

  private final CompletableFuture<SonarLintRpcServer> backendFuture = new CompletableFuture<>();
  private final Path storagePath;
  private final String appVersion;
  private final String userAgent;
  private final String appName;
  private boolean isTelemetryEnabled;
  private ClientJsonRpcLauncher clientLauncher;

  public BackendService(McpServerLaunchConfiguration mcpConfiguration) {
    this.storagePath = mcpConfiguration.getStoragePath();
    this.appVersion = mcpConfiguration.getAppVersion();
    this.userAgent = mcpConfiguration.getUserAgent();
    this.appName = mcpConfiguration.getAppName();
    this.isTelemetryEnabled = mcpConfiguration.isTelemetryEnabled();
  }

  // For tests
  BackendService(ClientJsonRpcLauncher launcher, Path storagePath, String appVersion, String appName) {
    this.clientLauncher = launcher;
    this.storagePath = storagePath;
    this.appVersion = appVersion;
    this.userAgent = appName + " " + appVersion;
    this.appName = appName;
  }

  public CompletableFuture<AnalyzeFilesResponse> analyzeFilesAndTrack(UUID analysisId, List<URI> filesToAnalyze, Long startTime) {
    return backendFuture.thenComposeAsync(server -> server.getAnalysisService().analyzeFilesAndTrack(
      new AnalyzeFilesAndTrackParams(PROJECT_ID, analysisId, filesToAnalyze, Map.of(), false, startTime)));
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

  public void initialize(AnalyzersAndLanguagesEnabled analyzers) {
    try {
      LOG.info("Starting backend service");
      if (clientLauncher == null) {
        var clientToServerOutputStream = new PipedOutputStream();
        var clientToServerInputStream = new PipedInputStream(clientToServerOutputStream);
        var serverToClientOutputStream = new PipedOutputStream();
        var serverToClientInputStream = new PipedInputStream(serverToClientOutputStream);
        new BackendJsonRpcLauncher(clientToServerInputStream, serverToClientOutputStream);
        var rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.detachAndStopAllAppenders();
        var rpcAppender = new McpClientLogbackAppender();
        rpcAppender.start();
        rootLogger.addAppender(rpcAppender);
        clientLauncher = new ClientJsonRpcLauncher(serverToClientInputStream, clientToServerOutputStream, new McpSonarLintRpcClient());
      }
      var backend = clientLauncher.getServerProxy();
      initRpcServer(backend, analyzers).get(1, TimeUnit.MINUTES);
      backendFuture.complete(backend);
      LOG.info("Backend service initialized");
      projectOpened();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      backendFuture.cancel(true);
    }
  }

  private CompletableFuture<Void> initRpcServer(SonarLintRpcServer rpcServer, AnalyzersAndLanguagesEnabled analyzersInStorage) {
    var capabilities = EnumSet.of(BackendCapability.FULL_SYNCHRONIZATION, BackendCapability.PROJECT_SYNCHRONIZATION);
    if (isTelemetryEnabled) {
      capabilities.add(BackendCapability.TELEMETRY);
    }

    LOG.info("Using discovered analyzers, enabling languages: " + analyzersInStorage.enabledLanguages);

    return rpcServer.initialize(
      new InitializeParams(
        new ClientConstantInfoDto(
          appName,
          userAgent),
        new TelemetryClientConstantAttributesDto("mcpserver", appName, appVersion, "MCP", emptyMap()),
        new HttpConfigurationDto(
          new SslConfigurationDto(null, null, null, null, null, null),
          null, null, null, null),
        null,
        capabilities,
        storagePath,
        getWorkDir(),
        analyzersInStorage.analyzerPaths,
        Map.of(),
        analyzersInStorage.enabledLanguages,
        Set.of(),
        emptySet(),
        null,
        null,
        null,
        null,
        false,
        new LanguageSpecificRequirements(null, null),
        false,
        null));
  }

  private void projectOpened() {
    backendFuture.thenAcceptAsync(server -> server
      .getConfigurationService()
      .didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(PROJECT_ID, null, false, PROJECT_ID, null)))));
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

  public record AnalyzersAndLanguagesEnabled(Set<Path> analyzerPaths, EnumSet<Language> enabledLanguages) {
  }

}
