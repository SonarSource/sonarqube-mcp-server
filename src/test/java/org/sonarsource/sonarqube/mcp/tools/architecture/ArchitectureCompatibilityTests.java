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

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.CLOUD_ENV;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.INTENDED_JAVA_MODEL;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.intendedArchitectures;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.stubMainBranch;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.textOf;

class ArchitectureCompatibilityTests {
  private static final String UPGRADE_RECOMMENDATION = "Update the MCP server";
  private static final String API_RESPONSE_ERROR = "The Architecture API returned an unexpected response.";

  @RegisterExtension
  final SonarQubeMcpServerTestHarness harness = new SonarQubeMcpServerTestHarness();

  private void project() {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get("/api/v2/components/projects?keys=my_project&pageSize=1")
      .willReturn(okJson("{\"projects\":[{\"key\":\"my_project\",\"id\":\"opaque-project-id\"}],\"page\":{\"total\":1}}")));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true})
  void it_should_explain_incompatible_response_envelopes_without_writing(boolean cloud) {
    project();
    var client = harness.newClient(cloud ? CLOUD_ENV : Map.of());
    var paths = cloud
      ? Map.of("current", ArchitectureApi.CURRENT_ARCHITECTURES_PATH, "intended", ArchitectureApi.INTENDED_ARCHITECTURES_PATH,
        "directives", ArchitectureApi.PROJECT_DIRECTIVES_PATH, "findings", ArchitectureApi.PROJECT_FINDINGS_PATH)
      : Map.of("intended", ArchitectureApi.INTENDED_ARCHITECTURES_PATH);
    for (var target : paths.keySet()) {
      harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo((cloud ? "" : ArchitectureApi.SERVER_API_PREFIX) + paths.get(target)))
        .willReturn(okJson("{}")));
      var arguments = new java.util.HashMap<String, Object>(Map.of("target", target, "projectKey", "my_project"));
      if (!target.equals("directives")) {
        arguments.put("language", "java");
      }
      var result = client.callTool(ArchitectureGetTool.TOOL_NAME, arguments);
      assertThat(result.isError()).isTrue();
      assertThat(textOf(result)).contains(API_RESPONSE_ERROR).doesNotContain(UPGRADE_RECOMMENDATION, "compatible API versions");
    }
    for (var method : new String[] {"POST", "PATCH", "DELETE"}) {
      assertThat(harness.getMockSonarQubeServer().getReceivedBodies(method, "/architecture/")).isEmpty();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true})
  void it_should_explain_malformed_response_fields_without_relabeling_page_value_mismatches(boolean cloud) {
    project();
    var client = harness.newClient(cloud ? CLOUD_ENV : Map.of());
    var cases = cloud ? new String[][] {
      {"current", ArchitectureApi.CURRENT_ARCHITECTURES_PATH, "{\"currentArchitectures\":\"x\"}"},
      {"current", ArchitectureApi.CURRENT_ARCHITECTURES_PATH, "{\"currentArchitectures\":[null]}"},
      {"intended", ArchitectureApi.INTENDED_ARCHITECTURES_PATH, "not JSON"},
      {"directives", ArchitectureApi.PROJECT_DIRECTIVES_PATH, "{\"projectDirectives\":[],\"page\":{}}"},
      {"directives", ArchitectureApi.PROJECT_DIRECTIVES_PATH, "{\"projectDirectives\":[{}],\"page\":{\"pageIndex\":1,\"pageSize\":50,\"total\":1}}"},
      {"findings", ArchitectureApi.PROJECT_FINDINGS_PATH, "{\"findings\":[],\"page\":{}}"},
      {"findings", ArchitectureApi.PROJECT_FINDINGS_PATH, "{\"findings\":[{}],\"page\":{\"pageIndex\":1,\"pageSize\":50,\"total\":1}}"}
    } : new String[][] {
      {"intended", ArchitectureApi.INTENDED_ARCHITECTURES_PATH, "not JSON"}
    };
    for (var fixture : cases) {
      harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo((cloud ? "" : ArchitectureApi.SERVER_API_PREFIX) + fixture[1]))
        .willReturn(okJson(fixture[2])));
      var arguments = new java.util.HashMap<String, Object>(Map.of("target", fixture[0], "projectKey", "my_project"));
      if (!fixture[0].equals("directives")) {
        arguments.put("language", "java");
      }
      var result = client.callTool(ArchitectureGetTool.TOOL_NAME, arguments);
      assertThat(result.isError()).isTrue();
      assertThat(textOf(result)).as(fixture[2]).contains(API_RESPONSE_ERROR).doesNotContain(UPGRADE_RECOMMENDATION, "compatible API versions");
    }
    if (cloud) {
      harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(ArchitectureApi.PROJECT_DIRECTIVES_PATH))
        .willReturn(okJson("{\"projectDirectives\":[],\"page\":{\"pageIndex\":2,\"pageSize\":50,\"total\":0}}")));
      var mismatched = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "directives", "projectKey", "my_project"));
      assertThat(mismatched.isError()).isTrue();
      assertThat(textOf(mismatched)).doesNotContain(API_RESPONSE_ERROR);
    }
  }

  @Test
  void it_should_report_a_null_directive_detail_as_an_api_error() {
    project();
    var id = "11111111-1111-4111-8111-111111111111";
    var path = ArchitectureApi.PROJECT_DIRECTIVES_PATH + "/" + id;
    harness.getMockSonarQubeServer().stubFor(get(path).willReturn(okJson("null")));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "directiveId", id));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("Invalid public architecture response", API_RESPONSE_ERROR)
      .doesNotContain(UPGRADE_RECOMMENDATION, "supports the same", "Check compatible API versions");
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining(path)).isEqualTo(1);
  }

  @ParameterizedTest
  @CsvSource({"true,false,false", "true,true,false", "true,false,true", "true,true,true"})
  void it_should_reconcile_malformed_write_responses_without_replay(boolean cloud, boolean patchWrite, boolean committed) {
    project();
    var backend = harness.getMockSonarQubeServer();
    var path = (cloud ? "" : ArchitectureApi.SERVER_API_PREFIX) + ArchitectureApi.INTENDED_ARCHITECTURES_PATH;
    var next = INTENDED_JAVA_MODEL.replace("domain", "infra");
    var previous = patchWrite ? intendedArchitectures(INTENDED_JAVA_MODEL) : "{\"intendedArchitectures\":[]}";
    backend.stubFor(get(urlPathEqualTo(path)).inScenario("malformed-write").whenScenarioStateIs("Started").willReturn(okJson(previous)));
    var writePath = patchWrite ? path + "/11111111-1111-4111-8111-111111111111" : path;
    var write = patchWrite ? patch(writePath) : post(writePath);
    backend.stubFor(write.inScenario("malformed-write").willSetStateTo("after-write").willReturn(okJson("not JSON")));
    backend.stubFor(get(urlPathEqualTo(path)).inScenario("malformed-write").whenScenarioStateIs("after-write")
      .willReturn(okJson(committed ? intendedArchitectures(next) : previous)));
    var result = harness.newClient(cloud ? CLOUD_ENV : Map.of()).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "intended", "projectKey", "my_project", "model", next, "dryRun", false));
    assertThat(result.isError()).as(textOf(result)).isEqualTo(!committed);
    if (committed) {
      assertThat(textOf(result)).contains(patchWrite ? "replaced" : "created").doesNotContain("Previous exported model");
    } else {
      assertThat(textOf(result)).contains("could not be confirmed", "No automatic rollback", "Previous exported model", "Invalid public architecture response", API_RESPONSE_ERROR)
        .doesNotContain(UPGRADE_RECOMMENDATION, "supports the same", "Check compatible API versions");
    }
    assertThat(backend.getReceivedBodies(patchWrite ? "PATCH" : "POST", writePath)).hasSize(1);
    assertThat(backend.getReceivedBodies(patchWrite ? "POST" : "PATCH", path)).isEmpty();
    assertThat(backend.getReceivedBodies("DELETE", path)).isEmpty();
    assertThat(backend.countRequestsContaining(path)).isEqualTo(4);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true})
  void it_should_explain_gateway_endpoint_refusals_without_inferring_an_upgrade(boolean cloud) {
    project();
    var backend = harness.getMockSonarQubeServer();
    var path = (cloud ? "" : ArchitectureApi.SERVER_API_PREFIX) + (cloud ? ArchitectureApi.PROJECT_FINDINGS_PATH : ArchitectureApi.INTENDED_ARCHITECTURES_PATH);
    var client = harness.newClient(cloud ? CLOUD_ENV : Map.of());
    var arguments = Map.<String, Object>of("target", cloud ? "findings" : "intended", "projectKey", "my_project");
    backend.stubFor(get(urlPathEqualTo(path)).willReturn(aResponse().withStatus(403)
      .withBody("{\"message\":\"Invalid key=value pair (missing equal-sign) in Authorization header\"}")));
    var gateway = client.callTool(ArchitectureGetTool.TOOL_NAME, arguments);
    assertThat(gateway.isError()).isTrue();
    assertThat(textOf(gateway)).contains("Invalid key=value pair", "include it. This gateway response can also mean the Architecture endpoint is unavailable",
      "check endpoint availability and gateway routing")
      .doesNotContain(UPGRADE_RECOMMENDATION, "version mismatch", "..");
    backend.stubFor(get(urlPathEqualTo(path)).willReturn(aResponse().withStatus(403).withBody("{\"message\":\"Insufficient permissions\"}")));
    var denied = client.callTool(ArchitectureGetTool.TOOL_NAME, arguments);
    assertThat(denied.isError()).isTrue();
    assertThat(textOf(denied)).contains("Insufficient permissions", "Check project permissions")
      .doesNotContain("can also mean the Architecture endpoint is unavailable", "Update the MCP server", "version mismatch");
    assertThat(backend.countRequestsContaining(path)).isEqualTo(2);
    for (var method : new String[] {"POST", "PATCH", "DELETE"}) {
      assertThat(backend.getReceivedBodies(method, "/architecture/")).isEmpty();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true})
  void it_should_not_relabel_http_errors_or_empty_results_as_version_incompatibility(boolean cloud) {
    project();
    var client = harness.newClient(cloud ? CLOUD_ENV : Map.of());
    var path = (cloud ? "" : ArchitectureApi.SERVER_API_PREFIX) + (cloud ? ArchitectureApi.CURRENT_ARCHITECTURES_PATH : ArchitectureApi.INTENDED_ARCHITECTURES_PATH);
    var target = cloud ? "current" : "intended";
    var arguments = Map.<String, Object>of("target", target, "projectKey", "my_project", "language", "java");
    for (var status : new int[] {401, 403, 404, 500}) {
      harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(path)).willReturn(aResponse().withStatus(status).withBody("{\"message\":\"Request refused\"}")));
      var result = client.callTool(ArchitectureGetTool.TOOL_NAME, arguments);
      assertThat(result.isError()).isTrue();
      assertThat(textOf(result)).doesNotContain(API_RESPONSE_ERROR);
    }
    var before = harness.getMockSonarQubeServer().countRequestsContaining("/architecture/");
    var invalid = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", target, "projectKey", "my_project", "depth", 0));
    assertThat(invalid.isError()).isTrue();
    assertThat(textOf(invalid)).doesNotContain(API_RESPONSE_ERROR);
    assertThat(harness.getMockSonarQubeServer().countRequestsContaining("/architecture/")).isEqualTo(before);
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo(path)).willReturn(okJson(cloud ? "{\"currentArchitectures\":[]}" : "{\"intendedArchitectures\":[]}")));
    var empty = client.callTool(ArchitectureGetTool.TOOL_NAME, arguments);
    assertThat(empty.isError()).isFalse();
    assertThat(textOf(empty)).doesNotContain(API_RESPONSE_ERROR);
  }
}
