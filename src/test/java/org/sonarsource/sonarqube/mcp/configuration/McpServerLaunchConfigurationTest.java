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
package org.sonarsource.sonarqube.mcp.configuration;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpServerLaunchConfigurationTest {

  @Test
  void should_return_correct_user_agent(@TempDir Path tempDir) {
    var configuration = new McpServerLaunchConfiguration(Map.of("STORAGE_PATH", tempDir.toString(), "SONARQUBE_TOKEN", "token", "SONARQUBE_ORG", "org"));

    assertThat(configuration.getUserAgent())
      .isEqualTo("SonarQube MCP Server " + System.getProperty("sonarqube.mcp.server.version"));
  }

  @Test
  void should_throw_error_if_no_storage_path() {
    var arg = Map.<String, String>of();

    assertThatThrownBy(() -> new McpServerLaunchConfiguration(arg))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("STORAGE_PATH environment variable or property must be set");
  }

  @Test
  void should_throw_error_if_storage_path_is_empty() {
    var arg = Map.of("STORAGE_PATH", "");

    assertThatThrownBy(() -> new McpServerLaunchConfiguration(arg))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("STORAGE_PATH environment variable or property must be set");
  }

  @Test
  void should_throw_error_if_sonarqube_token_is_missing(@TempDir Path tempDir) {
    var arg = Map.of("STORAGE_PATH", tempDir.toString(), "SONARQUBE_ORG", "org");

    assertThatThrownBy(() -> new McpServerLaunchConfiguration(arg))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("SONARQUBE_TOKEN environment variable or property must be set");
  }

  @Test
  void should_throw_error_if_sonarqube_cloud_org_is_missing(@TempDir Path tempDir) {
    var arg = Map.of("STORAGE_PATH", tempDir.toString(), "SONARQUBE_TOKEN", "token");

    assertThatThrownBy(() -> new McpServerLaunchConfiguration(arg))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("SONARQUBE_ORG environment variable must be set when using SonarQube Cloud");
  }

}
