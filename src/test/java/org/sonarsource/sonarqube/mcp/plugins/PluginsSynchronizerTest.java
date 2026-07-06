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
package org.sonarsource.sonarqube.mcp.plugins;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarlint.core.rpc.protocol.common.Language;
import org.sonarsource.sonarqube.mcp.http.HttpClient;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.plugins.PluginsApi;
import org.sonarsource.sonarqube.mcp.serverapi.plugins.response.InstalledPluginsResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PluginsSynchronizerTest {

  private static final String HELLO_CONTENT = "hello";
  private static final String HELLO_MD5 = "5d41402abc4b2a76b9719d911017c592";

  @Test
  void it_should_download_sonarlint_supported_plugins(@TempDir Path tempDir) {
    var serverApi = mock(ServerApi.class);
    var pluginsApi = mock(PluginsApi.class);
    when(serverApi.pluginsApi()).thenReturn(pluginsApi);
    when(pluginsApi.getInstalled()).thenReturn(new InstalledPluginsResponse(List.of(
      new InstalledPluginsResponse.Plugin("java", true, "filename", HELLO_MD5))));
    var response = mock(HttpClient.Response.class);
    when(response.isSuccessful()).thenReturn(true);
    when(response.bodyAsStream()).thenReturn(new ByteArrayInputStream(HELLO_CONTENT.getBytes()));
    when(pluginsApi.downloadPlugin("java")).thenReturn(response);
    var pluginsSynchronizer = new PluginsSynchronizer(serverApi, tempDir);

    var analyzers = pluginsSynchronizer.synchronizeAnalyzers();

    var pluginPath = tempDir.resolve("plugins").resolve("filename");
    assertThat(analyzers.analyzerPaths()).containsExactly(pluginPath);
    assertThat(analyzers.enabledLanguages()).containsExactly(Language.JAVA);
    assertThat(pluginPath)
      .exists()
      .hasContent(HELLO_CONTENT);
  }

  @Test
  void it_should_skip_downloading_sonarlint_unsupported_plugins(@TempDir Path tempDir) {
    var serverApi = mock(ServerApi.class);
    var pluginsApi = mock(PluginsApi.class);
    when(serverApi.pluginsApi()).thenReturn(pluginsApi);
    when(pluginsApi.getInstalled()).thenReturn(new InstalledPluginsResponse(List.of(
      new InstalledPluginsResponse.Plugin("key", false, "filename", HELLO_MD5))));
    var pluginsSynchronizer = new PluginsSynchronizer(serverApi, tempDir);

    var analyzers = pluginsSynchronizer.synchronizeAnalyzers();

    assertThat(analyzers.analyzerPaths()).isEmpty();
  }

  @Test
  void it_should_skip_downloading_plugins_for_unsupported_languages(@TempDir Path tempDir) {
    var serverApi = mock(ServerApi.class);
    var pluginsApi = mock(PluginsApi.class);
    when(serverApi.pluginsApi()).thenReturn(pluginsApi);
    when(pluginsApi.getInstalled()).thenReturn(new InstalledPluginsResponse(List.of(
      new InstalledPluginsResponse.Plugin("cobol", true, "sonar-cobol-plugin.jar", HELLO_MD5))));
    var pluginsSynchronizer = new PluginsSynchronizer(serverApi, tempDir);

    var analyzers = pluginsSynchronizer.synchronizeAnalyzers();

    assertThat(analyzers.analyzerPaths()).isEmpty();
    assertThat(analyzers.enabledLanguages()).isEmpty();
    assertThat(tempDir.resolve("plugins").resolve("sonar-cobol-plugin.jar")).doesNotExist();
  }

  @Test
  void it_should_skip_downloading_already_synchronized_plugin(@TempDir Path tempDir) throws IOException {
    var pluginsFolderPath = tempDir.resolve("plugins");
    Files.createDirectories(pluginsFolderPath);
    var pluginPath = pluginsFolderPath.resolve("filename");
    Files.writeString(pluginPath, HELLO_CONTENT);
    var serverApi = mock(ServerApi.class);
    var pluginsApi = mock(PluginsApi.class);
    when(serverApi.pluginsApi()).thenReturn(pluginsApi);
    when(pluginsApi.getInstalled()).thenReturn(new InstalledPluginsResponse(List.of(
      new InstalledPluginsResponse.Plugin("java", true, "filename", HELLO_MD5))));
    var pluginsSynchronizer = new PluginsSynchronizer(serverApi, tempDir);

    var analyzers = pluginsSynchronizer.synchronizeAnalyzers();

    assertThat(analyzers.analyzerPaths()).containsExactly(pluginPath);
    assertThat(pluginPath)
      .exists()
      .hasContent(HELLO_CONTENT);
    verify(pluginsApi, never()).downloadPlugin("java");
  }

  @Test
  void it_should_throw_if_plugin_download_request_is_not_successful(@TempDir Path tempDir) {
    var serverApi = mock(ServerApi.class);
    var pluginsApi = mock(PluginsApi.class);
    when(serverApi.pluginsApi()).thenReturn(pluginsApi);
    when(pluginsApi.getInstalled()).thenReturn(new InstalledPluginsResponse(List.of(
      new InstalledPluginsResponse.Plugin("java", true, "filename", HELLO_MD5))));
    var response = mock(HttpClient.Response.class);
    when(response.isSuccessful()).thenReturn(false);
    when(response.code()).thenReturn(500);
    when(pluginsApi.downloadPlugin("java")).thenReturn(response);
    var pluginsSynchronizer = new PluginsSynchronizer(serverApi, tempDir);

    var throwable = catchThrowable(pluginsSynchronizer::synchronizeAnalyzers);

    assertThat(throwable).isInstanceOf(IllegalStateException.class)
        .hasMessage("Failed to download plugin 'java': HTTP status 500");
  }

  @Test
  void it_should_cleanup_unknown_plugins_after_synchronization(@TempDir Path tempDir) throws IOException {
    var pluginsFolderPath = tempDir.resolve("plugins");
    Files.createDirectories(pluginsFolderPath);
    var pluginPath = pluginsFolderPath.resolve("plugin.jar");
    Files.writeString(pluginPath, HELLO_CONTENT);
    var serverApi = mock(ServerApi.class);
    var pluginsApi = mock(PluginsApi.class);
    when(serverApi.pluginsApi()).thenReturn(pluginsApi);
    when(pluginsApi.getInstalled()).thenReturn(new InstalledPluginsResponse(List.of()));
    var response = mock(HttpClient.Response.class);
    when(response.isSuccessful()).thenReturn(false);
    when(response.code()).thenReturn(500);
    when(pluginsApi.downloadPlugin("java")).thenReturn(response);
    var pluginsSynchronizer = new PluginsSynchronizer(serverApi, tempDir);

    var analyzers = pluginsSynchronizer.synchronizeAnalyzers();

    assertThat(analyzers.analyzerPaths()).isEmpty();
    assertThat(analyzers.enabledLanguages()).isEmpty();
    assertThat(pluginPath).doesNotExist();
  }

  @Test
  void it_should_cleanup_plugins_for_unsupported_languages(@TempDir Path tempDir) throws IOException {
    var pluginsFolderPath = tempDir.resolve("plugins");
    Files.createDirectories(pluginsFolderPath);
    var unsupportedPluginPath = pluginsFolderPath.resolve("sonar-cobol-plugin.jar");
    Files.writeString(unsupportedPluginPath, "cobol content");
    var serverApi = mock(ServerApi.class);
    var pluginsApi = mock(PluginsApi.class);
    when(serverApi.pluginsApi()).thenReturn(pluginsApi);
    when(pluginsApi.getInstalled()).thenReturn(new InstalledPluginsResponse(List.of(
      new InstalledPluginsResponse.Plugin("cobol", true, "sonar-cobol-plugin.jar", HELLO_MD5)
    )));
    var pluginsSynchronizer = new PluginsSynchronizer(serverApi, tempDir);

    var analyzers = pluginsSynchronizer.synchronizeAnalyzers();

    assertThat(analyzers.analyzerPaths()).isEmpty();
    assertThat(analyzers.enabledLanguages()).isEmpty();
    assertThat(unsupportedPluginPath).doesNotExist();
  }

  @Test
  void it_should_reject_absolute_plugin_filename(@TempDir Path tempDir) {
    var escapePath = tempDir.resolve("pwned.jar");
    var serverApi = mock(ServerApi.class);
    var pluginsApi = mock(PluginsApi.class);
    when(serverApi.pluginsApi()).thenReturn(pluginsApi);
    when(pluginsApi.getInstalled()).thenReturn(new InstalledPluginsResponse(List.of(
      new InstalledPluginsResponse.Plugin("java", true, escapePath.toString(), HELLO_MD5))));
    var pluginsSynchronizer = new PluginsSynchronizer(serverApi, tempDir);

    var throwable = catchThrowable(pluginsSynchronizer::synchronizeAnalyzers);

    assertThat(throwable).isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Invalid plugin filename");
    assertThat(escapePath).doesNotExist();
    verify(pluginsApi, never()).downloadPlugin("java");
  }

  @Test
  void it_should_reject_traversal_plugin_filename(@TempDir Path tempDir) {
    var escapePath = tempDir.resolve("pwned.jar");
    var serverApi = mock(ServerApi.class);
    var pluginsApi = mock(PluginsApi.class);
    when(serverApi.pluginsApi()).thenReturn(pluginsApi);
    when(pluginsApi.getInstalled()).thenReturn(new InstalledPluginsResponse(List.of(
      new InstalledPluginsResponse.Plugin("java", true, "../../pwned.jar", HELLO_MD5))));
    var pluginsSynchronizer = new PluginsSynchronizer(serverApi, tempDir);

    var throwable = catchThrowable(pluginsSynchronizer::synchronizeAnalyzers);

    assertThat(throwable).isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Invalid plugin filename");
    assertThat(escapePath).doesNotExist();
    verify(pluginsApi, never()).downloadPlugin("java");
  }

  @Test
  void it_should_throw_if_downloaded_plugin_hash_does_not_match(@TempDir Path tempDir) {
    var serverApi = mock(ServerApi.class);
    var pluginsApi = mock(PluginsApi.class);
    when(serverApi.pluginsApi()).thenReturn(pluginsApi);
    when(pluginsApi.getInstalled()).thenReturn(new InstalledPluginsResponse(List.of(
      new InstalledPluginsResponse.Plugin("java", true, "filename", "00000000000000000000000000000000"))));
    var response = mock(HttpClient.Response.class);
    when(response.isSuccessful()).thenReturn(true);
    when(response.bodyAsStream()).thenReturn(new ByteArrayInputStream(HELLO_CONTENT.getBytes()));
    when(pluginsApi.downloadPlugin("java")).thenReturn(response);
    var pluginsSynchronizer = new PluginsSynchronizer(serverApi, tempDir);

    var throwable = catchThrowable(pluginsSynchronizer::synchronizeAnalyzers);

    assertThat(throwable).isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("hash mismatch");
    assertThat(tempDir.resolve("plugins").resolve("filename")).doesNotExist();
  }

}
