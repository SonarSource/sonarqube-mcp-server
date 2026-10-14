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
    var mcpClient = harness.newClient();

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
  void it_should_reject_invocation_when_cag_is_entitled_but_a3s_is_not(SonarQubeMcpServerTestHarness harness) {
    harness.stubCagEntitlement(true);
    harness.stubA3sEnabled(false);
    var client = harness.newClient(Map.of("SONARQUBE_ORG", "my-org", "SONARQUBE_TOOLSETS", "vortex"));
    assertThat(client.listTools()).anyMatch(tool -> tool.name().equals(RunAdvancedCodeAnalysisTool.TOOL_NAME));
    var result = client.callTool(RunAdvancedCodeAnalysisTool.TOOL_NAME, Map.of("projectKey", "project", "branch", "main", "filePath", "src/Main.java"));
    assertThat(result.isError()).isTrue();
    assertThat(((McpSchema.TextContent) result.content().getFirst()).text())
      .isEqualTo("Vortex is not enabled for the selected SonarQube Cloud organization.");
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(A3sAnalysisApi.ANALYSES_PATH)).isZero();
  }

  @SonarQubeMcpServerTest
  void it_should_allow_mounted_local_analysis_after_vortex_rejects_unentitled_org(SonarQubeMcpServerTestHarness harness) throws IOException {
    harness.stubCagEntitlement(false);
    harness.stubA3sEnabled(false);
    harness.getMockSonarQubeServer().stubFor(get(QualityProfilesApi.SEARCH_PATH + "?organization=my-org&defaults=true")
      .willReturn(okJson("{\"profiles\":[]}")));
    var file = workspaceDir.resolve("src/LocalFallback.php");
    Files.writeString(file, "<?php echo 'hello';");
    try {
      var client = harness.withPlugins().newClient(Map.of("SONARQUBE_ORG", "my-org", "SONARQUBE_TOOLSETS", "analysis"));
      assertThat(client.listTools()).extracting(McpSchema.Tool::name)
        .contains(RunAdvancedCodeAnalysisTool.TOOL_NAME, AnalyzeCodeSnippetTool.TOOL_NAME);

      var advanced = client.callTool(RunAdvancedCodeAnalysisTool.TOOL_NAME,
        Map.of("projectKey", "project", "branch", "main", "filePath", "src/LocalFallback.php"));
      assertThat(advanced.isError()).isTrue();
      assertThat(((McpSchema.TextContent) advanced.content().getFirst()).text())
        .isEqualTo("Vortex is not enabled for the selected SonarQube Cloud organization.");
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(A3sAnalysisApi.ANALYSES_PATH)).isZero();
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(QualityProfilesApi.SEARCH_PATH)).isZero();

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
  void it_should_analyze_selected_org_when_default_is_unentitled(SonarQubeMcpServerTestHarness harness) {
    stubStartupOrganization(harness);
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
  void it_should_analyze_selected_org_when_startup_is_unresolved(SonarQubeMcpServerTestHarness harness) {
    stubSelectedOrganization(harness, true, true);
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH))
      .withQueryParam("organizationKey", com.github.tomakehurst.wiremock.client.WireMock.absent()).willReturn(okJson("[]")));
    harness.getMockSonarQubeServer().stubFor(post(A3sAnalysisApi.ANALYSES_PATH).willReturn(okJson(SIMPLE_RESPONSE)));
    var client = harness.newClient(Map.of("SONARQUBE_IS_CLOUD", "true", "SONARQUBE_TOOLSETS", "vortex"));
    assertThat(client.listTools()).anyMatch(tool -> tool.name().equals(RunAdvancedCodeAnalysisTool.TOOL_NAME));
    assertThat(client.callTool(RunAdvancedCodeAnalysisTool.TOOL_NAME, selectedArguments()).isError()).isFalse();
    var unresolved = client.callTool(RunAdvancedCodeAnalysisTool.TOOL_NAME, Map.of("projectKey", "project", "branch", "main", "filePath", "src/Main.java"));
    assertThat(unresolved.toString()).contains("list_organizations");
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(A3sAnalysisApi.ANALYSES_PATH)).isEqualTo(1);
  }

  @SonarQubeMcpServerTest
  void it_should_hide_organization_and_reject_overrides_for_fixed_analysis_tools(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient(Map.of("SONARQUBE_ORG", "my-org", "SONARQUBE_TOOLSETS", "analysis"));
    for (var name : List.of(AnalyzeCodeSnippetTool.TOOL_NAME, RunAdvancedCodeAnalysisTool.TOOL_NAME)) {
      var definition = client.listTools().stream().filter(tool -> tool.name().equals(name)).findFirst().orElseThrow();
      assertThat(definition.inputSchema().get("properties")).asInstanceOf(MAP).doesNotContainKey("organization");
      var arguments = name.equals(RunAdvancedCodeAnalysisTool.TOOL_NAME) ? selectedArguments()
        : Map.<String, Object>of("organization", "selected-org", "filePath", "src/Main.java", "language", "java");
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
    stubStartupOrganization(harness);
    stubSelectedOrganization(harness, true, false);
    var client = harness.newClient(Map.of("SONARQUBE_IS_CLOUD", "true", "SONARQUBE_TOOLSETS", "vortex"));
    assertThat(client.callTool(RunAdvancedCodeAnalysisTool.TOOL_NAME, selectedArguments()).isError()).isTrue();
    stubSelectedOrganization(harness, false, true);
    assertThat(client.callTool(RunAdvancedCodeAnalysisTool.TOOL_NAME, selectedArguments()).isError()).isTrue();
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(A3sAnalysisApi.ANALYSES_PATH)).isZero();
  }

  @SonarQubeMcpServerTest
  void it_should_keep_local_analysis_for_unentitled_org_when_startup_org_has_vortex(SonarQubeMcpServerTestHarness harness) throws IOException {
    stubStartupOrganization(harness);
    stubAdvancedAnalysisEnabled(harness);
    harness.getMockSonarQubeServer().stubFor(get(OrganizationsApi.ORGANIZATIONS_PATH + "?organizationKey=my-org&excludeEligibility=true")
      .willReturn(okJson("[{\"key\":\"my-org\",\"uuidV4\":\"00000000-0000-0000-0000-000000000001\"}]")));
    stubSelectedOrganization(harness, false, false);
    harness.getMockSonarQubeServer().stubFor(get(QualityProfilesApi.SEARCH_PATH + "?organization=selected-org&defaults=true")
      .willReturn(okJson("{\"profiles\":[]}")));
    var file = workspaceDir.resolve("src/Fallback.php");
    Files.writeString(file, "<?php echo 'hello';");
    try {
      var client = harness.withPlugins().newClient(Map.of("SONARQUBE_IS_CLOUD", "true", "SONARQUBE_TOOLSETS", "analysis"));
      assertThat(client.listTools()).anyMatch(tool -> tool.name().equals(AnalyzeCodeSnippetTool.TOOL_NAME));
      assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining(A3sAnalysisApi.A3S_ORG_CONFIG_PATH
        + "00000000-0000-0000-0000-000000000001")).isTrue();
      assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining(PluginsApi.INSTALLED_PLUGINS_PATH)).isTrue();

      assertResultEquals(client.callTool(AnalyzeCodeSnippetTool.TOOL_NAME,
        Map.of("organization", "selected-org", "filePath", "src/Fallback.php", "language", "php")),
        "{\"issues\":[],\"issueCount\":0,\"deprecationNotice\":\"%s\"}".formatted(AnalyzeCodeSnippetTool.DEPRECATION_NOTICE));
      var defaultOrg = client.callTool(AnalyzeCodeSnippetTool.TOOL_NAME, Map.of("filePath", "src/Fallback.php", "language", "php"));
      assertThat(defaultOrg.isError()).isTrue();
      assertThat(defaultOrg.toString()).contains(RunAdvancedCodeAnalysisTool.TOOL_NAME);
      assertThat(harness.getMockSonarQubeServer().countRequestsContaining(QualityProfilesApi.SEARCH_PATH)).isEqualTo(1);
    } finally {
      Files.deleteIfExists(file);
    }
  }

  @SonarQubeMcpServerTest
  void it_should_reject_local_analysis_when_selected_org_has_vortex(SonarQubeMcpServerTestHarness harness) {
    stubStartupOrganization(harness);
    stubSelectedOrganization(harness, true, true);
    stubAnalysisResponse(harness, SIMPLE_RESPONSE);
    var client = harness.newClient(Map.of("SONARQUBE_IS_CLOUD", "true", "SONARQUBE_TOOLSETS", "analysis"));

    var local = client.callTool(AnalyzeCodeSnippetTool.TOOL_NAME,
      Map.of("organization", "selected-org", "projectKey", "project", "filePath", "src/Main.java", "language", "java"));
    assertThat(local.isError()).isTrue();
    assertThat(local.toString()).contains(RunAdvancedCodeAnalysisTool.TOOL_NAME);
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(QualityProfilesApi.SEARCH_PATH)).isZero();
    assertThat(client.callTool(RunAdvancedCodeAnalysisTool.TOOL_NAME, selectedArguments()).isError()).isFalse();
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining("organizationKey=selected-org")).isEqualTo(1);
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(CagApi.CAG_ENTITLEMENT_PATH + "selected-uuid")).isEqualTo(1);
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(A3sAnalysisApi.A3S_ORG_CONFIG_PATH + "selected-uuid")).isEqualTo(1);
  }

  @SonarQubeMcpServerTest
  void it_should_retry_failed_startup_entitlement_then_share_the_result_between_analysis_tools(SonarQubeMcpServerTestHarness harness) {
    harness.stubCagEntitlementError();
    stubAnalysisResponse(harness, SIMPLE_RESPONSE);
    var client = harness.newClient(Map.of("SONARQUBE_ORG", "my-org", "SONARQUBE_TOOLSETS", "analysis"));
    stubAdvancedAnalysisEnabled(harness);

    var local = client.callTool(AnalyzeCodeSnippetTool.TOOL_NAME, Map.of("filePath", "src/Main.java", "language", "java"));
    assertThat(local.isError()).isTrue();
    assertThat(local.toString()).contains(RunAdvancedCodeAnalysisTool.TOOL_NAME);
    var advanced = client.callTool(RunAdvancedCodeAnalysisTool.TOOL_NAME,
      Map.of("projectKey", "project", "branch", "main", "filePath", "src/Main.java"));
    assertThat(advanced.isError()).isFalse();

    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(OrganizationsApi.ORGANIZATIONS_PATH)).isEqualTo(1);
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(CagApi.CAG_ENTITLEMENT_PATH)).isEqualTo(2);
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(A3sAnalysisApi.A3S_ORG_CONFIG_PATH)).isEqualTo(1);
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(QualityProfilesApi.SEARCH_PATH)).isZero();
  }

  private static void stubStartupOrganization(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(OrganizationsApi.ORGANIZATIONS_PATH))
      .withQueryParam("organizationKey", com.github.tomakehurst.wiremock.client.WireMock.absent())
      .willReturn(okJson("[{\"key\":\"my-org\",\"uuidV4\":\"00000000-0000-0000-0000-000000000001\"}]")));
    harness.stubCagEntitlement(false);
    harness.stubA3sEnabled(false);
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
