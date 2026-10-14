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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesResponse;
import org.sonarsource.sonarqube.mcp.client.TransportMode;
import org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfiguration;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.http.HttpClientProvider;
import org.sonarsource.sonarqube.mcp.serverapi.EndpointParams;
import org.sonarsource.sonarqube.mcp.serverapi.OrgFeatureEntitlements;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.a3s.A3sAnalysisApi;
import org.sonarsource.sonarqube.mcp.serverapi.cag.CagApi;
import org.sonarsource.sonarqube.mcp.serverapi.qualityprofiles.QualityProfilesApi;
import org.sonarsource.sonarqube.mcp.serverapi.qualityprofiles.response.SearchResponse;
import org.sonarsource.sonarqube.mcp.slcore.BackendService;
import org.sonarsource.sonarqube.mcp.tools.Tool;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AnalyzeCodeSnippetRoutingTest {

  @ParameterizedTest
  @CsvSource({
    "STDIO, true, false, false, false", "STDIO, true, false, false, true",
    "STDIO, true, true, false, false", "STDIO, true, true, false, true",
    "STDIO, true, false, true, false", "STDIO, true, false, true, true",
    "STDIO, true, true, true, false", "STDIO, true, true, true, true",
    "HTTP, true, true, true, false", "HTTP, true, true, true, true",
    "STDIO, false, true, true, false", "STDIO, false, true, true, true"
  })
  void it_should_route_by_transport_entitlement_and_mount(TransportMode transport, boolean cloud, boolean cag, boolean a3s,
    boolean mounted, @TempDir Path workDir) throws IOException {
    var api = mock(ServerApi.class, RETURNS_DEEP_STUBS);
    when(api.isSonarQubeCloud()).thenReturn(cloud);
    when(api.getOrganization()).thenReturn("org");
    when(api.organizationsApi().getOrganizationUuidV4("org")).thenReturn("org-uuid");
    when(api.cagApi().getCagEntitlement("org-uuid")).thenReturn(new CagApi.CagEntitlementResponse(cag));
    when(api.a3sAnalysisApi().getA3sOrgConfig("org-uuid")).thenReturn(new A3sAnalysisApi.OrgConfigResponse("org-uuid", a3s, true));
    when(api.orgFeatureEntitlements()).thenReturn(new OrgFeatureEntitlements(api));
    when(api.qualityProfilesApi().getQualityProfiles(null)).thenReturn(new SearchResponse(List.of()));
    var backend = mock(BackendService.class);
    when(backend.getWorkDir()).thenReturn(workDir);
    var analysis = mock(AnalyzeFilesResponse.class);
    when(analysis.getRawIssues()).thenReturn(List.of());
    when(backend.analyzeFilesAndTrack(any(), any())).thenReturn(CompletableFuture.completedFuture(analysis));
    var rejected = transport == TransportMode.STDIO && cloud && cag && a3s;
    // Rejected calls must finish before initialization and before reading a nonexistent mounted file.
    var initialization = rejected ? new CompletableFuture<Void>() : CompletableFuture.<Void>completedFuture(null);
    if (mounted && !rejected) {
      Files.writeString(workDir.resolve("file.php"), "<?php echo 'hello';");
    }
    var tool = new AnalyzeCodeSnippetTool(backend, () -> api, initialization, null, mounted ? workDir : null, cloud, transport);
    var arguments = mounted ? Map.<String, Object>of("filePath", "file.php", "language", "php")
      : Map.<String, Object>of("fileContent", "<?php echo 'hello';", "language", "php");

    var result = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> tool.execute(new Tool.Arguments(arguments, null)));

    assertThat(result.isError()).isEqualTo(rejected);
    if (rejected) {
      assertThat(result.toCallToolResult().toString()).contains(RunAdvancedCodeAnalysisTool.TOOL_NAME);
      if (!mounted) {
        assertThat(result.toCallToolResult().toString()).contains("Mount", "/app/mcp-workspace", "restart");
      }
      verify(api.qualityProfilesApi(), never()).getQualityProfiles(any());
      verifyNoInteractions(backend);
      assertThat(initialization).isNotDone();
    } else {
      verify(api.qualityProfilesApi()).getQualityProfiles(null);
      verify(backend).analyzeFilesAndTrack(any(), any());
    }
    if (transport == TransportMode.HTTP || !cloud) {
      verify(api.organizationsApi(), never()).getOrganizationUuidV4(any());
      verify(api.cagApi(), never()).getCagEntitlement(any());
    }
  }

  @SonarQubeMcpServerTest
  void it_should_reject_entitled_org_without_registered_advanced_tool(SonarQubeMcpServerTestHarness harness, @TempDir Path temp) {
    System.setProperty(McpServerLaunchConfiguration.MCP_WORKSPACE_PATH_OVERRIDE_PROPERTY, temp.resolve("absent-workspace").toString());
    try {
      harness.stubCagEntitlement(true);
      harness.stubA3sEnabled(true);
      var client = harness.withPlugins().newClient(Map.of("SONARQUBE_ORG", "my-org", "SONARQUBE_TOOLSETS", "analysis"));
      assertThat(client.listTools()).noneMatch(tool -> tool.name().equals(RunAdvancedCodeAnalysisTool.TOOL_NAME));

      var result = client.callTool(AnalyzeCodeSnippetTool.TOOL_NAME, Map.of("fileContent", "<?php echo 'hello';", "language", "php"));

      assertThat(result.isError()).isTrue();
      assertThat(result.toString()).contains("Mount", "restart", RunAdvancedCodeAnalysisTool.TOOL_NAME);
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(QualityProfilesApi.SEARCH_PATH)).isZero();
    } finally {
      System.clearProperty(McpServerLaunchConfiguration.MCP_WORKSPACE_PATH_OVERRIDE_PROPERTY);
    }
  }

  @SonarQubeMcpServerTest
  void it_should_validate_unresolved_organization_before_initialization_and_allow_runtime_selection(
    SonarQubeMcpServerTestHarness harness, @TempDir Path workDir) {
    harness.getMockSonarQubeServer().stubFor(get(QualityProfilesApi.SEARCH_PATH + "?organization=selected-org&defaults=true")
      .willReturn(okJson("{\"profiles\":[]}")));
    var backend = mock(BackendService.class);
    when(backend.getWorkDir()).thenReturn(workDir);
    var analysis = mock(AnalyzeFilesResponse.class);
    when(analysis.getRawIssues()).thenReturn(List.of());
    when(backend.analyzeFilesAndTrack(any(), any())).thenReturn(CompletableFuture.completedFuture(analysis));
    var http = new HttpClientProvider("test");
    try {
      var api = new ServerApi(new ServerApiHelper(new EndpointParams(harness.getMockSonarQubeServer().baseUrl(), null, null, true),
        http.getHttpClient("token")), true);
      var initialization = new CompletableFuture<Void>();
      var tool = new AnalyzeCodeSnippetTool(backend, () -> api, initialization, null, null, true);
      var arguments = Map.<String, Object>of("fileContent", "<?php echo 'hello';", "language", "php");

      assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertThatThrownBy(() -> tool.execute(new Tool.Arguments(arguments, null)))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("list_organizations"));
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(QualityProfilesApi.SEARCH_PATH)).isZero();
      verifyNoInteractions(backend);
      assertThat(initialization).isNotDone();

      initialization.complete(null);
      assertThat(tool.execute(new Tool.Arguments(Map.of("organization", "selected-org", "fileContent", "<?php echo 'hello';", "language", "php"), null))
        .isError()).isFalse();
      assertThat(api.getOrganization()).isNull();
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(QualityProfilesApi.SEARCH_PATH + "?organization=selected-org")).isEqualTo(1);
    } finally {
      http.shutdown();
    }
  }
}
