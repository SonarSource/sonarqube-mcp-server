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
package org.sonarsource.sonarqube.mcp.slcore;

import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import java.io.Closeable;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import jakarta.annotation.Nullable;
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
import org.sonarsource.sonarlint.core.rpc.protocol.backend.log.LogLevel;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.StandaloneRuleConfigDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.UpdateStandaloneRulesConfigurationParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.McpTransportMode;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.McpTransportModeUsedParams;
import org.sonarsource.sonarlint.core.rpc.protocol.client.telemetry.ToolCalledParams;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfiguration;
import org.sonarsource.sonarqube.mcp.log.McpLogger;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;

public class BackendService {

  public static final String PROJECT_ID = "sonarqube-mcp-server";
  private static final McpLogger LOG = McpLogger.getInstance();

  private final AtomicReference<CompletableFuture<SonarLintRpcServer>> backendFuture = new AtomicReference<>(new CompletableFuture<>());
  private final Path storagePath;
  private final Path logFilePath;
  private final String appVersion;
  private final String userAgent;
  private final String appName;
  private boolean isTelemetryEnabled;
  private final boolean isFileLoggingDisabled;
  private ClientJsonRpcLauncher clientLauncher;
  private BackendJsonRpcLauncher backendLauncher;
  private PipedOutputStream clientToServerOutputStream;
  private PipedInputStream clientToServerInputStream;
  private PipedOutputStream serverToClientOutputStream;
  private PipedInputStream serverToClientInputStream;
  private Supplier<ClientJsonRpcLauncher> launcherFactory;
  private McpTransportMode transportMode;
  private volatile boolean isInitialized = false;

  public BackendService(McpServerLaunchConfiguration mcpConfiguration) {
    this.storagePath = mcpConfiguration.getStoragePath();
    this.logFilePath = mcpConfiguration.getLogFilePath();
    this.appVersion = mcpConfiguration.getAppVersion();
    this.userAgent = mcpConfiguration.getUserAgent();
    this.appName = McpServerLaunchConfiguration.getAppName();
    this.isTelemetryEnabled = mcpConfiguration.isTelemetryEnabled();
    this.isFileLoggingDisabled = mcpConfiguration.isFileLoggingDisabled();
    if (!mcpConfiguration.isHttpEnabled()) {
      this.transportMode = McpTransportMode.STDIO;
    } else if (mcpConfiguration.isHttpsEnabled()) {
      this.transportMode = McpTransportMode.HTTPS;
    } else {
      this.transportMode = McpTransportMode.HTTP;
    }
  }

  // For tests
  BackendService(ClientJsonRpcLauncher launcher, Path storagePath, String appVersion, String appName) {
    this.clientLauncher = launcher;
    this.launcherFactory = () -> launcher;
    this.storagePath = storagePath;
    this.logFilePath = storagePath.resolve("mcp.log");
    this.appVersion = appVersion;
    this.userAgent = appName + " " + appVersion;
    this.appName = appName;
    this.isFileLoggingDisabled = false;
  }

  public CompletableFuture<AnalyzeFilesResponse> analyzeFilesAndTrack(UUID analysisId, List<URI> filesToAnalyze) {
    return backendFuture.get().thenComposeAsync(server -> server.getAnalysisService().analyzeFilesAndTrack(
      new AnalyzeFilesAndTrackParams(PROJECT_ID, analysisId, filesToAnalyze, Map.of(), false)));
  }

