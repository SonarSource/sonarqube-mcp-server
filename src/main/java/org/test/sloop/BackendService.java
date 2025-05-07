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
package org.test.sloop;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import org.sonarsource.sonarlint.core.rpc.client.Sloop;
import org.sonarsource.sonarlint.core.rpc.client.SloopLauncher;
import org.sonarsource.sonarlint.core.rpc.protocol.SonarLintRpcServer;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesAndTrackParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.ConfigurationScopeDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.config.scope.DidAddConfigurationScopesParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.file.DidUpdateFileSystemParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.ClientConstantInfoDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.HttpConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.InitializeParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.LanguageSpecificRequirements;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.SslConfigurationDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.initialize.TelemetryClientConstantAttributesDto;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetStandaloneRuleDescriptionParams;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.rules.GetStandaloneRuleDescriptionResponse;
import org.sonarsource.sonarlint.core.rpc.protocol.common.ClientFileDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static org.test.analysis.AnalysisUtils.getSupportedLanguages;

@Service
public class BackendService {

  private final CompletableFuture<SonarLintRpcServer> backendFuture = new CompletableFuture();
  private Sloop sloop = null;
  private SloopLauncher defaultSloopLauncher = null;

  @Value("${backend.workdir}")
  private String workDir;

  @Value("${backend.sloop.path}")
  private String sloopPath;

  @Value("${backend.mcp.home.path}")
  private String homePath;

  @Value("${backend.plugin.path}")
  private String pluginPath;

  @PostConstruct
  public void initBackend() {
    createServiceStartingTask();
  }

  public void projectOpened(String projectId) {
    backendFuture.thenAcceptAsync(server ->
      server.getConfigurationService().didAddConfigurationScopes(
        new DidAddConfigurationScopesParams(
          List.of(new ConfigurationScopeDto(projectId, null, true, "sonar-mcp-server", null))
        )
      )
    );
  }

  public CompletableFuture<GetStandaloneRuleDescriptionResponse> getStandaloneRuleDetails(GetStandaloneRuleDescriptionParams params) {
    return backendFuture.thenComposeAsync(server -> server.getRulesService().getStandaloneRuleDetails(params));
  }

  public CompletableFuture<AnalyzeFilesResponse> analyzeFilesAndTrack(String moduleId, UUID analysisId, List<URI> filesToAnalyze,
    Long startTime) {
    return backendFuture.thenComposeAsync(server ->
      server.getAnalysisService().analyzeFilesAndTrack(
        new AnalyzeFilesAndTrackParams(moduleId, analysisId, filesToAnalyze, Map.of(), false, startTime)
      ));
  }

  public void addFile(ClientFileDto clientFileDto) {
    backendFuture.thenAcceptAsync(server -> server.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(List.of(clientFileDto), List.of(), List.of())));
  }

  public void removeFile(URI file) {
    backendFuture.thenAcceptAsync(server -> server.getFileService().didUpdateFileSystem(new DidUpdateFileSystemParams(List.of(), List.of(), List.of(file))));
  }

  private void createServiceStartingTask() {
    try {
      this.sloop = startSloopProcess();
      initRpcServer(sloop.getRpcServer()).get(1, TimeUnit.MINUTES);
      backendFuture.complete(sloop.getRpcServer());
      System.out.println("Sloop process started");
    } catch (Exception e) {
      backendFuture.cancel(true);
    }
  }

  private Sloop startSloopProcess() {
    var lsp4jLogger = Logger.getLogger("org.eclipse.lsp4j.jsonrpc.RemoteEndpoint");
    lsp4jLogger.setFilter(record -> {
      if (record.getLevel() == Level.SEVERE) {
        record.setLevel(Level.OFF);
        return false;
      } else {
        return true;
      }
    });
    var sloopLauncher = this.defaultSloopLauncher != null ? this.defaultSloopLauncher : new SloopLauncher(new SloopRpcClient());
    var jreHomePath = getPathProperty("java.home");
    var sloopPath = getSloopPath();
    System.out.println("Starting Sloop process from " + sloopPath + " with JRE from " + jreHomePath);
    return sloopLauncher.start(sloopPath, jreHomePath);
  }

  private CompletableFuture<Void> initRpcServer(SonarLintRpcServer rpcServer) {
    var pluginResolvedPath = getPluginPath();

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
        null,
        Set.of(),
        getHomePath(),
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
        getSupportedLanguages(),
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

  public Path getWorkDir() {
    return Paths.get(workDir);
  }

  public Path getSloopPath() {
    return Paths.get(sloopPath);
  }

  private Path getHomePath() {
    return Paths.get(homePath);
  }

  private Path getPluginPath() {
    return Paths.get(pluginPath);
  }

  @Nullable
  private Path getPathProperty(String propertyName) {
    var property = System.getProperty(propertyName);
    if (property != null) {
      return Paths.get(property);
    }
    return null;
  }

  @Nullable
  private Duration getTimeoutProperty(String propertyName) {
    var property = System.getProperty(propertyName);
    if (property != null) {
      return Duration.ofMinutes(Long.parseLong(property));
    } else {
      return null;
    }
  }

}
