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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.sonarsource.sonarqube.mcp.configuration.McpServerLaunchConfiguration;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.a3s.A3sAnalysisApi;
import org.sonarsource.sonarqube.mcp.serverapi.cag.CagApi;
import org.sonarsource.sonarqube.mcp.serverapi.organizations.OrganizationsApi;
import org.sonarsource.sonarqube.mcp.serverapi.plugins.PluginsApi;
import org.sonarsource.sonarqube.mcp.serverapi.qualityprofiles.QualityProfilesApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;

class RunAdvancedCodeAnalysisToolTests {

  private static final Map<String, String> ADVANCED_ANALYSIS_ENV = Map.of("SONARQUBE_ORG", "my-org");
  private static Path workspaceDir;
  private static Path fileToAnalyze;

  @BeforeAll
  static void setup() throws IOException {
    workspaceDir = Files.createTempDirectory("sonar-mcp-workspace");
    fileToAnalyze = workspaceDir.resolve("src/Main.java");
    Files.createDirectories(fileToAnalyze.getParent());
    Files.writeString(fileToAnalyze, "class Main {}");
    System.setProperty(McpServerLaunchConfiguration.MCP_WORKSPACE_PATH_OVERRIDE_PROPERTY, workspaceDir.toString());
  }

  @AfterAll
  static void tearDown() throws IOException {
    System.clearProperty(McpServerLaunchConfiguration.MCP_WORKSPACE_PATH_OVERRIDE_PROPERTY);
    Files.deleteIfExists(fileToAnalyze);
    Files.deleteIfExists(fileToAnalyze.getParent());
    Files.deleteIfExists(workspaceDir);
  }

  @SonarQubeMcpServerTest
  void it_should_validate_annotations(SonarQubeMcpServerTestHarness harness) {
    stubAdvancedAnalysisEnabled(harness);
    stubAnalysisResponse(harness, SIMPLE_RESPONSE);
    var mcpClient = harness.newClient(ADVANCED_ANALYSIS_ENV);

    var tool = mcpClient.listTools().stream()
      .filter(t -> t.name().equals(RunAdvancedCodeAnalysisTool.TOOL_NAME))
      .findFirst()
      .orElseThrow();

    assertThat(tool.annotations())
      .isNotNull()
      .satisfies(annotations -> {
        assertThat(annotations.readOnlyHint()).isTrue();
        assertThat(annotations.openWorldHint()).isTrue();
      });
    assertThat(tool.outputSchema()).isNull();
  }

  @SonarQubeMcpServerTest
  void it_should_be_reachable_via_vortex_toolset_alone(SonarQubeMcpServerTestHarness harness) {
    stubAdvancedAnalysisEnabled(harness);
    stubAnalysisResponse(harness, SIMPLE_RESPONSE);
    var mcpClient = harness.newClient(Map.of("SONARQUBE_ORG", "my-org", "SONARQUBE_TOOLSETS", "vortex"));

    var toolNames = mcpClient.listTools().stream().map(McpSchema.Tool::name).toList();

    assertThat(toolNames).contains(RunAdvancedCodeAnalysisTool.TOOL_NAME);
  }

  @SonarQubeMcpServerTest
  void it_should_be_reachable_when_cag_and_a3s_are_both_entitled(SonarQubeMcpServerTestHarness harness) {
    harness.stubCagEntitlement(true);
    harness.stubA3sEnabled(true);
    var mcpClient = harness.newClient(ADVANCED_ANALYSIS_ENV);

    var toolNames = mcpClient.listTools().stream().map(McpSchema.Tool::name).toList();

    assertThat(toolNames).contains(RunAdvancedCodeAnalysisTool.TOOL_NAME);
  }

  @SonarQubeMcpServerTest
  void it_should_run_on_server_using_nil_organization_placeholder(SonarQubeMcpServerTestHarness harness) {
    harness.stubServerCagEntitlement(true);
    harness.stubServerA3sEntitlement(true);
    stubServerAnalysisResponse(harness, SIMPLE_RESPONSE);
    var mcpClient = harness.newClient(Map.of("SONARQUBE_FORCE_LOCAL_ANALYSIS", "true"));

    var result = mcpClient.callTool(
      RunAdvancedCodeAnalysisTool.TOOL_NAME,
      Map.of(
        "projectKey", "my-project",
        "branch", "main",
        "filePath", "src/Main.java"
      ));

    assertResultEquals(result, """
      {
        "issues" : [ ]
      }""");
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/api/v2" + A3sAnalysisApi.A3S_ANALYSES_PATH)).isTrue();
    assertThat(harness.getMockSonarQubeServer().getReceivedRequests())
      .anyMatch(request -> request.body().contains(CagApi.SERVER_ORGANIZATION_ID_PLACEHOLDER));
  }

