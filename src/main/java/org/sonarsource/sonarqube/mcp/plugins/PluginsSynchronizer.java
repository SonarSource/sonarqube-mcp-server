/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
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
package org.sonarsource.sonarqube.mcp.plugins;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarqube.mcp.log.McpLogger;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.plugins.response.InstalledPluginsResponse;
import org.sonarsource.sonarqube.mcp.slcore.BackendService;

import static org.sonarsource.sonarqube.mcp.analysis.LanguageUtils.SUPPORTED_LANGUAGES_BY_PLUGIN_KEY;

public class PluginsSynchronizer {

  private static final McpLogger LOG = McpLogger.getInstance();

  private final ServerApi serverApi;
  private final Path pluginsPath;

  public PluginsSynchronizer(ServerApi serverApi, Path storagePath) {
    this.serverApi = serverApi;
    this.pluginsPath = storagePath.resolve("plugins");
  }

  public BackendService.AnalyzersAndLanguagesEnabled synchronizeAnalyzers() {
    var serverPlugins = serverApi.pluginsApi().getInstalled().plugins();
    downloadMissingPlugins(serverPlugins);
    cleanupUnknownPlugins(serverPlugins);
    return listLocalPlugins(serverPlugins);
  }

  private void downloadMissingPlugins(List<InstalledPluginsResponse.Plugin> serverPlugins) {
    try {
      Files.createDirectories(pluginsPath);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to create plugins directory", e);
    }
    for (var serverPlugin : serverPlugins) {
      if (shouldDownload(serverPlugin)) {
        downloadPlugin(serverPlugin.key(), pluginsPath.resolve(serverPlugin.filename()));
      }
    }
  }

  private boolean shouldDownload(InstalledPluginsResponse.Plugin plugin) {
    return plugin.sonarLintSupported() && !Files.exists(pluginsPath.resolve(plugin.filename()));
  }

  private void downloadPlugin(String pluginKey, Path localPath) {
    try (var response = serverApi.pluginsApi().downloadPlugin(pluginKey)) {
      if (response.isSuccessful()) {
        try (var inputStream = response.bodyAsStream()) {
          FileUtils.copyInputStreamToFile(inputStream, localPath.toFile());
        }
        LOG.info("Successfully downloaded plugin '" + pluginKey + "' to " + localPath);
      } else {
        throw new IllegalStateException("Failed to download plugin '" + pluginKey + "': HTTP status " + response.code());
      }
    } catch (IOException e) {
      throw new IllegalStateException("Error downloading plugin '" + pluginKey + "'", e);
    }
  }

  private void cleanupUnknownPlugins(List<InstalledPluginsResponse.Plugin> serverPlugins) {
    var serverFileNames = serverPlugins.stream()
      .map(InstalledPluginsResponse.Plugin::filename)
      .collect(Collectors.toSet());
    try (var directoryStream = Files.newDirectoryStream(pluginsPath, "*.jar")) {
      for (var localFile : directoryStream) {
        var fileName = localFile.getFileName().toString();
        if (!serverFileNames.contains(fileName)) {
          deleteUnknownPlugin(localFile);
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("Error during cleanup of unknown plugins", e);
    }
  }

  private static void deleteUnknownPlugin(Path localFile) {
    try {
      Files.delete(localFile);
      LOG.info("Removed unknown plugin file: " + localFile);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to remove unknown plugin file", e);
    }
  }

  private BackendService.AnalyzersAndLanguagesEnabled listLocalPlugins(List<InstalledPluginsResponse.Plugin> serverPlugins) {
    var pluginsPaths = new HashSet<Path>();
    var enabledLanguages = EnumSet.noneOf(Language.class);
    for (var serverPlugin : serverPlugins) {
      var pluginPath = pluginsPath.resolve(serverPlugin.filename());
      if (serverPlugin.sonarLintSupported() && Files.exists(pluginPath)) {
        SUPPORTED_LANGUAGES_BY_PLUGIN_KEY.forEach((supportedPluginKey, supportedLanguages) -> {
          if (serverPlugin.key().equals(supportedPluginKey)) {
            pluginsPaths.add(pluginPath);
            enabledLanguages.addAll(supportedLanguages);
          }
        });
      }
    }

    LOG.info("Found " + pluginsPaths.size() + " plugins, enabled languages: " + enabledLanguages);
    return new BackendService.AnalyzersAndLanguagesEnabled(pluginsPaths, enabledLanguages);
  }

}
