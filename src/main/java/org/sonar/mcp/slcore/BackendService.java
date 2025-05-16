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
import static org.sonar.mcp.analysis.LanguageUtils.getSupportedSonarLanguages;

public class BackendService {

  public static final String PROJECT_ID = "sonar-mcp-server";
  private static final McpLogger LOG = McpLogger.getInstance();

  private final CompletableFuture<SonarLintRpcServer> backendFuture = new CompletableFuture<>();
  private final String storagePath;
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
    var pluginResolvedPath = getPluginPath();

    var capabilities = EnumSet.of(BackendCapability.FULL_SYNCHRONIZATION, BackendCapability.PROJECT_SYNCHRONIZATION);
    if (isTelemetryEnabled) {
      capabilities.add(BackendCapability.TELEMETRY);
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
        Set.of(pluginResolvedPath.resolve("sonar-go-plugin-1.21.1.1670.jar"),
          pluginResolvedPath.resolve("sonar-html-plugin-3.19.0.5695.jar"),
          pluginResolvedPath.resolve("sonar-iac-plugin-1.45.0.14930.jar"),
          pluginResolvedPath.resolve("sonar-java-plugin-8.13.0.38826.jar"),
          pluginResolvedPath.resolve("sonar-java-symbolic-execution-plugin-8.13.0.38826.jar"),
          pluginResolvedPath.resolve("sonar-javascript-plugin-10.22.0.32148.jar"),
          pluginResolvedPath.resolve("sonar-kotlin-plugin-3.1.0.7071.jar"),
          pluginResolvedPath.resolve("sonar-php-plugin-3.45.0.12991.jar"),
          pluginResolvedPath.resolve("sonar-python-plugin-5.3.0.21704.jar"),
          pluginResolvedPath.resolve("sonar-ruby-plugin-1.18.1.375.jar"),
          pluginResolvedPath.resolve("sonar-text-plugin-2.22.0.5855.jar"),
          pluginResolvedPath.resolve("sonar-xml-plugin-2.13.0.5938.jar")),
        Map.of(),
        getSupportedSonarLanguages(),
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

  private Path getPluginPath() {
    return Paths.get(pluginPath);
  }

  private void projectOpened() {
    backendFuture.thenAcceptAsync(server -> server
      .getConfigurationService()
      .didAddConfigurationScopes(new DidAddConfigurationScopesParams(
        List.of(new ConfigurationScopeDto(PROJECT_ID, null, false, PROJECT_ID, null))
      )));
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
}
