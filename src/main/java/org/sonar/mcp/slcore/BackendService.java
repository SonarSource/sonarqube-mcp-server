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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.mcp.log.McpLogger;
import org.sonarsource.sonarlint.core.rpc.client.ClientJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.impl.BackendJsonRpcLauncher;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.binding.BindingConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.common.TransientSonarCloudConnectionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.config.SonarCloudConnectionConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetAllProjectsParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.connection.projects.GetAllProjectsResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidUpdateFileSystemParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.BackendCapability;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.ClientConstantInfoDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.HttpConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.LanguageSpecificRequirements;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.SonarCloudAlternativeEnvironmentDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.SonarQubeCloudRegionDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.SslConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.TelemetryClientConstantAttributesDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Either;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarlint.core.rpc.protocol.common.SonarCloudRegion;
import org.sonarsource.sonarlint.core.rpc.protocol.common.TokenDto;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static org.sonar.mcp.analysis.LanguageUtils.getSupportedSonarLanguages;

public class BackendService {
  private static final McpLogger LOG = McpLogger.getInstance();

  public static final String PROJECT_ID = "sonar-mcp-server";
  private static final String CONNECTION_ID = PROJECT_ID + "-connection";
  private final CompletableFuture<SonarLintRpcServer> backendFuture = new CompletableFuture<>();
  private String storagePath;
  private String pluginPath;
  private URI sonarqubeCloudUri;
  @Nullable
  private String sonarqubeCloudToken;
  @Nullable
  private String sonarqubeCloudOrg;
  @Nullable
  private String sonarqubeCloudProjectKey;
  private boolean isConnectedToSonarQubeCloud = false;
  private ClientJsonRpcLauncher clientLauncher;

  public BackendService(Map<String, String> environment) {
    initBackendService(environment);
  }

  // For tests
  BackendService(ClientJsonRpcLauncher launcher) {
    this.clientLauncher = launcher;
    initBackendService(System.getenv());
  }

  public boolean isSonarQubeCloudOrgAndTokenSet() {
    return sonarqubeCloudToken != null && sonarqubeCloudOrg != null;
  }

  private void initBackendService(Map<String, String> environment) {
    this.storagePath = getValueViaEnvOrProperty(environment, "STORAGE_PATH");
    Objects.requireNonNull(this.storagePath, "STORAGE_PATH environment variable or property must be set");
    this.pluginPath = getValueViaEnvOrProperty(environment, "PLUGIN_PATH");
    Objects.requireNonNull(this.pluginPath, "PLUGIN_PATH environment variable or property must be set");
    var sonarqubeCloudUrl = getValueViaEnvOrProperty(environment, "SONARQUBE_CLOUD_URL");
    this.sonarqubeCloudUri = sonarqubeCloudUrl == null ? null : URI.create(sonarqubeCloudUrl);
    this.sonarqubeCloudToken = getValueViaEnvOrProperty(environment, "SONARQUBE_CLOUD_TOKEN");
    this.sonarqubeCloudOrg = getValueViaEnvOrProperty(environment, "SONARQUBE_CLOUD_ORG");
    this.sonarqubeCloudProjectKey = getValueViaEnvOrProperty(environment, "SONARQUBE_CLOUD_PROJECT_KEY");

    if (sonarqubeCloudToken != null && sonarqubeCloudOrg != null && sonarqubeCloudProjectKey != null) {
      isConnectedToSonarQubeCloud = true;
      LOG.info("Connected to SonarQube Cloud");
    }

    createServiceStartingTask();
  }

  public CompletableFuture<AnalyzeFilesResponse> analyzeFilesAndTrack(UUID analysisId, List<URI> filesToAnalyze,
    Long startTime) {
    return backendFuture.thenComposeAsync(server ->
      server.getAnalysisService().analyzeFilesAndTrack(
        new AnalyzeFilesAndTrackParams(PROJECT_ID, analysisId, filesToAnalyze, Map.of(), false, startTime)
      ));
  }

  public CompletableFuture<GetAllProjectsResponse> findAllProjects() {
    return backendFuture.thenComposeAsync(server ->
      server.getConnectionService().getAllProjects(new GetAllProjectsParams(
        new TransientSonarCloudConnectionDto(sonarqubeCloudOrg, Either.forLeft(new TokenDto(sonarqubeCloudToken)), SonarCloudRegion.EU)
      )));
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

    var sonarqubeCloudConnections = new ArrayList<SonarCloudConnectionConfigurationDto>();
    if (sonarqubeCloudToken != null && sonarqubeCloudOrg != null) {
      sonarqubeCloudConnections.add(new SonarCloudConnectionConfigurationDto(CONNECTION_ID, sonarqubeCloudOrg, SonarCloudRegion.EU, true));
      LOG.info("Connection to SonarQube Cloud configured");
    }

    return rpcServer.initialize(
      new InitializeParams(
        new ClientConstantInfoDto(
          "Sonar MCP Server",
          "Sonar MCP Server 0.0.1"
        ),
        new TelemetryClientConstantAttributesDto("mcpserver", "Sonar MCP Server", "0.0.1", "0.0.1", emptyMap()),
        new HttpConfigurationDto(
          new SslConfigurationDto(getPathProperty("sonarlint.ssl.trustStorePath"), System.getProperty("sonarlint.ssl.trustStorePassword"),
            System.getProperty("sonarlint.ssl.trustStoreType"), getPathProperty("sonarlint.ssl.keyStorePath"), System.getProperty("sonarlint.ssl.keyStorePassword"),
            System.getProperty("sonarlint.ssl.keyStoreType")),
          getTimeoutProperty("sonarlint.http.connectTimeout"), getTimeoutProperty("sonarlint.http.socketTimeout"), getTimeoutProperty("sonarlint.http.connectionRequestTimeout"),
          getTimeoutProperty("sonarlint.http.responseTimeout")),
        this.sonarqubeCloudUri == null ? null : new SonarCloudAlternativeEnvironmentDto(Map.of(SonarCloudRegion.EU, new SonarQubeCloudRegionDto(this.sonarqubeCloudUri,
          this.sonarqubeCloudUri, this.sonarqubeCloudUri))),
        Set.of(BackendCapability.FULL_SYNCHRONIZATION, BackendCapability.PROJECT_SYNCHRONIZATION),
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
        sonarqubeCloudConnections,
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

  @CheckForNull
  private static String getValueViaEnvOrProperty(Map<String, String> environment, String propertyName) {
    var property = environment.get(propertyName);
    if (property == null) {
      property = System.getProperty(propertyName);
    }
    return property;
  }

  private void projectOpened() {
    backendFuture.thenAcceptAsync(server -> {
      if (!isConnectedToSonarQubeCloud) {
        server.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
          List.of(new ConfigurationScopeDto(PROJECT_ID, null, true, PROJECT_ID,
            new BindingConfigurationDto(CONNECTION_ID, sonarqubeCloudProjectKey, true))
          )
        ));
      } else {
        server.getConfigurationService().didAddConfigurationScopes(new DidAddConfigurationScopesParams(
          List.of(new ConfigurationScopeDto(PROJECT_ID, null, false, PROJECT_ID, null))
        ));
      }
    });
  }

  @Nullable
  private static Path getPathProperty(String propertyName) {
    var property = System.getProperty(propertyName);
    if (property != null) {
      return Paths.get(property);
    }
    return null;
  }

  @Nullable
  private static Duration getTimeoutProperty(String propertyName) {
    var property = System.getProperty(propertyName);
    if (property != null) {
      return Duration.ofMinutes(Long.parseLong(property));
    } else {
      return null;
    }
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
