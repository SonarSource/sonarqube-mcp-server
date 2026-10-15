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
package org.sonarsource.sonarqube.mcp.tools.analysis;

import io.modelcontextprotocol.spec.McpSchema;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.SonarQubeMcpServer;
import org.sonarsource.sonarqube.mcp.bridge.SonarQubeIdeBridgeClient;
import org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfiguration;
import org.sonarsource.sonarqube.mcp.harness.MockWebServer;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.a3s.A3sAnalysisApi;
import org.sonarsource.sonarqube.mcp.serverapi.cag.CagApi;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.OrganizationsApi;
import org.sonarsource.sonarqube.mcp.serverapi.plugins.PluginsApi;
import org.sonarsource.sonarqube.mcp.serverapi.qualityprofiles.QualityProfilesApi;
import org.sonarsource.sonarqube.mcp.tools.Tool;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class AnalysisToolRegistrationTest {

  @RegisterExtension
  final SonarQubeMcpServerTestHarness harness = new SonarQubeMcpServerTestHarness();

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void it_should_preserve_the_cloud_ide_bridge_unless_snippets_are_forced(boolean forceLocal) {
    var ide = new MockWebServer(64129);
    ide.stubFor(get(SonarQubeIdeBridgeClient.STATUS_PATH).willReturn(aResponse().withStatus(200)));
    ide.start();
    try {
      var client = harness.newClient(Map.of("SONARQUBE_ORG", "my-org", "SONARQUBE_TOOLSETS", "analysis,ide",
        "SONARQUBE_IDE_PORT", Integer.toString(ide.getPort()), "SONARQUBE_FORCE_LOCAL_ANALYSIS", Boolean.toString(forceLocal)));

      assertThat(client.listTools()).extracting(McpSchema.Tool::name)
        .contains(forceLocal ? AnalyzeCodeSnippetTool.TOOL_NAME : AnalyzeFileListTool.TOOL_NAME)
        .doesNotContain(forceLocal ? AnalyzeFileListTool.TOOL_NAME : AnalyzeCodeSnippetTool.TOOL_NAME,
          RunAdvancedCodeAnalysisTool.TOOL_NAME);
      assertThat(ide.countRequestsContaining(SonarQubeIdeBridgeClient.STATUS_PATH)).isEqualTo(forceLocal ? 0 : 1);
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(PluginsApi.INSTALLED_PLUGINS_PATH)).isEqualTo(forceLocal ? 1 : 0);
    } finally {
      ide.stop();
    }
  }

  @Test
  void it_should_analyze_an_entitled_organization_without_a_workspace_mount(@TempDir Path workspace) {
    harness.stubCagEntitlement(true);
    harness.stubA3sEnabled(true);
    harness.getMockSonarQubeServer().stubFor(get(QualityProfilesApi.SEARCH_PATH + "?organization=my-org&defaults=true")
      .willReturn(okJson("{\"profiles\":[]}")));
    System.setProperty(McpServerLaunchConfiguration.MCP_WORKSPACE_PATH_OVERRIDE_PROPERTY, workspace.resolve("absent-workspace").toString());
    try {
      var client = harness.withPlugins().newClient(Map.of("SONARQUBE_ORG", "my-org", "SONARQUBE_TOOLSETS", "analysis"));

      var result = client.callTool(AnalyzeCodeSnippetTool.TOOL_NAME, Map.of("fileContent", "<?php echo 'hello';", "language", "php"));

      assertThat(result.isError()).isFalse();
      assertThat(client.listTools()).extracting(McpSchema.Tool::name).doesNotContain(RunAdvancedCodeAnalysisTool.TOOL_NAME);
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(QualityProfilesApi.SEARCH_PATH)).isEqualTo(1);
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(CagApi.CAG_ENTITLEMENT_PATH)).isEqualTo(1);
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(A3sAnalysisApi.A3S_ORG_CONFIG_PATH)).isEqualTo(1);
    } finally {
      System.clearProperty(McpServerLaunchConfiguration.MCP_WORKSPACE_PATH_OVERRIDE_PROPERTY);
    }
  }

  @ParameterizedTest
  @CsvSource({
    "false, false, true, false, false", "true, true, true, false, true",
    "false, true, true, false, true", "true, false, true, false, true",
    "false, false, true, true, false", "true, true, true, true, false",
    "false, true, true, true, false", "true, false, true, true, false",
    "false, false, false, false, false", "true, true, false, false, false",
    "false, true, false, false, false", "true, false, false, false, false"
  })
  void it_should_register_one_cloud_analysis_tool(boolean firstEntitled, boolean secondEntitled, boolean mounted,
    boolean forceLocal, boolean advanced, @TempDir Path workspace) {
    var mock = harness.getMockSonarQubeServer();
    mock.stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH)).withQueryParam("organizationKey", absent())
      .willReturn(okJson("""
        [{"key":"first-org","uuidV4":"first-uuid"},{"key":"second-org","uuidV4":"second-uuid"}]
        """)));
    stubEntitlement(harness, "first-uuid", firstEntitled);
    stubEntitlement(harness, "second-uuid", secondEntitled);
    System.setProperty(McpServerLaunchConfiguration.MCP_WORKSPACE_PATH_OVERRIDE_PROPERTY,
      (mounted ? workspace : workspace.resolve("absent-workspace")).toString());
    try {
      var client = harness.newClient(Map.of("SONARQUBE_IS_CLOUD", "true", "SONARQUBE_TOOLSETS", "analysis,vortex",
        "SONARQUBE_FORCE_LOCAL_ANALYSIS", Boolean.toString(forceLocal)));

      assertThat(client.listTools()).extracting(McpSchema.Tool::name)
        .contains(advanced ? RunAdvancedCodeAnalysisTool.TOOL_NAME : AnalyzeCodeSnippetTool.TOOL_NAME)
        .doesNotContain(advanced ? AnalyzeCodeSnippetTool.TOOL_NAME : RunAdvancedCodeAnalysisTool.TOOL_NAME,
          AnalyzeFileListTool.TOOL_NAME, ToggleAutomaticAnalysisTool.TOOL_NAME);
      assertThat(mock.countRequestsContaining(PluginsApi.INSTALLED_PLUGINS_PATH)).isEqualTo(advanced ? 0 : 1);
      // Startup discovery supplies UUIDs, and no second organization listing is needed for analysis selection.
      assertThat(mock.countRequestsContaining(OrganizationsApi.ORGANIZATIONS_PATH)).isEqualTo(1);
      assertThat(mock.countRequestsContaining(CagApi.CAG_ENTITLEMENT_PATH + "first-uuid"))
        .isEqualTo(mounted && !forceLocal ? 1 : 0);
      assertThat(mock.countRequestsContaining(CagApi.CAG_ENTITLEMENT_PATH + "second-uuid"))
        .isEqualTo(mounted && !forceLocal && !firstEntitled ? 1 : 0);
    } finally {
      System.clearProperty(McpServerLaunchConfiguration.MCP_WORKSPACE_PATH_OVERRIDE_PROPERTY);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void it_should_check_only_the_fixed_cloud_organization(boolean entitled, @TempDir Path workspace) {
    harness.stubCagEntitlement(entitled);
    harness.stubA3sEnabled(entitled);
    System.setProperty(McpServerLaunchConfiguration.MCP_WORKSPACE_PATH_OVERRIDE_PROPERTY, workspace.toString());
    try {
      var client = harness.newClient(Map.of("SONARQUBE_ORG", "my-org", "SONARQUBE_TOOLSETS", "analysis,vortex"));

      assertThat(client.listTools()).extracting(McpSchema.Tool::name)
        .contains(entitled ? RunAdvancedCodeAnalysisTool.TOOL_NAME : AnalyzeCodeSnippetTool.TOOL_NAME)
        .doesNotContain(entitled ? AnalyzeCodeSnippetTool.TOOL_NAME : RunAdvancedCodeAnalysisTool.TOOL_NAME);
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(OrganizationsApi.ORGANIZATIONS_PATH)).isEqualTo(1);
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining("organizationKey=my-org")).isEqualTo(1);
    } finally {
      System.clearProperty(McpServerLaunchConfiguration.MCP_WORKSPACE_PATH_OVERRIDE_PROPERTY);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void it_should_preserve_disabled_analysis_toolsets(boolean forceLocal, @TempDir Path workspace) {
    harness.stubCagEntitlement(true);
    harness.stubA3sEnabled(true);
    System.setProperty(McpServerLaunchConfiguration.MCP_WORKSPACE_PATH_OVERRIDE_PROPERTY, workspace.toString());
    try {
      var client = harness.newClient(Map.of("SONARQUBE_ORG", "my-org", "SONARQUBE_TOOLSETS", "issues",
        "SONARQUBE_FORCE_LOCAL_ANALYSIS", Boolean.toString(forceLocal)));

      assertThat(client.listTools()).isNotEmpty().extracting(McpSchema.Tool::name)
        .doesNotContain(AnalyzeCodeSnippetTool.TOOL_NAME, RunAdvancedCodeAnalysisTool.TOOL_NAME);
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(PluginsApi.INSTALLED_PLUGINS_PATH)).isZero();
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(CagApi.CAG_ENTITLEMENT_PATH)).isZero();
    } finally {
      System.clearProperty(McpServerLaunchConfiguration.MCP_WORKSPACE_PATH_OVERRIDE_PROPERTY);
    }
  }

  @ParameterizedTest
  @CsvSource({"true, true", "true, false", "false, true", "false, false"})
  void it_should_keep_http_local_analysis_only_for_server_with_startup_token(boolean cloud, boolean startupToken,
    @TempDir Path storage) throws Exception {
    var environment = new HashMap<String, String>();
    environment.put("SONARQUBE_URL", harness.getMockSonarQubeServer().baseUrl());
    if (startupToken) {
      environment.put("SONARQUBE_TOKEN", "test-token");
    }
    environment.put("STORAGE_PATH", storage.toString());
    environment.put("SONARQUBE_TRANSPORT", "http");
    try (var socket = new ServerSocket(0)) {
      environment.put("SONARQUBE_HTTP_PORT", Integer.toString(socket.getLocalPort()));
    }
    environment.put("SONARQUBE_IS_CLOUD", Boolean.toString(cloud));
    environment.put("SONARQUBE_TOOLSETS", "analysis,ide,vortex");
    environment.put("SONARQUBE_FORCE_LOCAL_ANALYSIS", "true");
    environment.put("TELEMETRY_DISABLED", "true");
    harness.prepareMockWebServer(environment);
    var server = new SonarQubeMcpServer(environment);
    try {
      server.start();
      server.waitForInitialization();

      var analysisNames = server.getSupportedTools().stream().map(Tool::definition).map(McpSchema.Tool::name)
        .filter(name -> name.equals(AnalyzeCodeSnippetTool.TOOL_NAME) || name.equals(RunAdvancedCodeAnalysisTool.TOOL_NAME)
          || name.equals(AnalyzeFileListTool.TOOL_NAME) || name.equals(ToggleAutomaticAnalysisTool.TOOL_NAME)).toList();
      if (!cloud && startupToken) {
        assertThat(analysisNames).containsExactly(AnalyzeCodeSnippetTool.TOOL_NAME);
      } else {
        assertThat(analysisNames).isEmpty();
      }
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(PluginsApi.INSTALLED_PLUGINS_PATH))
        .isEqualTo(!cloud && startupToken ? 1 : 0);
    } finally {
      server.shutdown();
    }
  }

  private static void stubEntitlement(SonarQubeMcpServerTestHarness harness, String uuid, boolean entitled) {
    harness.getMockSonarQubeServer().stubFor(get(CagApi.CAG_ENTITLEMENT_PATH + uuid)
      .willReturn(okJson("{\"hasEntitlement\":" + entitled + "}")));
    harness.getMockSonarQubeServer().stubFor(get(A3sAnalysisApi.A3S_ORG_CONFIG_PATH + uuid)
      .willReturn(okJson("{\"id\":\"" + uuid + "\",\"enabled\":" + entitled + ",\"eligible\":true}")));
  }
}
