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
