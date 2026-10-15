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
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarlint.core.rpc.protocol.backend.analysis.AnalyzeFilesResponse;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.http.HttpClientProvider;
import org.sonarsource.sonarqube.mcp.serverapi.EndpointParams;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AnalyzeCodeSnippetRoutingTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void it_should_analyze_without_probing_vortex_entitlement(boolean mounted, @TempDir Path workDir) throws IOException {
    var api = mock(ServerApi.class, RETURNS_DEEP_STUBS);
    when(api.qualityProfilesApi().getQualityProfiles(null)).thenReturn(new SearchResponse(List.of()));
    var backend = mock(BackendService.class);
    when(backend.getWorkDir()).thenReturn(workDir);
    var analysis = mock(AnalyzeFilesResponse.class);
    when(analysis.getRawIssues()).thenReturn(List.of());
    when(backend.analyzeFilesAndTrack(any(), any())).thenReturn(CompletableFuture.completedFuture(analysis));
    if (mounted) {
      Files.writeString(workDir.resolve("file.php"), "<?php echo 'hello';");
    }
    var tool = new AnalyzeCodeSnippetTool(backend, () -> api, CompletableFuture.completedFuture(null), null, mounted ? workDir : null, true);
    var arguments = mounted ? Map.<String, Object>of("filePath", "file.php", "language", "php")
      : Map.<String, Object>of("fileContent", "<?php echo 'hello';", "language", "php");

    assertThat(tool.execute(new Tool.Arguments(arguments, null)).isError()).isFalse();

    verify(api.qualityProfilesApi()).getQualityProfiles(null);
    verify(backend).analyzeFilesAndTrack(any(), any());
    verifyNoInteractions(api.orgFeatureEntitlements(), api.organizationsApi(), api.cagApi(), api.a3sAnalysisApi());
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
      var toolArguments = new Tool.Arguments(arguments, null);

      assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertThatThrownBy(() -> tool.execute(toolArguments))
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
