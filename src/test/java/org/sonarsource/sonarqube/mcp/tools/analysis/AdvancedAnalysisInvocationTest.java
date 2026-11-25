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

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.sonarsource.sonarqube.mcp.analytics.AnalyticsService;
import org.sonarsource.sonarqube.mcp.analytics.ConnectionContext;
import org.sonarsource.sonarqube.mcp.analytics.ToolInvocationResult;
import org.sonarsource.sonarqube.mcp.http.HttpClientProvider;
import org.sonarsource.sonarqube.mcp.serverapi.EndpointParams;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.a3s.A3sAnalysisApi;
import org.sonarsource.sonarqube.mcp.serverapi.a3s.request.AnalysisCreationRequest;
import org.sonarsource.sonarqube.mcp.slcore.BackendService;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;
import org.sonarsource.sonarqube.mcp.tools.ToolExecutor;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdvancedAnalysisInvocationTest {

  private static final String INVOCATION_HEADER = "x-sonar-invocation-id";

  @RegisterExtension
  static WireMockExtension server = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort()).build();

  @TempDir
  Path workspace;

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void it_should_correlate_concurrent_analysis_requests_with_tool_analytics(boolean cloud) throws Exception {
    var path = cloud ? A3sAnalysisApi.ANALYSES_PATH : "/api/v2" + A3sAnalysisApi.A3S_ANALYSES_PATH;
    server.stubFor(post(path).willReturn(okJson("{\"issues\":[]}").withFixedDelay(100)));
    Files.writeString(workspace.resolve("Main.java"), "class Main {}");
    var api = mock(ServerApi.class, RETURNS_DEEP_STUBS);
    var analysisApi = analysisApi(cloud);
    when(api.a3sAnalysisApi()).thenReturn(analysisApi);
    // Use Server routing here to avoid unrelated Cloud entitlement lookups in the tool.
    var tool = new RunAdvancedCodeAnalysisTool(() -> api, null, workspace, cloud);
    var analytics = mock(AnalyticsService.class);
    doAnswer(call -> {
      call.getArgument(0, Runnable.class).run();
      return null;
    }).when(analytics).submit(any(Runnable.class));
    var executor = new ToolExecutor(mock(BackendService.class), analytics, ConnectionContext.empty(), null, null, ToolCategory::all);
    var request = McpSchema.CallToolRequest.builder(RunAdvancedCodeAnalysisTool.TOOL_NAME)
      .arguments(Map.of("projectKey", "project", "branch", "main", "filePath", "Main.java"))
      .meta(Map.of("invocation_id", "caller-supplied-id"))
      .build();

    try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
      var results = threads.invokeAll(List.<Callable<McpSchema.CallToolResult>>of(() -> executor.execute(tool, request), () -> executor.execute(tool, request)));
      for (var result : results) {
        assertThat(result.get().isError()).isFalse();
      }
    }

    var events = ArgumentCaptor.forClass(ToolInvocationResult.class);
    verify(analytics, times(2)).notifyToolInvoked(events.capture());
    var ids = events.getAllValues().stream().map(ToolInvocationResult::invocationId).toList();
    assertThat(ids).doesNotHaveDuplicates().doesNotContain("caller-supplied-id");
    var requests = server.findAll(postRequestedFor(urlEqualTo(path)));
    assertThat(requests).hasSize(2);
    assertThat(requests).extracting(r -> r.getHeader(INVOCATION_HEADER)).containsExactlyInAnyOrderElementsOf(ids);
    for (var id : ids) {
      server.verify(1, postRequestedFor(urlEqualTo(path))
        .withHeader(INVOCATION_HEADER, equalTo(id))
        .withHeader("Authorization", equalTo("Bearer test-token"))
        .withHeader("Content-Type", equalTo("application/json"))
        .withRequestBody(containing("class Main {}")));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void it_should_omit_the_header_without_invocation_metadata(boolean cloud) {
    var path = cloud ? A3sAnalysisApi.ANALYSES_PATH : "/api/v2" + A3sAnalysisApi.A3S_ANALYSES_PATH;
    server.stubFor(post(path).willReturn(okJson("{\"issues\":[]}")));

    var response = analysisApi(cloud).analyze(new AnalysisCreationRequest("org", "project", "main", "Main.java", "class Main {}", "MAIN"));

    assertThat(response.issues()).isEmpty();
    server.verify(postRequestedFor(urlEqualTo(path)).withoutHeader(INVOCATION_HEADER));
  }

  private static A3sAnalysisApi analysisApi(boolean cloud) {
    var client = new HttpClientProvider("test").getHttpClient("test-token");
    return new A3sAnalysisApi(new ServerApiHelper(new EndpointParams(server.baseUrl(), "org", null, cloud), client));
  }
}
