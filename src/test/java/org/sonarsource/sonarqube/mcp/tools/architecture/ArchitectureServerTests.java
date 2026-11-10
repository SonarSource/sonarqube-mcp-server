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
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiHelper;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;
import org.sonarsource.sonarqube.mcp.serverapi.exception.NotFoundException;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.textOf;

class ArchitectureServerTests {
  private static final String LOOKUP = "/api/v2/components/projects?keys=my_project&pageSize=1";
  private static final String INTENDED = "/api/v2/architecture/project-intended-architectures";
  private static final String ID = "00000000-0000-4000-8000-000000000001";
  private static final String MODEL = "[{\"type\":\"namespace\",\"language\":\"java\",\"groups\":[{\"label\":\"app\",\"patterns\":[\"app:**\"]}],\"constraints\":[]}]";
  private static final String SAVED_MODEL = "{\"id\":\"" + ID + "\",\"perspectives\":" + MODEL.replace("\"type\"", "\"qualifiers\"") + "}";
  @RegisterExtension
  final SonarQubeMcpServerTestHarness harness = new SonarQubeMcpServerTestHarness();

  private void lookup() {
    harness.getMockSonarQubeServer().stubFor(get(LOOKUP).willReturn(okJson("{\"projects\":[{\"key\":\"my_project\",\"id\":\"opaque-project-id\"}],\"page\":{\"total\":1}}")));
  }

  @Test
  void it_should_preserve_current_not_found_errors_without_directive_or_upgrade_advice() {
    var helper = mock(ServerApiHelper.class);
    var missingBranch = new NotFoundException("Analyzed branch was not found");
    when(helper.get(anyString())).thenThrow(missingBranch);

    var api = new ArchitectureApi(helper);

    assertThatThrownBy(() -> api.getStructure("my_project", "", "branch-id", "java"))
      .isSameAs(missingBranch);
  }

  @Test
  void it_should_keep_the_original_error_when_explaining_unavailable_project_routes() {
    var helper = mock(ServerApiHelper.class);
    when(helper.get(anyString())).thenThrow(new NotFoundException("Project model is unavailable"));

    var api = new ArchitectureApi(helper);

    assertThatThrownBy(() -> api.getIntended("my_project", "project-id"))
      .isInstanceOf(NotFoundException.class)
      .hasMessageContaining("Project model is unavailable")
      .hasMessageContaining("public Architecture endpoint availability");
  }

  @Test
  void it_should_use_the_public_project_id_for_intended_writes_and_require_a_revision_for_full_clear() {
    lookup();
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(get(INTENDED + "?projectId=opaque-project-id").inScenario("model").whenScenarioStateIs("Started").willReturn(okJson("{\"intendedArchitectures\":[]}")));
    var client = harness.newClient(Map.of());
    var args = new java.util.HashMap<String, Object>(Map.of("target", "intended", "projectKey", "my_project", "model", "{\"models\":" + MODEL + "}", "dryRun", true));
    var preview = client.callTool(ArchitectureSetTool.TOOL_NAME, args);
    assertThat(preview.isError()).isFalse();
    assertThat(textOf(preview)).doesNotContain("pattern and physical hierarchy checks unavailable");
    assertThat(backend.countRequestsContaining("project_branches")).isZero();
    assertThat(backend.getReceivedBodies("POST", INTENDED)).isEmpty();
    backend.stubFor(post(INTENDED).inScenario("model").willSetStateTo("created").willReturn(aResponse().withStatus(201).withBody(SAVED_MODEL)));
    backend.stubFor(get(INTENDED + "?projectId=opaque-project-id").inScenario("model").whenScenarioStateIs("created").willReturn(okJson("{\"intendedArchitectures\":[" + SAVED_MODEL + "]}")));
    args.put("dryRun", false);
    var created = client.callTool(ArchitectureSetTool.TOOL_NAME, args);
    assertThat(created.isError()).isFalse();
    assertThat(textOf(created)).doesNotContain("pattern and physical hierarchy checks unavailable");
    assertThat(backend.countRequestsContaining("project_branches")).isZero();
    assertThat(backend.getReceivedBodies("POST", INTENDED)).singleElement().asString().contains("opaque-project-id");
    backend.stubFor(patch(INTENDED + "/" + ID).inScenario("model").willSetStateTo("updated").willReturn(okJson(SAVED_MODEL.replace("app:**", "app:new:**"))));
    backend.stubFor(get(INTENDED + "?projectId=opaque-project-id").inScenario("model").whenScenarioStateIs("updated").willReturn(okJson("{\"intendedArchitectures\":[" + SAVED_MODEL.replace("app:**", "app:new:**") + "]}")));
    args.put("model", "{\"models\":" + MODEL.replace("app:**", "app:new:**") + "}");
    var updated = client.callTool(ArchitectureSetTool.TOOL_NAME, args);
    assertThat(updated.isError()).isFalse();
    assertThat(textOf(updated)).doesNotContain("pattern and physical hierarchy checks unavailable");
    assertThat(backend.countRequestsContaining("project_branches")).isZero();
    args.put("model", "{\"models\":[]}");
    var clear = client.callTool(ArchitectureSetTool.TOOL_NAME, args);
    assertThat(clear.isError()).isTrue();
    assertThat(textOf(clear)).contains("Clearing the whole intended architecture needs the revision");
    assertThat(backend.getReceivedBodies("DELETE", INTENDED + "/" + ID)).isEmpty();
    assertThat(backend.countRequestsContaining("project_branches")).isZero();
    assertThat(backend.getReceivedBodies("PATCH", INTENDED + "/" + ID)).singleElement().asString().contains("app:new:**");
  }

