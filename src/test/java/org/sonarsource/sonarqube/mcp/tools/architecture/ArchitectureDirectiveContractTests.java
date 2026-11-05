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

import com.google.gson.JsonParser;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpTestClient.assertResultEquals;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.CLOUD_ENV;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.MAIN_BRANCH_ID;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.stubMainBranch;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.textOf;

class ArchitectureDirectiveContractTests {
  private static final String PATH = ArchitectureApi.PROJECT_DIRECTIVES_PATH;
  private static final String FIRST = "00000000-0000-4000-8000-000000000001";
  private static final String SECOND = "00000000-0000-4000-8000-000000000002";
  private static final String THIRD = "00000000-0000-4000-8000-000000000003";
  private static final String INPUT = """
    {"type":"REMOVE_EDGE","ecosystem":"java","graphType":"namespace_graph","fromNodeKey":"app:a","toNodeKey":"app:b"}
    """.trim();
  private static final String WARNINGS = """
    ["Concurrent edits can race the reads; the public API has no conditional writes. Changes are applied individually without automatic retry or rollback."]
    """.trim();

  @RegisterExtension
  final SonarQubeMcpServerTestHarness harness = new SonarQubeMcpServerTestHarness();

  @Test
  @SuppressWarnings("unchecked")
  void it_should_advertise_the_complete_cloud_read_contract() {
    var read = cloudTool(ArchitectureGetTool.TOOL_NAME);
    var readProperties = (Map<String, Map<String, Object>>) read.inputSchema().get("properties");
    assertThat(readProperties).containsOnlyKeys("organization", "target", "projectKey", "component", "depth", "language", "directiveId", "pageIndex", "pageSize", "type", "category");
    assertThat(read.inputSchema()).containsEntry("required", List.of("target", "projectKey")).containsEntry("additionalProperties", false);
    assertThat(readProperties.get("target")).containsEntry("enum", List.of("current", "intended", "directives", "findings"));
    assertThat(readProperties.get("language")).containsEntry("enum", List.of("java", "cs", "js", "ts", "py"));
    for (var name : List.of("organization", "target", "projectKey", "component", "language", "directiveId", "type", "category")) {
      assertThat(readProperties.get(name)).containsEntry("type", "string");
    }
    for (var name : List.of("depth", "pageIndex", "pageSize")) {
      assertThat(readProperties.get(name)).containsEntry("type", "number");
    }
    assertThat(readProperties.get("pageSize").get("description").toString()).contains("default and maximum 50");
    assertThat(read.annotations().readOnlyHint()).isTrue();
    assertThat(read.annotations().openWorldHint()).isTrue();
    assertThat(read.outputSchema()).isNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  void it_should_advertise_the_complete_cloud_write_contract() {
    var write = cloudTool(ArchitectureSetTool.TOOL_NAME);
    var writeProperties = (Map<String, Map<String, Object>>) write.inputSchema().get("properties");
    assertThat(writeProperties).containsOnlyKeys("organization", "projectKey", "target", "model", "dryRun", "revision");
    assertThat(write.inputSchema()).containsEntry("required", List.of("projectKey", "target", "model", "dryRun")).containsEntry("additionalProperties", false);
    assertThat(writeProperties.get("target")).containsEntry("enum", List.of("intended", "directives"));
    for (var name : List.of("organization", "projectKey", "target", "model", "revision")) {
      assertThat(writeProperties.get(name)).containsEntry("type", "string");
    }
    assertThat(writeProperties.get("dryRun")).containsEntry("type", "boolean");
    assertThat(writeProperties.get("model").get("description").toString()).contains(
      "fromNodeKey/toNodeKey from architecture_get current",
      "toNodeKey for MOVE_NODE: existing destination container; for RENAME_NODE: new complete key");
    assertThat(write.annotations().readOnlyHint()).isFalse();
    assertThat(write.annotations().destructiveHint()).isTrue();
    assertThat(write.annotations().openWorldHint()).isTrue();
    assertThat(write.outputSchema()).isNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  void it_should_advertise_the_complete_server_read_and_write_contract() {
    var serverTools = harness.newClient().listTools();
    assertThat(serverTools).extracting(Tool::name).contains(ArchitectureSetTool.TOOL_NAME);
    var serverRead = serverTools.stream().filter(tool -> tool.name().equals(ArchitectureGetTool.TOOL_NAME)).findFirst().orElseThrow();
    var serverProperties = (Map<String, Map<String, Object>>) serverRead.inputSchema().get("properties");
    assertThat(serverProperties).containsOnlyKeys("target", "projectKey", "component", "depth", "language", "directiveId", "pageIndex", "pageSize", "type", "category");
    assertThat(serverProperties.get("target")).containsEntry("enum", List.of("current", "intended", "directives", "findings"));
    assertThat(serverRead.inputSchema()).containsEntry("required", List.of("target", "projectKey")).containsEntry("additionalProperties", false);
    assertThat(serverRead.annotations().readOnlyHint()).isTrue();
    assertThat(serverRead.annotations().openWorldHint()).isTrue();
    assertThat(serverRead.outputSchema()).isNull();
  }

  @Test
  void it_should_return_exact_summary_detail_and_empty_page_responses() {
    stubMainBranch(harness);
    var stored = stored(FIRST, "app:a", "default_perspective", "Stored description");
    stubPage(1, 50, stored, 1);
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(get(PATH + "/" + FIRST).willReturn(okJson(stored)));
    var client = harness.newClient(CLOUD_ENV);
    var page = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "directives", "projectKey", "my_project"));
    assertThat(page.isError()).isFalse();
    assertResultEquals(page, """
      {"projectKey":"my_project","view":"summary","directives":[{
        "id":"%s","type":"REMOVE_EDGE","ecosystem":"java","graphType":"namespace_graph",
        "fromNodeKey":"app:a","toNodeKey":"app:b","graphPerspective":"default_perspective",
        "origin":"existing-client","descriptionOmitted":true}],"page":{"pageIndex":1,"pageSize":50,"total":1}}
      """.formatted(FIRST));
    var detail = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "directives", "projectKey", "my_project", "directiveId", FIRST));
    assertThat(detail.isError()).isFalse();
    assertResultEquals(detail, "{\"projectKey\":\"my_project\",\"directive\":" + stored + "}");
    stubPage(1, 50, "", 0);
    var empty = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "directives", "projectKey", "my_project"));
    assertThat(empty.isError()).isFalse();
    assertResultEquals(empty, """
      {"projectKey":"my_project","view":"summary","directives":[],"page":{"pageIndex":1,"pageSize":50,"total":0},
      "message":"No directives are visible. Check project access and whether any directives are defined."}
      """);
  }

  @Test
  void it_should_return_exact_mixed_preview_and_confirmation_responses() {
    stubMainBranch(harness);
    var before = stored(FIRST, "app:a", "default_perspective", "Before");
    var removed = stored(SECOND, "app:obsolete", "default_perspective", "Unchanged stored description");
    stubPage(1, 100, before + "," + removed, 2);
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(get(PATH + "/" + FIRST).willReturn(okJson(before)));
    backend.stubFor(get(PATH + "/" + SECOND).willReturn(okJson(removed)));
    backend.stubFor(post(PATH).willReturn(aResponse().withStatus(201).withBody(stored(THIRD, "app:new", "default_perspective", null))));
    backend.stubFor(patch(PATH + "/" + FIRST).willReturn(okJson(before.replace("Before", "After"))));
    backend.stubFor(delete(PATH + "/" + SECOND).willReturn(aResponse().withStatus(204)));
    var create = INPUT.replace("app:a", "app:new");
    var model = "{\"create\":[" + create + "," + create + "],\"update\":[{\"id\":\"" + FIRST + "\",\"description\":\"After\"}],\"delete\":[{\"id\":\"" + SECOND + "\"}]}";
    var client = harness.newClient(CLOUD_ENV);
    var preview = client.callTool(ArchitectureSetTool.TOOL_NAME, Map.of("target", "directives", "projectKey", "my_project", "model", model, "dryRun", true));
    assertThat(preview.isError()).isFalse();
    assertResultEquals(preview, """
      {"projectKey":"my_project","mode":"dry-run","toCreate":[{
        "type":"REMOVE_EDGE","ecosystem":"java","graphType":"namespace_graph","fromNodeKey":"app:new","toNodeKey":"app:b",
        "graphPerspective":"default_perspective","origin":"sonarqube-mcp-server"}],
        "changes":[{"id":"%s","before":{"description":"Before"},"after":{"description":"After"}}],
        "toDelete":[{"id":"%s","type":"REMOVE_EDGE","ecosystem":"java","graphType":"namespace_graph",
          "fromNodeKey":"app:obsolete","toNodeKey":"app:b","graphPerspective":"default_perspective"}],
        "skipped":1,"skippedIds":[],"warnings":%s}
      """.formatted(FIRST, SECOND, WARNINGS));
    assertThat(backend.getReceivedBodies("POST", PATH)).isEmpty();
    assertThat(backend.getReceivedBodies("PATCH", PATH + "/" + FIRST)).isEmpty();
    assertThat(backend.getReceivedBodies("DELETE", PATH + "/" + SECOND)).isEmpty();
    var applied = client.callTool(ArchitectureSetTool.TOOL_NAME, Map.of("target", "directives", "projectKey", "my_project", "model", model, "dryRun", false));
    assertThat(applied.isError()).isFalse();
    assertResultEquals(applied, """
      {"projectKey":"my_project","mode":"applied","counts":{"created":1,"updated":1,"deleted":1,"skipped":1},
        "createdIds":["%s"],"updatedIds":["%s"],"deletedIds":["%s"],"skippedIds":[],"warnings":%s}
      """.formatted(THIRD, FIRST, SECOND, WARNINGS));
    assertThat(backend.getReceivedBodies("POST", PATH)).hasSize(1);
    assertThat(backend.getReceivedBodies("PATCH", PATH + "/" + FIRST)).hasSize(1);
    assertThat(backend.getReceivedBodies("DELETE", PATH + "/" + SECOND)).hasSize(1);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "default_perspective", " default_perspective ", "transformed_code_perspective", "transformed_model_perspective"})
  void it_should_skip_existing_default_perspective_aliases(String perspective) {
    stubMainBranch(harness);
    var row = stored(FIRST, "app:a", perspective, null);
    stubPage(1, 100, row, 1);
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "model", "{\"create\":[" + INPUT + "]}", "dryRun", false));
    assertThat(result.isError()).isFalse();
    assertResultEquals(result, """
      {"projectKey":"my_project","mode":"applied","counts":{"created":0,"updated":0,"deleted":0,"skipped":1},
        "createdIds":[],"updatedIds":[],"deletedIds":[],"skippedIds":["%s"],"warnings":%s}
      """.formatted(FIRST, WARNINGS));
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("POST", PATH)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", " default_perspective ", "transformed_code_perspective", "transformed_model_perspective"})
  void it_should_skip_equivalent_creates_within_one_batch(String perspective) {
    stubMainBranch(harness);
    stubPage(1, 100, "", 0);
    var alias = JsonParser.parseString(INPUT).getAsJsonObject();
    alias.addProperty("graphPerspective", perspective);
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "model", "{\"create\":[" + INPUT + "," + alias + "]}", "dryRun", true));
    assertThat(result.isError()).isFalse();
    assertResultEquals(result, """
      {"projectKey":"my_project","mode":"dry-run","toCreate":[{
        "type":"REMOVE_EDGE","ecosystem":"java","graphType":"namespace_graph","fromNodeKey":"app:a","toNodeKey":"app:b",
        "graphPerspective":"default_perspective","origin":"sonarqube-mcp-server"}],"changes":[],"toDelete":[],"skipped":1,"skippedIds":[],
        "warnings":["Concurrent edits can race the reads; the public API has no conditional writes. Changes are applied individually without automatic retry or rollback.",
          "No directives are visible. This is normal before the first create; duplicate checks cannot identify directives hidden by permissions."]}
      """);
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("POST", PATH)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", " default_perspective ", "transformed_code_perspective"})
  void it_should_reject_update_collisions_with_equivalent_perspectives(String perspective) {
    stubMainBranch(harness);
    var before = stored(FIRST, "app:first", "default_perspective", null);
    stubPage(1, 100, before + "," + stored(SECOND, "app:a", perspective, null), 2);
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(get(PATH + "/" + FIRST).willReturn(okJson(before)));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "model", "{\"update\":[{\"id\":\"" + FIRST + "\",\"fromNodeKey\":\"app:a\"}]}", "dryRun", false));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("An update would duplicate another directive. Nothing was changed.");
    assertThat(backend.getReceivedBodies("PATCH", PATH + "/" + FIRST)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"custom_view", " custom_view ", "intended_perspective_mapped_detail_filtered"})
  void it_should_preserve_distinct_perspectives_and_original_values(String perspective) {
    stubMainBranch(harness);
    var existing = stored(FIRST, "app:a", "default_perspective", null);
    stubPage(1, 100, existing, 1);
    var input = JsonParser.parseString(INPUT).getAsJsonObject();
    input.addProperty("graphPerspective", perspective);
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(post(PATH).willReturn(aResponse().withStatus(201).withBody(stored(SECOND, "app:a", perspective, null))));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "model", "{\"create\":[" + input + "]}", "dryRun", false));
    assertThat(result.isError()).isFalse();
    var body = JsonParser.parseString(backend.getReceivedBodies("POST", PATH).getFirst()).getAsJsonObject();
    assertThat(body.get("graphPerspective").getAsString()).isEqualTo(perspective);
    backend.stubFor(get(PATH + "/" + SECOND).willReturn(okJson(stored(SECOND, "app:a", perspective, null))));
    var read = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "directiveId", SECOND));
    assertResultEquals(read, "{\"projectKey\":\"my_project\",\"directive\":" + stored(SECOND, "app:a", perspective, null) + "}");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", " default_perspective ", "transformed_code_perspective", " custom_view "})
  void it_should_confirm_explicit_perspective_updates_without_rewriting_them(String perspective) {
    stubMainBranch(harness);
    var before = stored(FIRST, "app:a", "default_perspective", null);
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(get(PATH + "/" + FIRST).willReturn(okJson(before)));
    stubPage(1, 100, before, 1);
    backend.stubFor(patch(PATH + "/" + FIRST).willReturn(okJson(stored(FIRST, "app:a", perspective, null))));
    var update = JsonParser.parseString("{\"id\":\"" + FIRST + "\"}").getAsJsonObject();
    update.addProperty("graphPerspective", perspective);
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "model", "{\"update\":[" + update + "]}", "dryRun", false));
    assertThat(result.isError()).as(textOf(result)).isFalse();
    assertResultEquals(result, """
      {"projectKey":"my_project","mode":"applied","counts":{"created":0,"updated":1,"deleted":0,"skipped":0},
        "createdIds":[],"updatedIds":["%s"],"deletedIds":[],"skippedIds":[],"warnings":%s}
      """.formatted(FIRST, WARNINGS));
    assertThat(backend.getReceivedBodies("PATCH", PATH + "/" + FIRST)).hasSize(1);
    var payload = JsonParser.parseString(backend.getReceivedBodies("PATCH", PATH + "/" + FIRST).getFirst()).getAsJsonObject();
    assertThat(payload.get("graphPerspective").getAsString()).isEqualTo(perspective);
  }

  @ParameterizedTest
  @CsvSource({
    "MOVE_NODE,java,namespace_graph,app:com.acme.web.Controller,app:com.acme.domain",
    "RENAME_NODE,java,namespace_graph,app:com.acme.web.Controller,app:com.acme.web.NewController",
    "MOVE_NODE,ts,file_graph,src/ui/View.ts,src/domain",
    "RENAME_NODE,ts,file_graph,src/ui/View.ts,src/ui/NewView.ts"
  })
  void it_should_preview_and_create_move_and_rename_directives(String type, String ecosystem, String graphType, String from, String to) {
    stubMainBranch(harness);
    stubPage(1, 100, stored(FIRST, "app:unrelated", "default_perspective", null), 1);
    var input = JsonParser.parseString(INPUT).getAsJsonObject();
    input.addProperty("type", type);
    input.addProperty("ecosystem", ecosystem);
    input.addProperty("graphType", graphType);
    input.addProperty("fromNodeKey", from);
    input.addProperty("toNodeKey", to);
    var created = input.deepCopy();
    created.addProperty("id", SECOND);
    created.addProperty("projectId", MAIN_BRANCH_ID);
    created.addProperty("graphPerspective", "default_perspective");
    created.addProperty("origin", "sonarqube-mcp-server");
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(post(PATH).willReturn(aResponse().withStatus(201).withBody(created.toString())));
    var client = harness.newClient(CLOUD_ENV);
    var env = Map.of("SONARQUBE_ORG", "org", "SONARQUBE_PROJECT_KEY", "my_project");
    var preview = harness.newClient(env).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "directives", "model", "{\"create\":[" + input + "]}", "dryRun", true));
    assertThat(preview.isError()).isFalse();
    assertResultEquals(preview, """
      {"projectKey":"my_project","mode":"dry-run","toCreate":[{
        "type":"%s","ecosystem":"%s","graphType":"%s","fromNodeKey":"%s","toNodeKey":"%s",
        "graphPerspective":"default_perspective","origin":"sonarqube-mcp-server"}],
        "changes":[],"toDelete":[],"skipped":0,"skippedIds":[],"warnings":%s}
      """.formatted(type, ecosystem, graphType, from, to, WARNINGS));
    assertThat(backend.getReceivedBodies("POST", PATH)).isEmpty();
    var apply = client.callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "model", "{\"create\":[" + input + "]}", "dryRun", false));
    assertThat(apply.isError()).isFalse();
    assertResultEquals(apply, """
      {"projectKey":"my_project","mode":"applied","counts":{"created":1,"updated":0,"deleted":0,"skipped":0},
        "createdIds":["%s"],"updatedIds":[],"deletedIds":[],"skippedIds":[],"warnings":%s}
      """.formatted(SECOND, WARNINGS));
    var expectedRequest = JsonParser.parseString("""
      {"projectId":"%s","type":"%s","ecosystem":"%s","graphType":"%s","fromNodeKey":"%s","toNodeKey":"%s",
        "graphPerspective":"default_perspective","origin":"sonarqube-mcp-server"}
      """.formatted(MAIN_BRANCH_ID, type, ecosystem, graphType, from, to));
    assertThat(backend.getReceivedBodies("POST", PATH)).hasSize(1);
    assertThat(JsonParser.parseString(backend.getReceivedBodies("POST", PATH).getFirst())).isEqualTo(expectedRequest);
  }

  @Test
  void it_should_show_unset_previous_values_and_keep_apply_size_independent_of_descriptions() {
    stubMainBranch(harness);
    var before = stored(FIRST, "app:a", "default_perspective", null);
    var backend = harness.getMockSonarQubeServer();
    backend.stubFor(get(PATH + "/" + FIRST).willReturn(okJson(before)));
    var client = harness.newClient(CLOUD_ENV);
    var smallPreview = client.callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "model", "{\"update\":[{\"id\":\"" + FIRST + "\",\"description\":\"New description\"}]}", "dryRun", true));
    assertThat(smallPreview.isError()).isFalse();
    assertResultEquals(smallPreview, """
      {"projectKey":"my_project","mode":"dry-run","toCreate":[],
        "changes":[{"id":"%s","before":{"description":null},"after":{"description":"New description"}}],
        "toDelete":[],"skipped":0,"skippedIds":[],"warnings":%s}
      """.formatted(FIRST, WARNINGS));
    var description = "x".repeat(10000);
    backend.stubFor(patch(PATH + "/" + FIRST).willReturn(okJson(stored(FIRST, "app:a", "default_perspective", description))));
    var apply = client.callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "directives", "projectKey", "my_project", "model", "{\"update\":[{\"id\":\"" + FIRST + "\",\"description\":\"" + description + "\"}]}", "dryRun", false));
    assertThat(apply.isError()).isFalse();
    assertResultEquals(apply, """
      {"projectKey":"my_project","mode":"applied","counts":{"created":0,"updated":1,"deleted":0,"skipped":0},
        "createdIds":[],"updatedIds":["%s"],"deletedIds":[],"skippedIds":[],"warnings":%s}
      """.formatted(FIRST, WARNINGS));
    assertThat(textOf(apply)).hasSizeLessThan(1000).doesNotContain(description);
    assertThat(backend.getReceivedBodies("PATCH", PATH + "/" + FIRST)).hasSize(1);
  }

  private Tool cloudTool(String name) {
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo("/organizations/organizations"))
      .willReturn(okJson("[{\"key\":\"a\"},{\"key\":\"b\"}]")));
    return harness.newClient(Map.of("SONARQUBE_IS_CLOUD", "true")).listTools().stream()
      .filter(tool -> tool.name().equals(name)).findFirst().orElseThrow();
  }

  private void stubPage(int index, int size, String rows, int total) {
    harness.getMockSonarQubeServer().stubFor(get(PATH + "?projectId=" + MAIN_BRANCH_ID + "&pageIndex=" + index + "&pageSize=" + size)
      .willReturn(okJson("{\"projectDirectives\":[" + rows + "],\"page\":{\"pageIndex\":" + index + ",\"pageSize\":" + size + ",\"total\":" + total + "}}")));
  }

  private static String stored(String id, String from, String perspective, String description) {
    var value = JsonParser.parseString(INPUT).getAsJsonObject();
    value.addProperty("id", id);
    value.addProperty("projectId", MAIN_BRANCH_ID);
    value.addProperty("fromNodeKey", from);
    value.addProperty("origin", "existing-client");
    if (perspective != null) {
      value.addProperty("graphPerspective", perspective);
    }
    if (description != null) {
      value.addProperty("description", description);
    }
    return value.toString();
  }
}
