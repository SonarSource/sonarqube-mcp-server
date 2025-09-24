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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpServerLaunchConfigurationTest {

  @AfterEach
  void cleanup() {
    System.clearProperty("SONARQUBE_URL");
  }

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

  @Test
  void should_return_default_value_if_url_is_not_set(@TempDir Path tempDir) {
    var arg = Map.of("STORAGE_PATH", tempDir.toString(), "SONARQUBE_TOKEN", "token", "SONARQUBE_ORG", "org");
    var mcpServerLaunchConfiguration = new McpServerLaunchConfiguration(arg);

    var sonarQubeUrl = mcpServerLaunchConfiguration.getSonarQubeUrl();

    assertThat(sonarQubeUrl).isEqualTo("https://sonarcloud.io");
  }

  @Test
  void should_return_url_from_environment_variable_if_set(@TempDir Path tempDir) {
    var arg = Map.of("STORAGE_PATH", tempDir.toString(), "SONARQUBE_TOKEN", "token", "SONARQUBE_ORG", "org", "SONARQUBE_URL", "XXX");
    var mcpServerLaunchConfiguration = new McpServerLaunchConfiguration(arg);

    var sonarQubeUrl = mcpServerLaunchConfiguration.getSonarQubeUrl();

    assertThat(sonarQubeUrl).isEqualTo("XXX");
  }

  @Test
  void should_return_url_from_system_property_if_set(@TempDir Path tempDir) {
    var arg = Map.of("STORAGE_PATH", tempDir.toString(), "SONARQUBE_TOKEN", "token", "SONARQUBE_ORG", "org");
    System.setProperty("SONARQUBE_URL", "XXX");
    var mcpServerLaunchConfiguration = new McpServerLaunchConfiguration(arg);

    var sonarQubeUrl = mcpServerLaunchConfiguration.getSonarQubeUrl();

    assertThat(sonarQubeUrl).isEqualTo("XXX");
  }

  @Test
  void should_return_default_value_if_url_environment_variable_is_blank(@TempDir Path tempDir) {
    var arg = Map.of("STORAGE_PATH", tempDir.toString(), "SONARQUBE_TOKEN", "token", "SONARQUBE_ORG", "org", "SONARQUBE_URL", "");
    var mcpServerLaunchConfiguration = new McpServerLaunchConfiguration(arg);

    var sonarQubeUrl = mcpServerLaunchConfiguration.getSonarQubeUrl();

    assertThat(sonarQubeUrl).isEqualTo("https://sonarcloud.io");
  }

  @Test
  void should_return_default_value_if_url_system_property_is_blank(@TempDir Path tempDir) {
    var arg = Map.of("STORAGE_PATH", tempDir.toString(), "SONARQUBE_TOKEN", "token", "SONARQUBE_ORG", "org");
    System.setProperty("SONARQUBE_URL", "");
    var mcpServerLaunchConfiguration = new McpServerLaunchConfiguration(arg);

    var sonarQubeUrl = mcpServerLaunchConfiguration.getSonarQubeUrl();

    assertThat(sonarQubeUrl).isEqualTo("https://sonarcloud.io");
  }

  @Test
  void should_return_null_if_ide_port_is_not_set(@TempDir Path tempDir) {
    var arg = Map.of("STORAGE_PATH", tempDir.toString(), "SONARQUBE_TOKEN", "token", "SONARQUBE_ORG", "org");

    var mcpServerLaunchConfiguration = new McpServerLaunchConfiguration(arg);

    assertThat(mcpServerLaunchConfiguration.getSonarQubeIdePort()).isNull();
  }

  @Test
  void should_return_ide_port_if_set(@TempDir Path tempDir) {
    var arg = Map.of("STORAGE_PATH", tempDir.toString(), "SONARQUBE_TOKEN", "token", "SONARQUBE_ORG", "org", "SONARQUBE_IDE_PORT", "64120");

    var mcpServerLaunchConfiguration = new McpServerLaunchConfiguration(arg);

    assertThat(mcpServerLaunchConfiguration.getSonarQubeIdePort()).isEqualTo(64120);
  }

  @Test
  void should_not_return_ide_port_if_out_of_range(@TempDir Path tempDir) {
    var arg = Map.of("STORAGE_PATH", tempDir.toString(), "SONARQUBE_TOKEN", "token", "SONARQUBE_ORG", "org", "SONARQUBE_IDE_PORT", "70000");

    assertThatThrownBy(() -> new McpServerLaunchConfiguration(arg))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("SONARQUBE_IDE_PORT value must be between 64120 and 64130, got: 70000");
  }

}