  @SonarQubeMcpServerTest
  void it_should_not_register_vortex_when_cag_is_entitled_but_a3s_is_not(SonarQubeMcpServerTestHarness harness) {
    harness.stubCagEntitlement(true);
    harness.stubA3sEnabled(false);
    var client = harness.newClient(Map.of("SONARQUBE_ORG", "my-org", "SONARQUBE_TOOLSETS", "analysis"));

    assertThat(client.listTools()).extracting(McpSchema.Tool::name)
      .contains(AnalyzeCodeSnippetTool.TOOL_NAME).doesNotContain(RunAdvancedCodeAnalysisTool.TOOL_NAME);
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(A3sAnalysisApi.ANALYSES_PATH)).isZero();
  }

  @SonarQubeMcpServerTest
  void it_should_allow_mounted_local_analysis_for_unentitled_org(SonarQubeMcpServerTestHarness harness) throws IOException {
    harness.stubCagEntitlement(false);
    harness.stubA3sEnabled(false);
    harness.getMockSonarQubeServer().stubFor(get(QualityProfilesApi.SEARCH_PATH + "?organization=my-org&defaults=true")
      .willReturn(okJson("{\"profiles\":[]}")));
    var file = workspaceDir.resolve("src/LocalFallback.php");
    Files.writeString(file, "<?php echo 'hello';");
    try {
      var client = harness.withPlugins().newClient(Map.of("SONARQUBE_ORG", "my-org", "SONARQUBE_TOOLSETS", "analysis"));
      assertThat(client.listTools()).extracting(McpSchema.Tool::name)
        .contains(AnalyzeCodeSnippetTool.TOOL_NAME).doesNotContain(RunAdvancedCodeAnalysisTool.TOOL_NAME);

      var local = client.callTool(AnalyzeCodeSnippetTool.TOOL_NAME,
        Map.of("filePath", "src/LocalFallback.php", "language", "php"));
      assertThat(local.isError()).isFalse();
      assertResultEquals(local,
        "{\"issues\":[],\"issueCount\":0,\"deprecationNotice\":\"%s\"}".formatted(AnalyzeCodeSnippetTool.DEPRECATION_NOTICE));
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(QualityProfilesApi.SEARCH_PATH)).isEqualTo(1);
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(OrganizationsApi.ORGANIZATIONS_PATH)).isEqualTo(1);
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(CagApi.CAG_ENTITLEMENT_PATH)).isEqualTo(1);
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(A3sAnalysisApi.A3S_ORG_CONFIG_PATH)).isZero();
    } finally {
      Files.deleteIfExists(file);
    }
  }

  @SonarQubeMcpServerTest
  void it_should_return_issues_with_flows(SonarQubeMcpServerTestHarness harness) {
    stubAdvancedAnalysisEnabled(harness);
    stubAnalysisResponse(harness, RESPONSE_WITH_FLOWS);
    var mcpClient = harness.newClient(ADVANCED_ANALYSIS_ENV);

    var result = mcpClient.callTool(
      RunAdvancedCodeAnalysisTool.TOOL_NAME,
      Map.of(
        "projectKey", "my-project",
        "branch", "main",
        "filePath", "src/Main.java"
      ));

    assertResultEquals(result, """
      {
        "issues" : [ {
          "id" : "issue-1",
          "filePath" : "src/Main.java",
          "message" : "Null pointer dereference",
          "rule" : "java:S2259",
          "textRange" : {
            "startLine" : 10,
            "endLine" : 10
          },
          "flows" : [ {
            "type" : "DATA",
            "description" : "Data flow",
            "locations" : [ {
              "textRange" : {
                "startLine" : 5,
                "endLine" : 5
              },
              "message" : "Source of null",
              "file" : "src/Main.java"
            } ]
          } ]
        } ]
      }""");
  }

  @SonarQubeMcpServerTest
  void it_should_return_issues_with_flows_missing_type(SonarQubeMcpServerTestHarness harness) {
    stubAdvancedAnalysisEnabled(harness);
    stubAnalysisResponse(harness, RESPONSE_WITH_FLOWS_WITHOUT_TYPE);
    var mcpClient = harness.newClient(ADVANCED_ANALYSIS_ENV);

    var result = mcpClient.callTool(
      RunAdvancedCodeAnalysisTool.TOOL_NAME,
      Map.of(
        "projectKey", "my-project",
        "branch", "main",
        "filePath", "src/Main.java"
      ));

    assertResultEquals(result, """
      {
        "issues" : [ {
          "id" : "issue-1",
          "filePath" : "src/Main.java",
          "message" : "Null pointer dereference",
          "rule" : "java:S2259",
          "textRange" : {
            "startLine" : 10,
            "endLine" : 10
          },
          "flows" : [ {
            "description" : "Data flow",
            "locations" : [ {
              "textRange" : {
                "startLine" : 5,
                "endLine" : 5
              },
              "message" : "Source of null",
              "file" : "src/Main.java"
            } ]
          } ]
        } ]
      }""");
  }

  @SonarQubeMcpServerTest
  void it_should_return_issues_with_flows_missing_locations(SonarQubeMcpServerTestHarness harness) {
    stubAdvancedAnalysisEnabled(harness);
    stubAnalysisResponse(harness, RESPONSE_WITH_FLOWS_WITHOUT_LOCATIONS);
    var mcpClient = harness.newClient(ADVANCED_ANALYSIS_ENV);

    var result = mcpClient.callTool(
      RunAdvancedCodeAnalysisTool.TOOL_NAME,
      Map.of(
        "projectKey", "my-project",
        "branch", "main",
        "filePath", "src/Main.java"
      ));

    assertResultEquals(result, """
      {
        "issues" : [ {
          "id" : "issue-1",
          "filePath" : "src/Main.java",
          "message" : "Null pointer dereference",
          "rule" : "java:S2259",
          "flows" : [ {
            "type" : "DATA"
          } ]
        } ]
      }""");
  }

  @SonarQubeMcpServerTest
  void it_should_allow_issues_without_file_path(SonarQubeMcpServerTestHarness harness) {
    stubAdvancedAnalysisEnabled(harness);
    stubAnalysisResponse(harness, RESPONSE_WITHOUT_FILE_PATH);
    var mcpClient = harness.newClient(ADVANCED_ANALYSIS_ENV);

    var result = mcpClient.callTool(
      RunAdvancedCodeAnalysisTool.TOOL_NAME,
      Map.of(
        "projectKey", "my-project",
        "branch", "main",
        "filePath", "src/Main.java"
      ));

    assertResultEquals(result, """
      {
        "issues" : [ {
          "id" : "issue-1",
          "message" : "Add a 'package-info.java' file",
          "rule" : "java:S1228"
        } ]
      }""");
  }

  @SonarQubeMcpServerTest
  void it_should_return_an_error_on_api_failure(SonarQubeMcpServerTestHarness harness) {
    stubAdvancedAnalysisEnabled(harness);
    harness.getMockSonarQubeServer().stubFor(post(A3sAnalysisApi.ANALYSES_PATH)
      .willReturn(aResponse().withStatus(403)));
    var mcpClient = harness.newClient(ADVANCED_ANALYSIS_ENV);

    var result = mcpClient.callTool(
      RunAdvancedCodeAnalysisTool.TOOL_NAME,
      Map.of(
        "projectKey", "my-project",
        "branch", "main",
        "filePath", "src/Main.java"
      ));

    assertThat(result).isEqualTo(McpSchema.CallToolResult.builder()
      .isError(true)
      .addTextContent("An error occurred during the tool execution: SonarQube answered with Forbidden. Please verify your token has the " +
        "required permissions for this operation.")
      .build());
  }

  @SonarQubeMcpServerTest
  void it_should_succeed_and_surface_analysis_errors_alongside_issues(SonarQubeMcpServerTestHarness harness) {
    stubAdvancedAnalysisEnabled(harness);
    stubAnalysisResponse(harness, RESPONSE_WITH_ERRORS_AND_ISSUES);
    var mcpClient = harness.newClient(ADVANCED_ANALYSIS_ENV);

    var result = mcpClient.callTool(
      RunAdvancedCodeAnalysisTool.TOOL_NAME,
      Map.of(
        "projectKey", "my-project",
        "branch", "main",
        "filePath", "src/Main.java"
      ));

    assertResultEquals(result, """
      {
        "issues" : [ {
          "id" : "issue-1",
          "filePath" : "src/Main.java",
          "message" : "Null pointer dereference",
          "rule" : "java:S2259"
        } ],
        "analysisErrors" : [ {
          "code" : "SERVICE_CALL_ERROR",
          "message" : "Error while calling language analysis service"
        }, {
          "code" : "PARSE_ERROR",
          "message" : "Failed to parse analysis input"
        } ]
      }""");
  }

  @SonarQubeMcpServerTest
  void it_should_succeed_and_surface_analysis_errors_when_no_issues_were_found(SonarQubeMcpServerTestHarness harness) {
    stubAdvancedAnalysisEnabled(harness);
    stubAnalysisResponse(harness, RESPONSE_WITH_ERRORS_NO_ISSUES);
    var mcpClient = harness.newClient(ADVANCED_ANALYSIS_ENV);

    var result = mcpClient.callTool(
      RunAdvancedCodeAnalysisTool.TOOL_NAME,
      Map.of(
        "projectKey", "my-project",
        "branch", "main",
        "filePath", "src/Main.java"
      ));

    assertResultEquals(result, """
      {
        "issues" : [ ],
        "analysisErrors" : [ {
          "code" : "SERVICE_CALL_ERROR",
          "message" : "Error while calling language analysis service"
        }, {
          "code" : "PARSE_ERROR",
          "message" : "Failed to parse analysis input"
        } ]
      }""");
  }

  @SonarQubeMcpServerTest
  void it_should_return_error_when_file_path_is_outside_workspace(SonarQubeMcpServerTestHarness harness) {
    stubAdvancedAnalysisEnabled(harness);
    var mcpClient = harness.newClient(ADVANCED_ANALYSIS_ENV);

    var result = mcpClient.callTool(
      RunAdvancedCodeAnalysisTool.TOOL_NAME,
      Map.of(
        "projectKey", "my-project",
        "branch", "main",
        "filePath", "../../etc/passwd"
      ));

    assertThat(result.isError()).isTrue();
    assertThat(result.toString()).contains("outside the configured");
  }

  @SonarQubeMcpServerTest
  void it_should_return_error_when_file_does_not_exist_in_workspace(SonarQubeMcpServerTestHarness harness) {
    stubAdvancedAnalysisEnabled(harness);
    var mcpClient = harness.newClient(ADVANCED_ANALYSIS_ENV);

    var result = mcpClient.callTool(
      RunAdvancedCodeAnalysisTool.TOOL_NAME,
      Map.of(
        "projectKey", "my-project",
        "branch", "main",
        "filePath", "src/NonExistent.java"
      ));

    assertThat(result.isError()).isTrue();
    assertThat(result.toString()).contains("Could not read file");
  }

  @SonarQubeMcpServerTest
  void it_should_analyze_entitled_org_in_a_mixed_session(SonarQubeMcpServerTestHarness harness) {
    stubStartupOrganizations(harness, false);
    stubSelectedOrganization(harness, true, true);
    harness.getMockSonarQubeServer().stubFor(post(A3sAnalysisApi.ANALYSES_PATH)
      .withRequestBody(matchingJsonPath("$.organizationKey", equalTo("selected-org"))).willReturn(okJson(SIMPLE_RESPONSE)));
    var client = harness.newClient(Map.of("SONARQUBE_IS_CLOUD", "true", "SONARQUBE_TOOLSETS", "vortex"));
    var definition = client.listTools().stream().filter(tool -> tool.name().equals(RunAdvancedCodeAnalysisTool.TOOL_NAME)).findFirst().orElseThrow();
    assertThat(definition.inputSchema().get("properties")).asInstanceOf(MAP).containsKey("organization");
    assertThat(definition.inputSchema().get("required")).asInstanceOf(LIST).doesNotContain("organization");
    assertThat(client.callTool(RunAdvancedCodeAnalysisTool.TOOL_NAME, selectedArguments()).isError()).isFalse();
    assertThat(client.callTool(RunAdvancedCodeAnalysisTool.TOOL_NAME, Map.of("projectKey", "project", "branch", "main", "filePath", "src/Main.java")).isError()).isTrue();
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(A3sAnalysisApi.ANALYSES_PATH)).isEqualTo(1);
  }

  @SonarQubeMcpServerTest
  void it_should_require_selection_when_multiple_orgs_have_vortex(SonarQubeMcpServerTestHarness harness) {
    stubStartupOrganizations(harness, true);
    stubSelectedOrganization(harness, true, true);
    harness.getMockSonarQubeServer().stubFor(post(A3sAnalysisApi.ANALYSES_PATH).willReturn(okJson(SIMPLE_RESPONSE)));
    var client = harness.newClient(Map.of("SONARQUBE_IS_CLOUD", "true", "SONARQUBE_TOOLSETS", "vortex"));
    assertThat(client.callTool(RunAdvancedCodeAnalysisTool.TOOL_NAME, selectedArguments()).isError()).isFalse();
    var unresolved = client.callTool(RunAdvancedCodeAnalysisTool.TOOL_NAME, Map.of("projectKey", "project", "branch", "main", "filePath", "src/Main.java"));
    assertThat(unresolved.toString()).contains("list_organizations");
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(A3sAnalysisApi.ANALYSES_PATH)).isEqualTo(1);
  }

  @SonarQubeMcpServerTest
  void it_should_hide_organization_and_reject_overrides_for_fixed_analysis_tools(SonarQubeMcpServerTestHarness harness) {
    stubAdvancedAnalysisEnabled(harness);
    for (var forceLocal : new boolean[]{false, true}) {
      var client = harness.newClient(Map.of("SONARQUBE_ORG", "my-org", "SONARQUBE_TOOLSETS", "analysis",
        "SONARQUBE_FORCE_LOCAL_ANALYSIS", Boolean.toString(forceLocal)));
      var name = forceLocal ? AnalyzeCodeSnippetTool.TOOL_NAME : RunAdvancedCodeAnalysisTool.TOOL_NAME;
      var definition = client.listTools().stream().filter(tool -> tool.name().equals(name)).findFirst().orElseThrow();
      assertThat(definition.inputSchema().get("properties")).asInstanceOf(MAP).doesNotContainKey("organization");
      var arguments = forceLocal ? Map.<String, Object>of("organization", "selected-org", "filePath", "src/Main.java", "language", "java") : selectedArguments();
      var result = client.callTool(name, arguments);
      assertThat(result.isError()).isTrue();
      assertThat(result.toString()).contains("organization", "not defined in the schema");
    }
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(QualityProfilesApi.SEARCH_PATH)).isZero();
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(A3sAnalysisApi.ANALYSES_PATH)).isZero();
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining("organizationKey=selected-org")).isZero();
  }

  @SonarQubeMcpServerTest
  void it_should_require_both_vortex_entitlements_for_selected_org(SonarQubeMcpServerTestHarness harness) {
    stubStartupOrganizations(harness, true);
    stubSelectedOrganization(harness, true, false);
    var client = harness.newClient(Map.of("SONARQUBE_IS_CLOUD", "true", "SONARQUBE_TOOLSETS", "vortex"));
    var arguments = new java.util.HashMap<>(selectedArguments());
    arguments.put("filePath", "src/DoesNotExist.java");
    var rejected = client.callTool(RunAdvancedCodeAnalysisTool.TOOL_NAME, arguments);
    assertThat(rejected.isError()).isTrue();
    assertThat(((McpSchema.TextContent) rejected.content().getFirst()).text())
      .isEqualTo("Vortex is not enabled for the selected SonarQube Cloud organization.");
    stubSelectedOrganization(harness, false, true);
    var freshClient = harness.newClient(Map.of("SONARQUBE_IS_CLOUD", "true", "SONARQUBE_TOOLSETS", "vortex"));
    var freshRejected = freshClient.callTool(RunAdvancedCodeAnalysisTool.TOOL_NAME, arguments);
    assertThat(freshRejected.isError()).isTrue();
    assertThat(((McpSchema.TextContent) freshRejected.content().getFirst()).text())
      .isEqualTo("Vortex is not enabled for the selected SonarQube Cloud organization.");
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(CagApi.CAG_ENTITLEMENT_PATH + "selected-uuid")).isEqualTo(2);
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(A3sAnalysisApi.A3S_ORG_CONFIG_PATH + "selected-uuid")).isEqualTo(1);
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(A3sAnalysisApi.ANALYSES_PATH)).isZero();
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(QualityProfilesApi.SEARCH_PATH)).isZero();
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(PluginsApi.INSTALLED_PLUGINS_PATH)).isZero();
  }

  @SonarQubeMcpServerTest
  void it_should_force_local_analysis_for_entitled_and_unentitled_orgs(SonarQubeMcpServerTestHarness harness) throws IOException {
    stubStartupOrganizations(harness, true);
    stubSelectedOrganization(harness, false, false);
    for (var org : List.of("my-org", "selected-org")) {
      harness.getMockSonarQubeServer().stubFor(get(QualityProfilesApi.SEARCH_PATH + "?organization=" + org + "&defaults=true")
        .willReturn(okJson("{\"profiles\":[]}")));
    }
    var file = workspaceDir.resolve("src/Local.php");
    Files.writeString(file, "<?php echo 'hello';");
    try {
      var client = harness.withPlugins().newClient(Map.of("SONARQUBE_IS_CLOUD", "true", "SONARQUBE_TOOLSETS", "analysis",
        "SONARQUBE_FORCE_LOCAL_ANALYSIS", "true"));
      assertThat(client.listTools()).extracting(McpSchema.Tool::name)
        .contains(AnalyzeCodeSnippetTool.TOOL_NAME).doesNotContain(RunAdvancedCodeAnalysisTool.TOOL_NAME);
      for (var org : List.of("selected-org", "my-org", "selected-org")) {
        assertResultEquals(client.callTool(AnalyzeCodeSnippetTool.TOOL_NAME,
          Map.of("organization", org, "filePath", "src/Local.php", "language", "php")),
          "{\"issues\":[],\"issueCount\":0,\"deprecationNotice\":\"%s\"}".formatted(AnalyzeCodeSnippetTool.DEPRECATION_NOTICE));
      }
      var unresolved = client.callTool(AnalyzeCodeSnippetTool.TOOL_NAME, Map.of("filePath", "src/Local.php", "language", "php"));
      assertThat(unresolved.isError()).isTrue();
      assertThat(unresolved.toString()).contains("list_organizations");
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(QualityProfilesApi.SEARCH_PATH)).isEqualTo(3);
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(CagApi.CAG_ENTITLEMENT_PATH)).isZero();
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(A3sAnalysisApi.ANALYSES_PATH)).isZero();
    } finally {
      Files.deleteIfExists(file);
    }
  }

  @SonarQubeMcpServerTest
  void it_should_reuse_startup_entitlement_for_selected_org(SonarQubeMcpServerTestHarness harness) {
    stubStartupOrganizations(harness, false);
    stubSelectedOrganization(harness, true, true);
    stubAnalysisResponse(harness, SIMPLE_RESPONSE);
    var client = harness.newClient(Map.of("SONARQUBE_IS_CLOUD", "true", "SONARQUBE_TOOLSETS", "analysis"));

    assertThat(client.listTools()).extracting(McpSchema.Tool::name)
      .contains(RunAdvancedCodeAnalysisTool.TOOL_NAME).doesNotContain(AnalyzeCodeSnippetTool.TOOL_NAME);
    assertThat(client.callTool(RunAdvancedCodeAnalysisTool.TOOL_NAME, selectedArguments()).isError()).isFalse();
    assertThat(client.callTool(RunAdvancedCodeAnalysisTool.TOOL_NAME, selectedArguments()).isError()).isFalse();
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(OrganizationsApi.ORGANIZATIONS_PATH)).isEqualTo(1);
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(CagApi.CAG_ENTITLEMENT_PATH + "selected-uuid")).isEqualTo(1);
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(A3sAnalysisApi.A3S_ORG_CONFIG_PATH + "selected-uuid")).isEqualTo(1);
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(PluginsApi.INSTALLED_PLUGINS_PATH)).isZero();
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(QualityProfilesApi.SEARCH_PATH)).isZero();
  }

  @SonarQubeMcpServerTest
  void it_should_retry_a_failed_startup_check_without_caching_it_as_unentitled(SonarQubeMcpServerTestHarness harness) {
    stubStartupOrganizations(harness, true);
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH))
      .withQueryParam("organizationKey", com.github.tomakehurst.wiremock.client.WireMock.absent())
      .willReturn(okJson("""
        [{"key":"selected-org","uuidV4":"selected-uuid"},{"key":"my-org","uuidV4":"00000000-0000-0000-0000-000000000001"}]
        """)));
    harness.getMockSonarQubeServer().stubFor(get(CagApi.CAG_ENTITLEMENT_PATH + "selected-uuid").willReturn(aResponse().withStatus(503)));
    stubAnalysisResponse(harness, SIMPLE_RESPONSE);
    var client = harness.newClient(Map.of("SONARQUBE_IS_CLOUD", "true", "SONARQUBE_TOOLSETS", "analysis"));
    // The HTTP client retries a 503 once before the failed startup check returns.
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(CagApi.CAG_ENTITLEMENT_PATH + "selected-uuid")).isEqualTo(2);
    stubSelectedOrganization(harness, true, true);

    assertThat(client.callTool(RunAdvancedCodeAnalysisTool.TOOL_NAME, selectedArguments()).isError()).isFalse();
    assertThat(client.callTool(RunAdvancedCodeAnalysisTool.TOOL_NAME, selectedArguments()).isError()).isFalse();

    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(OrganizationsApi.ORGANIZATIONS_PATH)).isEqualTo(1);
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(CagApi.CAG_ENTITLEMENT_PATH + "selected-uuid")).isEqualTo(3);
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(A3sAnalysisApi.A3S_ORG_CONFIG_PATH + "selected-uuid")).isEqualTo(1);
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(QualityProfilesApi.SEARCH_PATH)).isZero();
  }

  private static void stubStartupOrganizations(SonarQubeMcpServerTestHarness harness, boolean firstEntitled) {
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH))
      .withQueryParam("organizationKey", com.github.tomakehurst.wiremock.client.WireMock.absent())
      .willReturn(okJson("""
        [{"key":"my-org","uuidV4":"00000000-0000-0000-0000-000000000001"},{"key":"selected-org","uuidV4":"selected-uuid"}]
        """)));
    harness.stubCagEntitlement(firstEntitled);
    harness.stubA3sEnabled(firstEntitled);
  }

  private static Map<String, Object> selectedArguments() {
    return Map.of("organization", "selected-org", "projectKey", "project", "branch", "main", "filePath", "src/Main.java");
  }

  private static void stubSelectedOrganization(SonarQubeMcpServerTestHarness harness, boolean cag, boolean a3s) {
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH))
      .withQueryParam("organizationKey", equalTo("selected-org"))
      .willReturn(okJson("[{\"key\":\"selected-org\",\"uuidV4\":\"selected-uuid\"}]")));
    harness.getMockSonarQubeServer().stubFor(get(CagApi.CAG_ENTITLEMENT_PATH + "selected-uuid")
      .willReturn(okJson("{\"hasEntitlement\":" + cag + ",\"allowed\":true}")));
    harness.getMockSonarQubeServer().stubFor(get(A3sAnalysisApi.A3S_ORG_CONFIG_PATH + "selected-uuid")
      .willReturn(okJson("{\"id\":\"selected-uuid\",\"enabled\":" + a3s + ",\"eligible\":true}")));
  }

  /**
   * Vortex requires combined CAG + A3S entitlement, so enabling advanced analysis for tests
   * means stubbing both.
   */
  private static void stubAdvancedAnalysisEnabled(SonarQubeMcpServerTestHarness harness) {
    harness.stubA3sEnabled(true);
    harness.stubCagEntitlement(true);
  }

  private static void stubAnalysisResponse(SonarQubeMcpServerTestHarness harness, String responseBody) {
    harness.getMockSonarQubeServer().stubFor(post(A3sAnalysisApi.ANALYSES_PATH)
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(responseBody)));
  }

  private static void stubServerAnalysisResponse(SonarQubeMcpServerTestHarness harness, String responseBody) {
    harness.getMockSonarQubeServer().stubFor(post("/api/v2" + A3sAnalysisApi.A3S_ANALYSES_PATH)
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(responseBody)));
  }

  private static final String SIMPLE_RESPONSE = """
    {
      "id": "analysis-1",
      "issues": [],
      "patchResult": null
    }
    """;

  @SonarQubeMcpServerTest
  void it_should_return_patch_result_when_server_calculates_it(SonarQubeMcpServerTestHarness harness) {
    stubAdvancedAnalysisEnabled(harness);
    stubAnalysisResponse(harness, RESPONSE_WITH_PATCH_RESULT);
    var mcpClient = harness.newClient(ADVANCED_ANALYSIS_ENV);

    var result = mcpClient.callTool(
      RunAdvancedCodeAnalysisTool.TOOL_NAME,
      Map.of(
        "projectKey", "my-project",
        "branch", "main",
        "filePath", "src/Main.java"
      ));

    assertResultEquals(result, """
      {
        "issues" : [ ],
        "patchResult" : {
          "newIssues" : [ {
            "id" : "new-issue-1",
            "filePath" : "src/Main.java",
            "message" : "New issue introduced",
            "rule" : "java:S1234"
          } ],
          "matchedIssues" : [ {
            "id" : "matched-issue-1",
            "filePath" : "src/Main.java",
            "message" : "Existing issue still present",
            "rule" : "java:S5678"
          } ],
          "closedIssues" : [ "old-issue-1", "old-issue-2" ]
        }
      }""");
  }

  private static final String RESPONSE_WITH_FLOWS_WITHOUT_LOCATIONS = """
    {
      "id": "analysis-1",
      "issues": [
        {
          "id": "issue-1",
          "filePath": "src/Main.java",
          "message": "Null pointer dereference",
          "rule": "java:S2259",
          "flows": [
            {
              "type": "DATA"
            }
          ]
        }
      ],
      "patchResult": null
    }
    """;

  private static final String RESPONSE_WITH_FLOWS_WITHOUT_TYPE = """
    {
      "id": "analysis-1",
      "issues": [
        {
          "id": "issue-1",
          "filePath": "src/Main.java",
          "message": "Null pointer dereference",
          "rule": "java:S2259",
          "textRange": {
            "startLine": 10,
            "endLine": 10,
            "startOffset": 4,
            "endOffset": 20
          },
          "flows": [
            {
              "description": "Data flow",
              "locations": [
                {
                  "textRange": {
                    "startLine": 5,
                    "endLine": 5,
                    "startOffset": 0,
                    "endOffset": 15
                  },
                  "message": "Source of null",
                  "file": "src/Main.java"
                }
              ]
            }
          ]
        }
      ],
      "patchResult": null
    }
    """;

  private static final String RESPONSE_WITH_FLOWS = """
    {
      "id": "analysis-1",
      "issues": [
        {
          "id": "issue-1",
          "filePath": "src/Main.java",
          "message": "Null pointer dereference",
          "rule": "java:S2259",
          "textRange": {
            "startLine": 10,
            "endLine": 10,
            "startOffset": 4,
            "endOffset": 20
          },
          "flows": [
            {
              "type": "DATA",
              "description": "Data flow",
              "locations": [
                {
                  "textRange": {
                    "startLine": 5,
                    "endLine": 5,
                    "startOffset": 0,
                    "endOffset": 15
                  },
                  "message": "Source of null",
                  "file": "src/Main.java"
                }
              ]
            }
          ]
        }
      ],
      "patchResult": null
    }
    """;

  private static final String RESPONSE_WITH_PATCH_RESULT = """
    {
      "id": "analysis-1",
      "issues": [],
      "patchResult": {
        "newIssues": [
          {
            "id": "new-issue-1",
            "filePath": "src/Main.java",
            "message": "New issue introduced",
            "rule": "java:S1234",
            "textRange": null,
            "flows": []
          }
        ],
        "matchedIssues": [
          {
            "id": "matched-issue-1",
            "filePath": "src/Main.java",
            "message": "Existing issue still present",
            "rule": "java:S5678",
            "textRange": null,
            "flows": []
          }
        ],
        "closedIssues": ["old-issue-1", "old-issue-2"]
      }
    }
    """;

  private static final String RESPONSE_WITH_ERRORS_AND_ISSUES = """
    {
      "id": "analysis-1",
      "issues": [
        {
          "id": "issue-1",
          "filePath": "src/Main.java",
          "message": "Null pointer dereference",
          "rule": "java:S2259"
        }
      ],
      "patchResult": null,
      "errors": [
        {
          "code": "SERVICE_CALL_ERROR",
          "message": "Error while calling language analysis service"
        },
        {
          "code": "PARSE_ERROR",
          "message": "Failed to parse analysis input"
        }
      ]
    }
    """;

  private static final String RESPONSE_WITH_ERRORS_NO_ISSUES = """
    {
      "id": "analysis-1",
      "issues": [],
      "patchResult": null,
      "errors": [
        {
          "code": "SERVICE_CALL_ERROR",
          "message": "Error while calling language analysis service"
        },
        {
          "code": "PARSE_ERROR",
          "message": "Failed to parse analysis input"
        }
      ]
    }
    """;

  private static final String RESPONSE_WITHOUT_FILE_PATH = """
    {
      "id": "analysis-1",
      "issues": [
        {
          "id": "issue-1",
          "message": "Add a 'package-info.java' file",
          "rule": "java:S1228"
        }
      ]
    }
    """;

}
