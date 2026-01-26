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
package org.sonarsource.sonarqube.mcp.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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
    var tools = loader.loadProxiedTools();

    assertThat(tools).isEmpty();
  }

  @Test
  void shutdown_should_not_fail_when_called_multiple_times() {
    loader = new ProxiedToolsLoader();
    loader.loadProxiedTools();

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

    var tools1 = loader1.loadProxiedTools();
    var tools2 = loader2.loadProxiedTools();

    assertThat(tools1).isNotNull().isEmpty();
    assertThat(tools2).isNotNull().isEmpty();

    loader1.shutdown();
    loader2.shutdown();
  }

  @Test
  void loadProxiedTools_after_shutdown_should_work() {
    loader = new ProxiedToolsLoader();
    
    var tools1 = loader.loadProxiedTools();
    loader.shutdown();
    
    // Loading again after shutdown should still work
    var tools2 = loader.loadProxiedTools();
    
    assertThat(tools1).isNotNull();
    assertThat(tools2).isNotNull();
  }

}
