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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfiguration;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.http.HttpClientProvider;
import org.sonarsource.sonarqube.mcp.serverapi.EndpointParams;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.a3s.A3sAnalysisApi;
import org.sonarsource.sonarqube.mcp.serverapi.cag.CagApi;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.OrganizationsApi;
import org.sonarsource.sonarqube.mcp.tools.Tool;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;

class RunAdvancedCodeAnalysisOrganizationTest {

  @SonarQubeMcpServerTest
  void it_should_validate_before_reading_files_and_select_organizations_without_changing_the_default(
    SonarQubeMcpServerTestHarness harness, @TempDir Path workspace) throws Exception {
    var mock = harness.getMockSonarQubeServer();
    for (var org : new String[]{"selected-org", "other-org"}) {
      mock.stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH)).withQueryParam("organizationKey", equalTo(org))
        .willReturn(okJson("[{\"key\":\"%s\",\"uuidV4\":\"%s-uuid\"}]".formatted(org, org))));
      mock.stubFor(get(CagApi.CAG_ENTITLEMENT_PATH + org + "-uuid").willReturn(okJson("{\"hasEntitlement\":true}")));
      mock.stubFor(get(A3sAnalysisApi.A3S_ORG_CONFIG_PATH + org + "-uuid").willReturn(okJson("{\"hasEntitlement\":true,\"enabled\":true,\"allowed\":true}")));
    }
    mock.stubFor(post(A3sAnalysisApi.ANALYSES_PATH).willReturn(okJson("{\"issues\":[]}")));
    var http = new HttpClientProvider("test");
    try {
      var api = new ServerApi(new ServerApiHelper(new EndpointParams(mock.baseUrl(), null, null, true), http.getHttpClient("token")), true);
      var tool = new RunAdvancedCodeAnalysisTool(() -> api, null, workspace, true);
      assertThat(tool.definition().inputSchema().get("properties")).asInstanceOf(MAP).containsKey("organization");
      var unresolved = new Tool.Arguments(Map.of("projectKey", "project", "branch", "main", "filePath", "missing.java"), null);
      assertThatThrownBy(() -> tool.execute(unresolved)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("list_organizations");
      assertThat(mock.countRequestsContaining(A3sAnalysisApi.ANALYSES_PATH)).isZero();

      Files.writeString(workspace.resolve("file.java"), "class File {}");
      for (var org : new String[]{"selected-org", "other-org", "selected-org"}) {
        var result = tool.execute(new Tool.Arguments(Map.of("organization", org, "projectKey", "project", "branch", "main", "filePath", "file.java"), null));
        assertThat(result.isError()).isFalse();
      }
      assertThat(api.getOrganization()).isNull();
      assertThatThrownBy(() -> tool.execute(unresolved)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("list_organizations");
      assertThat(mock.getReceivedRequests()).filteredOn(request -> !request.body().isBlank())
        .extracting(request -> com.google.gson.JsonParser.parseString(request.body()).getAsJsonObject().get("organizationKey").getAsString())
        .containsExactlyInAnyOrder("selected-org", "other-org", "selected-org");
    } finally {
      http.shutdown();
    }
  }

  @SonarQubeMcpServerTest
  void it_should_hide_and_reject_organization_for_fixed_advanced_analysis(SonarQubeMcpServerTestHarness harness, @TempDir Path workspace) {
    harness.stubCagEntitlement(true);
    harness.stubA3sEnabled(true);
    assertFixedAnalysis(harness, workspace, RunAdvancedCodeAnalysisTool.TOOL_NAME);
  }

  @SonarQubeMcpServerTest
  void it_should_hide_and_reject_organization_for_fixed_snippet_analysis(SonarQubeMcpServerTestHarness harness, @TempDir Path workspace) {
    harness.stubCagEntitlement(false);
    assertFixedAnalysis(harness, workspace, AnalyzeCodeSnippetTool.TOOL_NAME);
  }

  private static void assertFixedAnalysis(SonarQubeMcpServerTestHarness harness, Path workspace, String name) {
    System.setProperty(McpServerLaunchConfiguration.MCP_WORKSPACE_PATH_OVERRIDE_PROPERTY, workspace.toString());
    try {
      var client = harness.newClient(Map.of("SONARQUBE_ORG", "my-org", "SONARQUBE_TOOLSETS", "analysis"));
      var definition = client.listTools().stream().filter(tool -> tool.name().equals(name)).findFirst().orElseThrow();
      assertThat(definition.inputSchema().get("properties")).asInstanceOf(MAP).doesNotContainKey("organization");
      var arguments = name.equals(RunAdvancedCodeAnalysisTool.TOOL_NAME)
        ? Map.<String, Object>of("organization", "other-org", "projectKey", "project", "branch", "main", "filePath", "missing.java")
        : Map.<String, Object>of("organization", "other-org", "filePath", "missing.java", "language", "java");
      var result = client.callTool(name, arguments);
      assertThat(result.isError()).isTrue();
      assertThat(((McpSchema.TextContent) result.content().getFirst()).text()).contains("organization", "not defined in the schema");
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(A3sAnalysisApi.ANALYSES_PATH)).isZero();
    } finally {
      System.clearProperty(McpServerLaunchConfiguration.MCP_WORKSPACE_PATH_OVERRIDE_PROPERTY);
    }
  }
}