  @Test
  void it_should_restrict_server_schema_and_execution_to_intended_only() {
    var backend = harness.getMockSonarQubeServer();
    var client = harness.newClient(Map.of());
    var readSchema = client.listTools().stream().filter(tool -> ArchitectureGetTool.TOOL_NAME.equals(tool.name())).findFirst().orElseThrow().inputSchema();
    assertThat(readSchema.toString()).contains("intended: model")
      .doesNotContain("directives", "findings", "Current keys:", "default_perspective", "namespace_graph", "file_graph", "directiveId", "pageIndex", "pageSize", "category");
    var writeSchema = client.listTools().stream().filter(tool -> ArchitectureSetTool.TOOL_NAME.equals(tool.name())).findFirst().orElseThrow().inputSchema();
    assertThat(writeSchema.toString()).contains("intended: replace model")
      .doesNotContain("directives", "REMOVE_EDGE", "RENAME_NODE", "MOVE_NODE");
    for (var target : new String[] {"current", "directives", "findings"}) {
      var read = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", target, "projectKey", "my_project"));
      assertThat(read.isError()).isTrue();
      assertThat(textOf(read)).contains("intended");
      var write = client.callTool(ArchitectureSetTool.TOOL_NAME, Map.of("target", target, "projectKey", "my_project", "model", "{}", "dryRun", true));
      assertThat(write.isError()).isTrue();
      assertThat(textOf(write)).contains("intended");
    }
    assertThat(backend.hasReceivedRequestContaining("/api/v2/architecture/")).isFalse();
    assertThat(backend.hasReceivedRequestContaining("/api/v2/components/projects")).isFalse();
    assertThat(backend.hasReceivedRequestContaining("project_branches")).isFalse();
  }

  @Test
  void it_should_encode_the_key_in_the_public_lookup() {
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(get("/api/v2/components/projects?keys=my%3Aproject&pageSize=1")
      .willReturn(okJson("{\"projects\":[{\"id\":\"opaque\",\"key\":\"my:project\"}],\"page\":{\"total\":1}}")));
    backend.stubFor(get(INTENDED + "?projectId=opaque").willReturn(okJson("{\"intendedArchitectures\":[]}")));
    assertThat(harness.newClient(Map.of()).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "intended", "projectKey", "my:project")).isError()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(ints = {401, 403, 404, 429, 500})
  void it_should_not_mutate_when_lookup_fails(int status) {
    harness.getMockSonarQubeServer().stubFor(get(LOOKUP).willReturn(aResponse().withStatus(status)));
    var result = harness.newClient(Map.of()).callTool(ArchitectureSetTool.TOOL_NAME, Map.of("target", "intended", "projectKey", "my_project", "model", "{\"models\":" + MODEL + "}", "dryRun", false));
    assertThat(result.isError()).isTrue();
    if (status == 404) { assertThat(textOf(result)).contains("public project lookup", "Server build"); }
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/api/v2/architecture/")).isFalse();
  }

  @Test
  void it_should_explain_unavailable_public_architecture_routes_on_server() {
    lookup();
    var path = INTENDED + "?projectId=opaque-project-id";
    harness.getMockSonarQubeServer().stubFor(get(path).willReturn(aResponse().withStatus(404)));
    var result = harness.newClient(Map.of()).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "intended", "projectKey", "my_project"));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("404", "public Architecture endpoint availability", "Browse permission", "gateway routing")
      .doesNotContain("..", "directive id", "older Server", "Update the MCP server");
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "null", "{\"projects\":[],\"page\":{\"total\":0}}", "{\"projects\":[{\"id\":\"x\",\"key\":\"wrong\"}],\"page\":{\"total\":1}}", "{\"projects\":[{\"id\":12,\"key\":\"my_project\"}],\"page\":{\"total\":1}}", "{\"projects\":[{\"id\":\"\",\"key\":\"my_project\"}],\"page\":{\"total\":1}}", "{\"projects\":[{\"id\":\"x\",\"key\":\"my_project\"}],\"page\":{\"total\":2}}"})
  void it_should_refuse_invalid_project_responses_before_architecture_requests(String body) {
    harness.getMockSonarQubeServer().stubFor(get(LOOKUP).willReturn(okJson(body)));
    var result = harness.newClient(Map.of()).callTool(ArchitectureSetTool.TOOL_NAME, Map.of("target", "intended", "projectKey", "my_project", "model", "{\"models\":" + MODEL + "}", "dryRun", false));
    assertThat(result.isError()).isTrue();
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/api/v2/architecture/")).isFalse();
  }
}