  public void addFile(ClientFileDto clientFileDto) {
    LOG.info("Adding file " + clientFileDto.getUri());
    backendFuture.get().thenAcceptAsync(server -> server.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(List.of(clientFileDto), List.of(), List.of())));
  }

  public ClientFileDto toClientFileDto(Path filePath, String content, @Nullable Language language, boolean isTest) {
    return new ClientFileDto(filePath.toUri(), filePath, PROJECT_ID, isTest, Charset.defaultCharset().toString(), filePath,
      content, language, true);
  }

  public void removeFile(URI file) {
    LOG.info("Removing file " + file);
    backendFuture.get().thenAcceptAsync(server -> server.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(List.of(), List.of(), List.of(file))));
  }

  public void notifyToolCalled(String toolName, boolean succeeded) {
    backendFuture.get().thenAcceptAsync(server -> server.getTelemetryService().toolCalled(new ToolCalledParams(toolName, succeeded)));
  }

  public void notifySonarQubeIdeIntegration() {
    backendFuture.get().thenAcceptAsync(server -> server.getTelemetryService().mcpIntegrationEnabled());
  }

  public void notifyTransportModeUsed() {
    backendFuture.get().thenAcceptAsync(server -> server.getTelemetryService().mcpTransportModeUsed(new McpTransportModeUsedParams(transportMode)));
  }

  public Path getWorkDir() {
    return getSonarLintUserHome().resolve("work");
  }

  private Path getSonarLintUserHome() {
    return storagePath.resolve(".sonarlint");
  }

  public boolean initialize(AnalyzersAndLanguagesEnabled analyzers) {
    backendFuture.updateAndGet(current -> current.isCompletedExceptionally() ? new CompletableFuture<>() : current);
    try {
      LOG.info("Starting backend service");
      if (clientLauncher == null && launcherFactory != null) {
        clientLauncher = launcherFactory.get();
      }
      if (clientLauncher == null) {
        clientToServerOutputStream = new PipedOutputStream();
        clientToServerInputStream = new PipedInputStream(clientToServerOutputStream);
        serverToClientOutputStream = new PipedOutputStream();
        serverToClientInputStream = new PipedInputStream(serverToClientOutputStream);
        backendLauncher = new BackendJsonRpcLauncher(clientToServerInputStream, serverToClientOutputStream);
        var rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.detachAndStopAllAppenders();
        if (!isFileLoggingDisabled) {
          var fileAppender = new RollingFileAppender<ILoggingEvent>();
          fileAppender.setContext(rootLogger.getLoggerContext());
          fileAppender.setName("FILE");
          fileAppender.setFile(logFilePath.toAbsolutePath().toString());
          var policy = new TimeBasedRollingPolicy<ILoggingEvent>();
          policy.setContext(rootLogger.getLoggerContext());
          policy.setFileNamePattern(storagePath.toAbsolutePath() + "/logs/mcp.%d{yyyy-MM-dd}.log");
          policy.setMaxHistory(10);
          policy.setParent(fileAppender);
          policy.start();
          fileAppender.setRollingPolicy(policy);
          var encoder = new PatternLayoutEncoder();
          encoder.setContext(rootLogger.getLoggerContext());
          encoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
          encoder.start();
          fileAppender.setEncoder(encoder);
          fileAppender.start();
          rootLogger.addAppender(fileAppender);
        }
        clientLauncher = new ClientJsonRpcLauncher(serverToClientInputStream, clientToServerOutputStream, new McpSonarLintRpcClient());
      }
      var backend = clientLauncher.getServerProxy();
      initRpcServer(backend, analyzers).get(1, TimeUnit.MINUTES);
      backendFuture.get().complete(backend);
      isInitialized = true;
      LOG.info("Backend service initialized");
      projectOpened();
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      failInitialization(e);
    } catch (Exception e) {
      failInitialization(e);
    }
    return false;
  }

  private void failInitialization(Exception failure) {
    LOG.error("Backend service initialization failed", failure);
    backendFuture.get().completeExceptionally(failure);
    isInitialized = false;
    boolean wasInterrupted = Thread.interrupted();
    try {
      closeRpcStreams();
      if (clientLauncher != null) {
        clientLauncher.close();
      }
    } catch (Exception e) {
      LOG.warn("Unable to close launcher after initialization failure: " + e.getMessage());
    } finally {
      clientLauncher = null;
      if (wasInterrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void closeRpcStreams() {
    closeRpcResource(backendLauncher);
    closeRpcResource(clientToServerOutputStream);
    closeRpcResource(clientToServerInputStream);
    closeRpcResource(serverToClientOutputStream);
    closeRpcResource(serverToClientInputStream);
    backendLauncher = null;
    clientToServerOutputStream = null;
    clientToServerInputStream = null;
    serverToClientOutputStream = null;
    serverToClientInputStream = null;
  }

  private static void closeRpcResource(@Nullable Closeable resource) {
    if (resource != null) {
      try {
        resource.close();
      } catch (Exception e) {
        LOG.warn("Unable to close backend RPC resource: " + e.getMessage());
      }
    }
  }

  /**
   * Restarts the backend with new analyzers. This shuts down the current backend
   * and initializes a new one with the provided analyzers.
   */
  public void restartWithAnalyzers(AnalyzersAndLanguagesEnabled analyzers) {
    if (!isInitialized) {
      LOG.info("Backend not yet initialized, initializing with analyzers");
      if (!initialize(analyzers)) {
        throw new IllegalStateException("Backend initialization failed");
      }
      return;
    }

    LOG.info("Restarting backend with new analyzers...");
    try {
      // Shut down current backend
      var currentBackend = backendFuture.get().getNow(null);
      if (currentBackend != null) {
        currentBackend.shutdown().get(10, TimeUnit.SECONDS);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      LOG.error("Interrupted while shutting down backend for restart", e);
      throw new IllegalStateException("Backend restart interrupted", e);
    } catch (Exception e) {
      LOG.warn("Error during backend shutdown for restart, proceeding anyway: " + e.getMessage());
    }

    // Close the old launcher
    try {
      closeRpcStreams();
      if (clientLauncher != null) {
        clientLauncher.close();
      }
    } catch (Exception e) {
      LOG.warn("Error closing launcher during restart: " + e.getMessage());
    }

    // Reset state for clean restart
    clientLauncher = null;
    isInitialized = false;
    backendFuture.set(new CompletableFuture<>());

    // Initialize with new analyzers
    if (!initialize(analyzers)) {
      throw new IllegalStateException("Backend restart failed");
    }
    LOG.info("Backend restarted with new analyzers");
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
        getSonarLintUserHome().toString(),
        null,
        false,
        new LanguageSpecificRequirements(null, false),
        false,
        null,
        LogLevel.DEBUG));
  }

  private void projectOpened() {
    backendFuture.get().thenAcceptAsync(server -> server
      .getConfigurationService()
      .didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(PROJECT_ID, null, false, PROJECT_ID, null)))));
  }

  public void shutdown() {
    try {
      var aliveBackend = backendFuture.get().getNow(null);
      if (aliveBackend != null) {
        aliveBackend.shutdown().get(10, TimeUnit.SECONDS);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      LOG.error("Unable to shutdown the MCP backend", e);
    } finally {
      // Clear interrupt flag so clientLauncher.close() (which calls awaitTermination) can complete
      boolean wasInterrupted = Thread.interrupted();
      try {
        closeRpcStreams();
        if (clientLauncher != null) {
          clientLauncher.close();
        }
      } catch (Exception e) {
        LOG.error("Unable to stop the MCP backend launcher", e);
      }
      if (wasInterrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  public void updateRulesConfiguration(Map<String, StandaloneRuleConfigDto> ruleConfigurationByKey) {
    backendFuture.get().thenAccept(server -> {
      var newActiveRules = new HashMap<String, StandaloneRuleConfigDto>();
      server.getRulesService().listAllStandaloneRulesDefinitions().join().getRulesByKey().forEach((key, value) ->
      // disable all standalone rules
      newActiveRules.put(key, new StandaloneRuleConfigDto(false, Map.of())));
      // enable custom ones
      newActiveRules.putAll(ruleConfigurationByKey);
      server.getRulesService().updateStandaloneRulesConfiguration(new UpdateStandaloneRulesConfigurationParams(newActiveRules));
    });
  }

  public record AnalyzersAndLanguagesEnabled(Set<Path> analyzerPaths, EnumSet<Language> enabledLanguages) {
  }

}
