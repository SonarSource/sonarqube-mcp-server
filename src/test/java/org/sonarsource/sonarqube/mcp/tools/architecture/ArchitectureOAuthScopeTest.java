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
package org.sonarsource.sonarqube.mcp.tools.architecture;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.http.HttpClientProvider;
import org.sonarsource.sonarqube.mcp.serverapi.EndpointParams;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.transport.OAuthScopeContext;
import org.sonarsource.sonarqube.mcp.transport.PerRequestToolFilteringHandler;
import reactor.core.publisher.Mono;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.INTENDED_JAVA_MODEL;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.MAIN_BRANCH_ID;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.intendedArchitectures;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.stubMainBranch;

class ArchitectureOAuthScopeTest {
  @SonarQubeMcpServerTest
  @SuppressWarnings("unchecked")
  void it_should_check_scopes_before_project_lookup_and_preserve_preview_and_apply(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    var backend = harness.getMockSonarQubeServer();
    var path = ArchitectureApi.INTENDED_ARCHITECTURES_PATH + "?projectId=" + MAIN_BRANCH_ID;
    var next = INTENDED_JAVA_MODEL.replace("domain", "infra");
    backend.stubFor(get(path).willReturn(okJson(intendedArchitectures(INTENDED_JAVA_MODEL))));
    backend.stubFor(patch(ArchitectureApi.INTENDED_ARCHITECTURES_PATH + "/11111111-1111-4111-8111-111111111111")
      .inScenario("scope-save").whenScenarioStateIs("Started").willSetStateTo("saved")
      .willReturn(okJson(intendedArchitectures(next))));
    backend.stubFor(get(path).inScenario("scope-save").whenScenarioStateIs("saved").willReturn(okJson(intendedArchitectures(next))));
    var clients = new HttpClientProvider("architecture-scope-test");
    var baseUrl = backend.baseUrl();
    var api = new ServerApi(new ServerApiHelper(new EndpointParams(baseUrl, "org", baseUrl, true), clients.getHttpClient("token")), true);
    var getTool = new ArchitectureGetTool(() -> api, true, null);
    var setTool = new ArchitectureSetTool(() -> api, true, null);
    var tools = List.<Tool>of(getTool, setTool);
    var delegate = mock(McpStatelessServerHandler.class);
    when(delegate.handleRequest(any(), any())).thenAnswer(invocation -> {
      var request = invocation.getArgument(1, McpSchema.JSONRPCRequest.class);
      var params = (Map<String, Object>) request.params();
      var tool = tools.stream().filter(candidate -> candidate.definition().name().equals(params.get("name"))).findFirst().orElseThrow();
      var result = tool.execute(new Tool.Arguments((Map<String, Object>) params.get("arguments"), null));
      return Mono.just(new McpSchema.JSONRPCResponse(McpSchema.JSONRPC_VERSION, request.id(), result.toCallToolResult(), null));
    });
    var handler = new PerRequestToolFilteringHandler(delegate, tools);
    var apply = Map.<String, Object>of("target", "intended", "projectKey", "my_project", "model", next, "dryRun", false);
    try {
      var before = backend.getReceivedRequests().size();
      for (var scopes : List.of(Set.of("read:all"), Set.of("write:all"))) {
        var denied = handler.handleRequest(context(scopes), call("architecture_set", apply)).block();
        assertThat(denied.error()).isNotNull();
        assertThat(denied.error().message()).contains("Insufficient OAuth scope");
        assertThat(backend.getReceivedRequests()).hasSize(before);
      }
      var read = handler.handleRequest(context(Set.of("read:all")), call("architecture_get", Map.of("target", "intended", "projectKey", "my_project"))).block();
      assertThat(read.error()).isNull();
      assertThat(((McpSchema.CallToolResult) read.result()).isError()).isFalse();
      var deniedRead = handler.handleRequest(context(Set.of("write:all")), call("architecture_get", Map.of("target", "intended", "projectKey", "my_project"))).block();
      assertThat(deniedRead.error()).isNotNull();
      for (var dryRun : List.of(true, false)) {
        var result = handler.handleRequest(context(Set.of("read:all", "write:all")), call("architecture_set",
          Map.of("target", "intended", "projectKey", "my_project", "model", next, "dryRun", dryRun))).block();
        assertThat(result.error()).isNull();
        var content = (McpSchema.CallToolResult) result.result();
        assertThat(content.isError()).as(content.content().toString()).isFalse();
        assertThat(content.content().toString()).contains(dryRun ? "dry-run" : "replaced");
        assertThat(backend.getReceivedBodies("PATCH", ArchitectureApi.INTENDED_ARCHITECTURES_PATH)).hasSize(dryRun ? 0 : 1);
      }
    } finally {
      clients.shutdown();
    }
  }

  private static McpTransportContext context(Set<String> scopes) {
    return McpTransportContext.create(Map.of(OAuthScopeContext.CONTEXT_KEY, new OAuthScopeContext(scopes)));
  }

  private static McpSchema.JSONRPCRequest call(String tool, Map<String, Object> arguments) {
    return new McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION, "tools/call", "1", Map.of("name", tool, "arguments", arguments));
  }
}
