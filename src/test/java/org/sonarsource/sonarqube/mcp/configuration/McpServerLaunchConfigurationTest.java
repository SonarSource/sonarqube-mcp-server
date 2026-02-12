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

import io.modelcontextprotocol.spec.McpSchema;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.tools.analysis.AnalyzeCodeSnippetTool;
import org.sonarsource.sonarqube.mcp.tools.analysis.AnalyzeFileListTool;
import org.sonarsource.sonarqube.mcp.tools.analysis.RunAdvancedCodeAnalysisTool;
import org.sonarsource.sonarqube.mcp.tools.analysis.ToggleAutomaticAnalysisTool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpServerLaunchConfigurationTest {

  @AfterEach
  void cleanup() {
    System.clearProperty("SONARQUBE_URL");
    System.clearProperty("SONARQUBE_ADVANCED_ANALYSIS_ENABLED");
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
  void should_throw_error_if_sonarqube_server_url_is_missing(@TempDir Path tempDir) {
    var arg = Map.of("STORAGE_PATH", tempDir.toString(), "SONARQUBE_TOKEN", "token");

    assertThatThrownBy(() -> new McpServerLaunchConfiguration(arg))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("SONARQUBE_URL must be set when connecting to SonarQube Server. " +
        "SONARQUBE_ORG is not defined, so a connection to SonarQube Server was assumed.");
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
  void should_enable_all_categories_by_default(@TempDir Path tempDir) {
    var arg = Map.of("STORAGE_PATH", tempDir.toString(), "SONARQUBE_TOKEN", "token", "SONARQUBE_ORG", "org");
    var configuration = new McpServerLaunchConfiguration(arg);

    assertThat(configuration.isToolCategoryEnabled(ToolCategory.ANALYSIS)).isTrue();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.ISSUES)).isTrue();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.PROJECTS)).isTrue();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.QUALITY_GATES)).isTrue();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.RULES)).isTrue();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.SOURCES)).isTrue();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.MEASURES)).isTrue();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.LANGUAGES)).isTrue();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.PORTFOLIOS)).isTrue();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.SYSTEM)).isTrue();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.WEBHOOKS)).isTrue();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.DEPENDENCY_RISKS)).isTrue();
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
    // PROJECTS should only be enabled if explicitly listed
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.PROJECTS)).isFalse();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.RULES)).isFalse();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.WEBHOOKS)).isFalse();
  }

  @Test
  void should_respect_toolset_configuration_without_special_cases(@TempDir Path tempDir) {
    var arg = Map.of(
      "STORAGE_PATH", tempDir.toString(),
      "SONARQUBE_TOKEN", "token",
      "SONARQUBE_ORG", "org",
      "SONARQUBE_TOOLSETS", "analysis,issues"
    );
    var configuration = new McpServerLaunchConfiguration(arg);

    assertThat(configuration.isToolCategoryEnabled(ToolCategory.ANALYSIS)).isTrue();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.ISSUES)).isTrue();
    // PROJECTS should only be enabled if explicitly listed in SONARQUBE_TOOLSETS
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.PROJECTS)).isFalse();
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

  @Test
  void should_only_enable_external_tools_when_toolsets_is_external(@TempDir Path tempDir) {
    var arg = Map.of(
      "STORAGE_PATH", tempDir.toString(),
      "SONARQUBE_TOKEN", "token",
      "SONARQUBE_ORG", "org",
      "SONARQUBE_TOOLSETS", "external"
    );
    var configuration = new McpServerLaunchConfiguration(arg);

    assertThat(configuration.isToolCategoryEnabled(ToolCategory.EXTERNAL)).isTrue();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.PROJECTS)).isFalse();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.ANALYSIS)).isFalse();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.ISSUES)).isFalse();
    assertThat(configuration.isToolCategoryEnabled(ToolCategory.RULES)).isFalse();
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
    assertThat(configuration.isSonarCloud()).isTrue();
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
    assertThat(configuration.isSonarCloud()).isTrue();
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
    assertThat(configuration.isSonarCloud()).isFalse();
    assertThat(configuration.getSonarqubeOrg()).isNull();
  }

  @Test
  @SuppressWarnings("deprecation")
  void should_support_deprecated_sonarqube_cloud_url_for_backward_compatibility(@TempDir Path tempDir) {
    var arg = Map.of(
      "STORAGE_PATH", tempDir.toString(),
      "SONARQUBE_TOKEN", "token",
      "SONARQUBE_ORG", "my-org",
      "SONARQUBE_CLOUD_URL", "https://sonarqube.us"
    );
    var configuration = new McpServerLaunchConfiguration(arg);

    assertThat(configuration.getSonarQubeUrl()).isEqualTo("https://sonarqube.us");
    assertThat(configuration.isSonarCloud()).isTrue();
    assertThat(configuration.getSonarqubeOrg()).isEqualTo("my-org");
  }

  @Test
  @SuppressWarnings("deprecation")
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
    assertThat(configuration.isSonarCloud()).isTrue();
  }

  // Advanced analysis enabled tests

  @Test
  void should_not_enable_advanced_analysis_by_default(@TempDir Path tempDir) {
    var arg = Map.of("STORAGE_PATH", tempDir.toString(), "SONARQUBE_TOKEN", "token", "SONARQUBE_ORG", "org");
    var configuration = new McpServerLaunchConfiguration(arg);

    assertThat(configuration.isAdvancedAnalysisEnabled()).isFalse();
  }

  @Test
  void should_enable_advanced_analysis_when_system_property_is_true(@TempDir Path tempDir) {
    var arg = Map.of(
      "STORAGE_PATH", tempDir.toString(),
      "SONARQUBE_TOKEN", "token",
      "SONARQUBE_ORG", "org"
    );
    System.setProperty("SONARQUBE_ADVANCED_ANALYSIS_ENABLED", "true");
    var configuration = new McpServerLaunchConfiguration(arg);

    assertThat(configuration.isAdvancedAnalysisEnabled()).isTrue();
  }

  @Test
  void should_not_enable_advanced_analysis_when_system_property_is_false(@TempDir Path tempDir) {
    var arg = Map.of(
      "STORAGE_PATH", tempDir.toString(),
      "SONARQUBE_TOKEN", "token",
      "SONARQUBE_ORG", "org"
    );
    System.setProperty("SONARQUBE_ADVANCED_ANALYSIS_ENABLED", "false");
    var configuration = new McpServerLaunchConfiguration(arg);

    assertThat(configuration.isAdvancedAnalysisEnabled()).isFalse();
  }

  @Test
  void should_parse_advanced_analysis_enabled_case_insensitively(@TempDir Path tempDir) {
    var arg = Map.of(
      "STORAGE_PATH", tempDir.toString(),
      "SONARQUBE_TOKEN", "token",
      "SONARQUBE_ORG", "org"
    );
    System.setProperty("SONARQUBE_ADVANCED_ANALYSIS_ENABLED", "TRUE");
    var configuration = new McpServerLaunchConfiguration(arg);

    assertThat(configuration.isAdvancedAnalysisEnabled()).isTrue();
  }

  @SonarQubeMcpServerTest
  void should_register_only_advanced_analysis_tool_on_sonarcloud(SonarQubeMcpServerTestHarness harness) {
    System.setProperty("SONARQUBE_ADVANCED_ANALYSIS_ENABLED", "true");

    var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "org"));

    var toolNames = mcpClient.listTools().stream().map(McpSchema.Tool::name).toList();

    assertThat(toolNames).contains(RunAdvancedCodeAnalysisTool.TOOL_NAME);
    assertThat(toolNames).doesNotContain(AnalyzeCodeSnippetTool.TOOL_NAME);
    assertThat(toolNames).doesNotContain(AnalyzeFileListTool.TOOL_NAME);
    assertThat(toolNames).doesNotContain(ToggleAutomaticAnalysisTool.TOOL_NAME);
  }

  @SonarQubeMcpServerTest
  void should_log_warning_and_fallback_to_standard_analysis_on_sonarqube_server(SonarQubeMcpServerTestHarness harness) {
    var originalErr = System.err;
    var errBuffer = new ByteArrayOutputStream();
    System.setErr(new PrintStream(errBuffer, true, StandardCharsets.UTF_8));

    try {
      System.setProperty("SONARQUBE_ADVANCED_ANALYSIS_ENABLED", "true");

      var mcpClient = harness.newClient();

      var toolNames = mcpClient.listTools().stream().map(McpSchema.Tool::name).toList();

      assertThat(toolNames).doesNotContain(RunAdvancedCodeAnalysisTool.TOOL_NAME);
      assertThat(toolNames).contains(AnalyzeCodeSnippetTool.TOOL_NAME);

      var stderr = errBuffer.toString(StandardCharsets.UTF_8);
      assertThat(stderr).contains("SONARQUBE_ADVANCED_ANALYSIS_ENABLED is set but advanced analysis is only available on SonarCloud");
    } finally {
      System.setErr(originalErr);
    }
  }

}
