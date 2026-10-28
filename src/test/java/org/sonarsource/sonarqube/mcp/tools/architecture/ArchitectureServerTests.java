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
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
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
  private static final String DIRECTIVES = "/api/v2/architecture/project-directives";
  private static final String INTENDED = "/api/v2/architecture/intended-architectures";
  private static final String ID = "00000000-0000-4000-8000-000000000001";
  private static final String INPUT = "{\"type\":\"REMOVE_EDGE\",\"ecosystem\":\"java\",\"graphType\":\"namespace_graph\",\"fromNodeKey\":\"app:a\",\"toNodeKey\":\"app:b\"}";
  private static final String MODEL = "[{\"type\":\"namespace\",\"language\":\"java\",\"groups\":[{\"label\":\"app\",\"patterns\":[\"app:**\"]}],\"constraints\":[]}]";
  private static final String SAVED_MODEL = "{\"id\":\"" + ID + "\",\"intendedArchitectures\":" + MODEL.replace("\"type\"", "\"qualifiers\"") + "}";
  @RegisterExtension
  final SonarQubeMcpServerTestHarness harness = new SonarQubeMcpServerTestHarness();

  private void lookup() {
    harness.getMockSonarQubeServer().stubFor(get(LOOKUP).willReturn(okJson("{\"projects\":[{\"key\":\"my_project\",\"id\":\"opaque-project-id\"}],\"page\":{\"total\":1}}")));
  }

  private String saved() {
    return INPUT.substring(0, INPUT.length() - 1) + ",\"id\":\"" + ID + "\",\"projectId\":\"opaque-project-id\","
      + "\"origin\":\"sonarqube-mcp-server\",\"graphPerspective\":\"default_perspective\"}";
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
      .hasMessageContaining("public Architecture APIs");
  }

  @Test
  void it_should_use_the_public_project_id_for_intended_preview_create_update_and_refuse_empty_replacement() {
    lookup();
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(get(INTENDED + "?projectId=opaque-project-id").inScenario("model").whenScenarioStateIs("Started").willReturn(okJson("{\"id\":null,\"intendedArchitectures\":[]}")));
    var client = harness.newClient(Map.of());
    var args = new java.util.HashMap<String, Object>(Map.of("target", "intended", "projectKey", "my_project", "model", "{\"models\":" + MODEL + "}", "dryRun", true));
    assertThat(client.callTool(ArchitectureSetTool.TOOL_NAME, args).isError()).isFalse();
    assertThat(backend.getReceivedBodies("POST", INTENDED)).isEmpty();
    backend.stubFor(post(INTENDED).inScenario("model").willSetStateTo("created").willReturn(aResponse().withStatus(201).withBody(SAVED_MODEL)));
    backend.stubFor(get(INTENDED + "?projectId=opaque-project-id").inScenario("model").whenScenarioStateIs("created").willReturn(okJson(SAVED_MODEL)));
    args.put("dryRun", false);
    assertThat(client.callTool(ArchitectureSetTool.TOOL_NAME, args).isError()).isFalse();
    assertThat(backend.getReceivedBodies("POST", INTENDED)).singleElement().asString().contains("opaque-project-id");
    backend.stubFor(patch(INTENDED + "/" + ID).inScenario("model").willSetStateTo("updated").willReturn(okJson(SAVED_MODEL.replace("app:**", "app:new:**"))));
    backend.stubFor(get(INTENDED + "?projectId=opaque-project-id").inScenario("model").whenScenarioStateIs("updated").willReturn(okJson(SAVED_MODEL.replace("app:**", "app:new:**"))));
    args.put("model", "{\"models\":" + MODEL.replace("app:**", "app:new:**") + "}");
    assertThat(client.callTool(ArchitectureSetTool.TOOL_NAME, args).isError()).isFalse();
    args.put("model", "{\"models\":[]}");
    var clear = client.callTool(ArchitectureSetTool.TOOL_NAME, args);
    assertThat(clear.isError()).isTrue();
    assertThat(backend.getReceivedBodies("DELETE", INTENDED + "/" + ID)).isEmpty();
    assertThat(backend.hasReceivedRequestContaining("project_branches")).isFalse();
  }

  @Test
  void it_should_read_preview_and_apply_directive_changes_on_server() {
    lookup();
    var backend = harness.getMockSonarQubeServer();
    var page = DIRECTIVES + "?projectId=opaque-project-id&pageIndex=1&pageSize=100";
    backend.stubFor(get(page).willReturn(okJson("{\"projectDirectives\":[],\"page\":{\"pageIndex\":1,\"pageSize\":100,\"total\":0}}")));
    var client = harness.newClient(Map.of());
    var schema = client.listTools().stream().filter(tool -> ArchitectureGetTool.TOOL_NAME.equals(tool.name())).findFirst().orElseThrow().inputSchema();
    assertThat(schema.toString()).contains("directiveId/pageIndex/pageSize apply only to directives", "default_perspective", "namespace_graph", "file_graph");
    var args = new java.util.HashMap<String, Object>(Map.of("target", "directives", "projectKey", "my_project", "model", "{\"create\":[" + INPUT + "]}", "dryRun", true));
    assertThat(client.callTool(ArchitectureSetTool.TOOL_NAME, args).isError()).isFalse();
    assertThat(backend.getReceivedBodies("POST", DIRECTIVES)).isEmpty();
    backend.stubFor(post(DIRECTIVES).willReturn(aResponse().withStatus(201).withBody(saved())));
    args.put("dryRun", false);
    assertThat(client.callTool(ArchitectureSetTool.TOOL_NAME, args).isError()).isFalse();
    assertThat(backend.getReceivedBodies("POST", DIRECTIVES)).singleElement().asString().contains("sonarqube-mcp-server", "default_perspective");
    backend.stubFor(get(page).willReturn(okJson("{\"projectDirectives\":[" + saved() + "],\"page\":{\"pageIndex\":1,\"pageSize\":100,\"total\":1}}")));
    var duplicate = client.callTool(ArchitectureSetTool.TOOL_NAME, args);
    assertThat(duplicate.isError()).isFalse();
    assertThat(textOf(duplicate)).contains("skippedIds", ID);
    assertThat(backend.getReceivedBodies("POST", DIRECTIVES)).hasSize(1);
    backend.stubFor(get(DIRECTIVES + "/" + ID).willReturn(okJson(saved())));
    var read = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "directives", "projectKey", "my_project", "directiveId", ID));
    assertThat(read.isError()).isFalse();
    assertThat(textOf(read)).contains("sonarqube-mcp-server");
    backend.stubFor(patch(DIRECTIVES + "/" + ID).willReturn(okJson(saved().replace("}", ",\"description\":\"Changed\"}"))));
    args.put("model", "{\"update\":[{\"id\":\"" + ID + "\",\"description\":\"Changed\"}]}");
    assertThat(client.callTool(ArchitectureSetTool.TOOL_NAME, args).isError()).isFalse();
    assertThat(backend.getReceivedBodies("PATCH", DIRECTIVES + "/" + ID)).singleElement().asString()
      .contains("app:a", "app:b", "Changed", "sonarqube-mcp-server").doesNotContain("graphPerspective");
    backend.stubFor(delete(DIRECTIVES + "/" + ID).willReturn(aResponse().withStatus(204)));
    args.put("model", "{\"delete\":[{\"id\":\"" + ID + "\"}]}");
    assertThat(client.callTool(ArchitectureSetTool.TOOL_NAME, args).isError()).isFalse();
    assertThat(backend.hasReceivedRequestContaining("project_branches")).isFalse();
  }

  @Test
  void it_should_stop_after_partial_server_changes_without_replaying() {
    lookup();
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(get(DIRECTIVES + "?projectId=opaque-project-id&pageIndex=1&pageSize=100")
      .willReturn(okJson("{\"projectDirectives\":[" + saved() + "],\"page\":{\"pageIndex\":1,\"pageSize\":100,\"total\":1}}")));
    backend.stubFor(get(DIRECTIVES + "/" + ID).willReturn(okJson(saved())));
    var createdId = "00000000-0000-4000-8000-000000000002";
    backend.stubFor(post(DIRECTIVES).willReturn(aResponse().withStatus(201).withBody(saved().replace(ID, createdId).replace("app:a", "app:new"))));
    backend.stubFor(patch(DIRECTIVES + "/" + ID).willReturn(aResponse().withStatus(403).withBody("{\"message\":\"update rejected\"}")));
    var changes = "{\"create\":[" + INPUT.replace("app:a", "app:new") + "],\"update\":[{\"id\":\"" + ID + "\",\"description\":\"Changed\"}]}";
    var result = harness.newClient(Map.of()).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "model", changes, "dryRun", false));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains(createdId);
    assertThat(backend.getReceivedBodies("POST", DIRECTIVES)).hasSize(1);
    assertThat(backend.getReceivedBodies("PATCH", DIRECTIVES + "/" + ID)).hasSize(1);
    assertThat(backend.getReceivedBodies("DELETE", DIRECTIVES + "/" + ID)).isEmpty();
  }

  @Test
  void it_should_encode_the_key_in_the_public_lookup() {
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(get("/api/v2/components/projects?keys=my%3Aproject&pageSize=1")
      .willReturn(okJson("{\"projects\":[{\"id\":\"opaque\",\"key\":\"my:project\"}],\"page\":{\"total\":1}}")));
    backend.stubFor(get(INTENDED + "?projectId=opaque").willReturn(okJson("{\"id\":null,\"intendedArchitectures\":[]}")));
    assertThat(harness.newClient(Map.of()).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "intended", "projectKey", "my:project")).isError()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(ints = {401, 403, 404, 429, 500})
  void it_should_not_mutate_when_lookup_fails(int status) {
    harness.getMockSonarQubeServer().stubFor(get(LOOKUP).willReturn(aResponse().withStatus(status)));
    var result = harness.newClient(Map.of()).callTool(ArchitectureSetTool.TOOL_NAME, Map.of("target", "directives", "projectKey", "my_project", "model", "{\"create\":[" + INPUT + "]}", "dryRun", false));
    assertThat(result.isError()).isTrue();
    if (status == 404) { assertThat(textOf(result)).contains("public project lookup", "Server build"); }
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/api/v2/architecture/")).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"intended", "directives"})
  void it_should_explain_unavailable_public_architecture_routes_on_server(String target) {
    lookup();
    var path = target.equals("intended") ? INTENDED + "?projectId=opaque-project-id"
      : DIRECTIVES + "?projectId=opaque-project-id&pageIndex=1&pageSize=50";
    harness.getMockSonarQubeServer().stubFor(get(path).willReturn(aResponse().withStatus(404)));
    var result = harness.newClient(Map.of()).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", target, "projectKey", "my_project"));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("older Server versions", "public Architecture APIs", "Browse permission");
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
