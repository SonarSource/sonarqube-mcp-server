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
package org.sonarsource.sonarqube.mcp.client;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ProxiedToolsLoaderTest {

  private ProxiedToolsLoader loader;

  @AfterEach
  void cleanup() {
    if (loader != null) {
      loader.shutdown();
    }
  }

  @Test
  void loadProxiedTools_should_return_empty_when_no_servers_configured() {
    loader = new ProxiedToolsLoader();
    var tools = loader.loadProxiedTools(TransportMode.STDIO, UUID.randomUUID().toString());

    assertThat(tools).isEmpty();
  }

  @Test
  void shutdown_should_not_fail_when_called_multiple_times() {
    loader = new ProxiedToolsLoader();
    loader.loadProxiedTools(TransportMode.STDIO, UUID.randomUUID().toString());

    assertThatCode(() -> {
      loader.shutdown();
      loader.shutdown();
      loader.shutdown();
    }).doesNotThrowAnyException();
  }

  @Test
  void multiple_loaders_should_work_independently() {
    var loader1 = new ProxiedToolsLoader();
    var loader2 = new ProxiedToolsLoader();

    var tools1 = loader1.loadProxiedTools(TransportMode.STDIO, UUID.randomUUID().toString());
    var tools2 = loader2.loadProxiedTools(TransportMode.HTTP, UUID.randomUUID().toString());

    assertThat(tools1).isNotNull().isEmpty();
    assertThat(tools2).isNotNull().isEmpty();

    loader1.shutdown();
    loader2.shutdown();
  }

  @Test
  void loadProxiedTools_after_shutdown_should_work() {
    loader = new ProxiedToolsLoader();
    
    var tools1 = loader.loadProxiedTools(TransportMode.STDIO, UUID.randomUUID().toString());
    loader.shutdown();
    
    // Loading again after shutdown should still work
    var tools2 = loader.loadProxiedTools(TransportMode.STDIO, UUID.randomUUID().toString());
    
    assertThat(tools1).isNotNull();
    assertThat(tools2).isNotNull();
  }

  @Test
  void getProxiedInstructions_should_return_empty_before_load() {
    loader = new ProxiedToolsLoader();

    assertThat(loader.getProxiedInstructions()).isEmpty();
  }

  @Test
  void getProxiedInstructions_should_return_empty_when_no_servers_configured() {
    loader = new ProxiedToolsLoader();
    loader.loadProxiedTools(TransportMode.STDIO, UUID.randomUUID().toString());

    assertThat(loader.getProxiedInstructions()).isEmpty();
  }

  @Test
  void isCommandAvailable_should_return_true_for_existing_command() {
    assertThat(ProxiedToolsLoader.isCommandAvailable("cat")).isTrue();
  }

  @Test
  void isCommandAvailable_should_return_false_for_missing_absolute_command() {
    assertThat(ProxiedToolsLoader.isCommandAvailable("/non/existent/command")).isFalse();
  }

  @Test
  void isCommandAvailable_should_return_false_when_absolute_path_points_to_directory(@TempDir Path tempDir) {
    assertThat(tempDir.toFile().canExecute()).isTrue();
    assertThat(ProxiedToolsLoader.isCommandAvailable(tempDir.toAbsolutePath().toString())).isFalse();
  }

}
