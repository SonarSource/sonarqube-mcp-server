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
package org.sonarsource.sonarqube.mcp.configuration;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

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
  void should_allow_missing_token_in_http_mode(@TempDir Path tempDir) {
    var arg = Map.of(
      "STORAGE_PATH", tempDir.toString(),
      "SONARQUBE_URL", "https://sonarqube.example.com",
      "SONARQUBE_TRANSPORT", "http"
    );

    var config = new McpServerLaunchConfiguration(arg);

    assertThat(config.getSonarQubeToken()).isNull();
    assertThat(config.isHttpEnabled()).isTrue();
  }

  @Test
  void should_throw_error_in_stdio_mode_when_neither_url_nor_org_is_set(@TempDir Path tempDir) {
    var arg = Map.of("STORAGE_PATH", tempDir.toString(), "SONARQUBE_TOKEN", "token");

    assertThatThrownBy(() -> new McpServerLaunchConfiguration(arg))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("SONARQUBE_URL or SONARQUBE_ORG must be set");
  }

  @Test
  void should_throw_error_in_stdio_mode_when_only_is_cloud_flag_is_set(@TempDir Path tempDir) {
    // SONARQUBE_IS_CLOUD alone is not enough in stdio — org resolution is per-request in HTTP only
    var arg = Map.of(
      "STORAGE_PATH", tempDir.toString(),
      "SONARQUBE_TOKEN", "token",
      "SONARQUBE_IS_CLOUD", "true"
    );

    assertThatThrownBy(() -> new McpServerLaunchConfiguration(arg))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("SONARQUBE_URL or SONARQUBE_ORG must be set");
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

  // Tool category tests

  @Test
  void should_enable_only_important_categories_by_default(@TempDir Path tempDir) {
    var arg = Map.of("STORAGE_PATH", tempDir.toString(), "SONARQUBE_TOKEN", "token", "SONARQUBE_ORG", "org");
    var configuration = new McpServerLaunchConfiguration(arg);

    assertThat(configuration.isToolCategoryEnabled(ToolCategory.ANALYSIS)).isTrue();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.ISSUES)).isTrue();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.PROJECTS)).isTrue();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.QUALITY_GATES)).isTrue();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.RULES)).isTrue();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.SOURCES)).isFalse();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.DUPLICATIONS)).isTrue();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.MEASURES)).isTrue();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.LANGUAGES)).isFalse();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.PORTFOLIOS)).isFalse();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.SYSTEM)).isFalse();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.WEBHOOKS)).isFalse();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.SECURITY_HOTSPOTS)).isTrue();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.DEPENDENCY_RISKS)).isTrue();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.CAG)).isTrue();
  }

  @Test
  void should_only_enable_specified_toolsets_when_toolsets_is_set(@TempDir Path tempDir) {
    var arg = Map.of(
      "STORAGE_PATH", tempDir.toString(),
      "SONARQUBE_TOKEN", "token",
      "SONARQUBE_ORG", "org",
      "SONARQUBE_TOOLSETS", "analysis,issues,quality-gates"
    );
    var configuration = new McpServerLaunchConfiguration(arg);

    assertThat(configuration.isToolCategoryEnabled(ToolCategory.ANALYSIS)).isTrue();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.ISSUES)).isTrue();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.QUALITY_GATES)).isTrue();
    // PROJECTS is always enabled as it's required to find project keys
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.PROJECTS)).isTrue();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.RULES)).isFalse();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.WEBHOOKS)).isFalse();
  }

  @Test
  void should_always_enable_projects_toolset_even_when_not_specified(@TempDir Path tempDir) {
    var arg = Map.of(
      "STORAGE_PATH", tempDir.toString(),
      "SONARQUBE_TOKEN", "token",
      "SONARQUBE_ORG", "org",
      "SONARQUBE_TOOLSETS", "analysis,issues"
    );
    var configuration = new McpServerLaunchConfiguration(arg);

    assertThat(configuration.isToolCategoryEnabled(ToolCategory.ANALYSIS)).isTrue();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.ISSUES)).isTrue();
    // PROJECTS should always be enabled even when not explicitly listed
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.PROJECTS)).isTrue();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.RULES)).isFalse();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.WEBHOOKS)).isFalse();
  }

  @Test
  void should_return_enabled_toolsets(@TempDir Path tempDir) {
    var arg = Map.of(
      "STORAGE_PATH", tempDir.toString(),
      "SONARQUBE_TOKEN", "token",
      "SONARQUBE_ORG", "org",
      "SONARQUBE_TOOLSETS", "analysis,issues"
    );
    var configuration = new McpServerLaunchConfiguration(arg);

    assertThat(configuration.getEnabledToolsets()).containsExactlyInAnyOrder(
      ToolCategory.ANALYSIS,
      ToolCategory.ISSUES
    );
  }

  // Read-only mode tests

  @Test
  void should_not_enable_read_only_mode_by_default(@TempDir Path tempDir) {
    var arg = Map.of("STORAGE_PATH", tempDir.toString(), "SONARQUBE_TOKEN", "token", "SONARQUBE_ORG", "org");
    var configuration = new McpServerLaunchConfiguration(arg);

    assertThat(configuration.isReadOnlyMode()).isFalse();
  }

  @Test
  void should_enable_read_only_mode_when_set_to_true(@TempDir Path tempDir) {
    var arg = Map.of(
      "STORAGE_PATH", tempDir.toString(),
      "SONARQUBE_TOKEN", "token",
      "SONARQUBE_ORG", "org",
      "SONARQUBE_READ_ONLY", "true"
    );
    var configuration = new McpServerLaunchConfiguration(arg);

    assertThat(configuration.isReadOnlyMode()).isTrue();
  }

  @Test
  void should_not_enable_read_only_mode_when_set_to_false(@TempDir Path tempDir) {
    var arg = Map.of(
      "STORAGE_PATH", tempDir.toString(),
      "SONARQUBE_TOKEN", "token",
      "SONARQUBE_ORG", "org",
      "SONARQUBE_READ_ONLY", "false"
    );
    var configuration = new McpServerLaunchConfiguration(arg);

    assertThat(configuration.isReadOnlyMode()).isFalse();
  }

  @Test
  void should_parse_read_only_mode_case_insensitively(@TempDir Path tempDir) {
    var arg = Map.of(
      "STORAGE_PATH", tempDir.toString(),
      "SONARQUBE_TOKEN", "token",
      "SONARQUBE_ORG", "org",
      "SONARQUBE_READ_ONLY", "TRUE"
    );
    var configuration = new McpServerLaunchConfiguration(arg);

    assertThat(configuration.isReadOnlyMode()).isTrue();
  }

  // Simplified configuration tests

  @Test
  void should_use_sonarcloud_io_when_org_is_set_without_url(@TempDir Path tempDir) {
    var arg = Map.of("STORAGE_PATH", tempDir.toString(), "SONARQUBE_TOKEN", "token", "SONARQUBE_ORG", "my-org");
    var configuration = new McpServerLaunchConfiguration(arg);

    assertThat(configuration.getSonarQubeUrl()).isEqualTo("https://sonarcloud.io");
    assertThat(configuration.isSonarQubeCloud()).isTrue();
    assertThat(configuration.getSonarqubeOrg()).isEqualTo("my-org");
  }

  @Test
  void should_use_custom_url_when_org_and_url_are_both_set(@TempDir Path tempDir) {
    var arg = Map.of(
      "STORAGE_PATH", tempDir.toString(),
      "SONARQUBE_TOKEN", "token",
      "SONARQUBE_ORG", "my-org",
      "SONARQUBE_URL", "https://sonarqube.us"
    );
    var configuration = new McpServerLaunchConfiguration(arg);

    assertThat(configuration.getSonarQubeUrl()).isEqualTo("https://sonarqube.us");
    assertThat(configuration.isSonarQubeCloud()).isTrue();
    assertThat(configuration.getSonarqubeOrg()).isEqualTo("my-org");
  }

  @Test
  void should_use_server_mode_when_only_url_is_set(@TempDir Path tempDir) {
    var arg = Map.of(
      "STORAGE_PATH", tempDir.toString(),
      "SONARQUBE_TOKEN", "token",
      "SONARQUBE_URL", "https://my-server.com"
    );
    var configuration = new McpServerLaunchConfiguration(arg);

    assertThat(configuration.getSonarQubeUrl()).isEqualTo("https://my-server.com");
    assertThat(configuration.isSonarQubeCloud()).isFalse();
    assertThat(configuration.getSonarqubeOrg()).isNull();
  }

  @Test
  void should_support_deprecated_sonarqube_cloud_url_for_backward_compatibility(@TempDir Path tempDir) {
    var arg = Map.of(
      "STORAGE_PATH", tempDir.toString(),
      "SONARQUBE_TOKEN", "token",
      "SONARQUBE_ORG", "my-org",
      "SONARQUBE_CLOUD_URL", "https://sonarqube.us"
    );
    var configuration = new McpServerLaunchConfiguration(arg);

    assertThat(configuration.getSonarQubeUrl()).isEqualTo("https://sonarqube.us");
    assertThat(configuration.isSonarQubeCloud()).isTrue();
    assertThat(configuration.getSonarqubeOrg()).isEqualTo("my-org");
  }

  @Test
  void should_prefer_sonarqube_url_over_deprecated_cloud_url(@TempDir Path tempDir) {
    var arg = Map.of(
      "STORAGE_PATH", tempDir.toString(),
      "SONARQUBE_TOKEN", "token",
      "SONARQUBE_ORG", "my-org",
      "SONARQUBE_URL", "https://sonarqube.us",
      "SONARQUBE_CLOUD_URL", "https://sonarcloud.io"
    );
    var configuration = new McpServerLaunchConfiguration(arg);

    assertThat(configuration.getSonarQubeUrl()).isEqualTo("https://sonarqube.us");
    assertThat(configuration.isSonarQubeCloud()).isTrue();
  }

  // isSonarQubeCloudUrl tests

  @Test
  void isSonarQubeCloudUrl_should_return_false_for_null() {
    assertThat(McpServerLaunchConfiguration.isSonarQubeCloudUrl(null)).isFalse();
  }

  @Test
  void isSonarQubeCloudUrl_should_return_true_for_sonarcloud_io() {
    assertThat(McpServerLaunchConfiguration.isSonarQubeCloudUrl("https://sonarcloud.io")).isTrue();
  }

  @Test
  void isSonarQubeCloudUrl_should_return_true_for_sonarqube_us() {
    assertThat(McpServerLaunchConfiguration.isSonarQubeCloudUrl("https://sonarqube.us")).isTrue();
  }

  @Test
  void isSonarQubeCloudUrl_should_return_false_for_custom_server_url() {
    assertThat(McpServerLaunchConfiguration.isSonarQubeCloudUrl("https://my-sonarqube.example.com")).isFalse();
  }

  // isSonarQubeCloud detection tests — mode-independent (stdio and HTTP behave identically)

  @Test
  void should_default_to_sonarcloud_when_no_url_is_set(@TempDir Path tempDir) {
    var arg = Map.of(
      "STORAGE_PATH", tempDir.toString(),
      "SONARQUBE_TRANSPORT", "http"
    );
    var configuration = new McpServerLaunchConfiguration(arg);

    assertThat(configuration.isSonarQubeCloud()).isTrue();
    assertThat(configuration.getSonarQubeUrl()).isEqualTo("https://sonarcloud.io");
    assertThat(configuration.getSonarQubeToken()).isNull();
  }

  @Test
  void should_detect_sonarcloud_from_sonarcloud_io_url(@TempDir Path tempDir) {
    var arg = Map.of(
      "STORAGE_PATH", tempDir.toString(),
      "SONARQUBE_TOKEN", "token",
      "SONARQUBE_URL", "https://sonarcloud.io"
    );
    var configuration = new McpServerLaunchConfiguration(arg);

    assertThat(configuration.isSonarQubeCloud()).isTrue();
  }

  @Test
  void should_detect_sonarcloud_from_sonarqube_us_url(@TempDir Path tempDir) {
    var arg = Map.of(
      "STORAGE_PATH", tempDir.toString(),
      "SONARQUBE_TOKEN", "token",
      "SONARQUBE_URL", "https://sonarqube.us"
    );
    var configuration = new McpServerLaunchConfiguration(arg);

    assertThat(configuration.isSonarQubeCloud()).isTrue();
    assertThat(configuration.getSonarQubeUrl()).isEqualTo("https://sonarqube.us");
  }

  @Test
  void should_detect_sonarqube_server_from_custom_url(@TempDir Path tempDir) {
    var arg = Map.of(
      "STORAGE_PATH", tempDir.toString(),
      "SONARQUBE_TOKEN", "token",
      "SONARQUBE_URL", "https://my-sonarqube.example.com"
    );
    var configuration = new McpServerLaunchConfiguration(arg);

    assertThat(configuration.isSonarQubeCloud()).isFalse();
    assertThat(configuration.getSonarQubeUrl()).isEqualTo("https://my-sonarqube.example.com");
  }

  @Test
  void should_detect_sonarcloud_from_org_even_with_custom_url(@TempDir Path tempDir) {
    // SONARQUBE_ORG is set → SQC regardless of the URL
    var arg = Map.of(
      "STORAGE_PATH", tempDir.toString(),
      "SONARQUBE_TOKEN", "token",
      "SONARQUBE_URL", "https://test.sc-test.io",
      "SONARQUBE_ORG", "my-org"
    );
    var configuration = new McpServerLaunchConfiguration(arg);

    assertThat(configuration.isSonarQubeCloud()).isTrue();
    assertThat(configuration.getSonarQubeUrl()).isEqualTo("https://test.sc-test.io");
  }

  @Test
  void should_require_sonarqube_is_cloud_flag_for_custom_sqc_url_without_org(@TempDir Path tempDir) {
    // Custom SQC URL, no org, no SONARQUBE_IS_CLOUD → falls through to SQS (URL is required and present)
    var arg = Map.of(
      "STORAGE_PATH", tempDir.toString(),
      "SONARQUBE_TOKEN", "token",
      "SONARQUBE_URL", "https://test.sc-test.io"
    );
    var configuration = new McpServerLaunchConfiguration(arg);

    assertThat(configuration.isSonarQubeCloud()).isFalse();
  }

  @Test
  void should_force_sonarcloud_when_is_sonarcloud_flag_is_set(@TempDir Path tempDir) {
    var arg = Map.of(
      "STORAGE_PATH", tempDir.toString(),
      "SONARQUBE_TOKEN", "token",
      "SONARQUBE_URL", "https://test.sc-test.io",
      "SONARQUBE_IS_CLOUD", "true"
    );
    var configuration = new McpServerLaunchConfiguration(arg);

    assertThat(configuration.isSonarQubeCloud()).isTrue();
    assertThat(configuration.getSonarQubeUrl()).isEqualTo("https://test.sc-test.io");
  }

  @Test
  void should_not_force_sonarcloud_when_is_sonarcloud_flag_is_false(@TempDir Path tempDir) {
    var arg = Map.of(
      "STORAGE_PATH", tempDir.toString(),
      "SONARQUBE_TOKEN", "token",
      "SONARQUBE_URL", "https://my-sonarqube.example.com",
      "SONARQUBE_IS_CLOUD", "false"
    );
    var configuration = new McpServerLaunchConfiguration(arg);

    assertThat(configuration.isSonarQubeCloud()).isFalse();
  }

}
