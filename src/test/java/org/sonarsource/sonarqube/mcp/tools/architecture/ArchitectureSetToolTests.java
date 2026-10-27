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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.ArchitectureApi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.CLOUD_ENV;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.INTENDED_JAVA_MODEL;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.MAIN_BRANCH_ID;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.intendedArchitectures;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.stubMainBranch;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.textOf;

class ArchitectureSetToolTests {
  private static final String COLLIDING_CONSTRAINT_MODEL = """
    [{"id":"11111111-1111-4111-8111-111111111111","type":"namespace","language":"java","groups":[{"label":"A -> B"},{"label":"C"},{"label":"A"},{"label":"B -> C"}],
    "constraints":[{"from":"A -> B","to":"C"}]}]""";

  @Test
  void it_should_compare_constraint_endpoint_pairs_instead_of_display_strings() {
    var before = IntendedModels.parse(COLLIDING_CONSTRAINT_MODEL);
    var after = IntendedModels.parse(COLLIDING_CONSTRAINT_MODEL.replace("\"from\":\"A -> B\",\"to\":\"C\"", "\"from\":\"A\",\"to\":\"B -> C\""));

    var diff = IntendedModels.diff(before, after);
    assertThat(diff.removed()).containsExactly("java constraint \"A -> B\" -> C");
    assertThat(diff.added()).containsExactly("java constraint A -> \"B -> C\"");
    assertThat(diff.isEmpty()).isFalse();
  }

  @Test
  void it_should_only_reject_equal_constraint_endpoint_pairs_as_duplicates() {
    var model = JsonParser.parseString(COLLIDING_CONSTRAINT_MODEL).getAsJsonArray();
    var constraints = model.get(0).getAsJsonObject().getAsJsonArray("constraints");
    constraints.add(JsonParser.parseString("{\"from\":\"A\",\"to\":\"B -> C\"}"));
    assertThat(IntendedModels.invalidReason(IntendedModels.parse(model.toString()))).isNull();

    constraints.add(constraints.get(0).deepCopy());
    assertThat(IntendedModels.invalidReason(IntendedModels.parse(model.toString()))).contains("appears more than once");
  }

  @SonarQubeMcpServerTest
  void it_should_preview_then_apply_a_constraint_edit_with_colliding_display_text(SonarQubeMcpServerTestHarness harness) {
    var next = COLLIDING_CONSTRAINT_MODEL.replace("\"from\":\"A -> B\",\"to\":\"C\"", "\"from\":\"A\",\"to\":\"B -> C\"");
    stored(harness, COLLIDING_CONSTRAINT_MODEL);
    var revision = IntendedRevision.of(IntendedModels.parse(COLLIDING_CONSTRAINT_MODEL));

    var preview = set(harness, next, true, revision);
    assertThat(preview.isError()).isFalse();
    assertThat(textOf(preview)).contains("dry-run").doesNotContain("unchanged");
    assertWrites(harness, 0, 0);

    stubPatch(harness, next);

    var applied = set(harness, next, false, revision);
    assertThat(applied.isError()).withFailMessage(textOf(applied)).isFalse();
    assertThat(textOf(applied)).contains("replaced").doesNotContain("unchanged");
    assertWrites(harness, 1, 0);
    var body = JsonParser.parseString(harness.getMockSonarQubeServer().getReceivedBodies("PATCH", MODEL_PATH).getFirst()).getAsJsonObject();
    var expected = JsonParser.parseString(next).getAsJsonArray().get(0).getAsJsonObject().get("constraints");
    assertThat(body.getAsJsonArray("intendedArchitectures").get(0).getAsJsonObject().getAsJsonArray("constraints")).isEqualTo(expected);
  }

  @Test
  void it_should_detect_sibling_group_reordering() {
    var original = IntendedModels.parse(INTENDED_JAVA_MODEL);
    var reordered = JsonParser.parseString(INTENDED_JAVA_MODEL).getAsJsonArray();
    var children = reordered.get(0).getAsJsonObject().getAsJsonArray("groups").get(0).getAsJsonObject().getAsJsonArray("groups");
    var first = children.remove(0);
    children.add(first);

    assertThat(IntendedModels.diff(original, IntendedModels.parse(reordered.toString())).changed()).contains("java group order");
  }

  @Test
  void it_should_detect_empty_language_perspective_additions_and_removals() {
    var emptyJava = IntendedModels.parse("[{\"type\":\"namespace\",\"language\":\"java\",\"groups\":[],\"constraints\":[]}]");
    var emptyTs = IntendedModels.parse("[{\"type\":\"file\",\"language\":\"ts\",\"groups\":[],\"constraints\":[]}]");

    var diff = IntendedModels.diff(emptyJava, emptyTs);

    assertThat(diff.removed()).contains("java language");
    assertThat(diff.added()).contains("ts language");
  }

  @Test
  void it_should_reject_partial_models_before_discarding_view_metadata() {
    var model = "[{\"type\":\"namespace\",\"language\":\"java\",\"groups\":[{\"label\":\"app\",\"patterns\":[\"app:**\"]}],\"constraints\":[]}]";
    var partialInputs = List.of(
      "{\"component\":\"app\",\"models\":" + model + "}",
      "{\"depth\":1,\"models\":" + model + "}",
      "{\"language\":\"java\",\"models\":" + model + "}",
      model.replace("\"language\":", "\"partial\":true,\"language\":"),
      model.replace("\"label\":", "\"hiddenSubgroups\":2,\"label\":"),
      "{\"partial\":true,\"models\":" + model + "}",
      model.replace("\"language\":", "\"partial\":{},\"language\":"),
      model.replace("\"label\":", "\"hiddenSubgroups\":{},\"label\":"));

    for (var input : partialInputs) {
      assertThatThrownBy(() -> IntendedModels.parse(input))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("partial");
    }
  }

  @Test
  void it_should_ignore_pattern_and_constraint_order_but_keep_group_order() {
    var original = IntendedModels.parse("""
      [{"type":"namespace","language":"java","groups":[{"label":"app","patterns":["a:**","b:**"]}],
      "constraints":[{"from":"app","to":"app"},{"from":"app","to":"other"}]}]""");
    var reordered = IntendedModels.parse("""
      [{"type":"namespace","language":"java","groups":[{"label":"app","patterns":["b:**","a:**"]}],
      "constraints":[{"from":"app","to":"other"},{"from":"app","to":"app"}]}]""");

    assertThat(IntendedModels.diff(original, reordered).isEmpty()).isTrue();
  }

  private static final String PATH = ArchitectureApi.INTENDED_ARCHITECTURES_PATH + "?projectId=" + MAIN_BRANCH_ID;
  private static final String MODEL_PATH = ArchitectureApi.INTENDED_ARCHITECTURES_PATH + "/11111111-1111-4111-8111-111111111111";
  private static final String NEXT = INTENDED_JAVA_MODEL.replace("domain", "infra");

  @SonarQubeMcpServerTest
  void it_should_validate_annotations_and_schema(SonarQubeMcpServerTestHarness harness) {
    harness.getMockSonarQubeServer().stubFor(get(urlPathEqualTo("/organizations/organizations"))
      .willReturn(okJson("[{\"key\":\"a\"},{\"key\":\"b\"}]")));
    var tool = harness.newClient(Map.of("SONARQUBE_IS_CLOUD", "true")).listTools().stream().filter(t -> t.name().equals(ArchitectureSetTool.TOOL_NAME)).findFirst().orElseThrow();
    assertThat(tool.annotations().readOnlyHint()).isFalse();
    assertThat(tool.annotations().destructiveHint()).isTrue();
    assertThat(tool.annotations().openWorldHint()).isTrue();
    assertThat(tool.inputSchema().get("properties").toString()).contains("target", "organization", "dryRun", "revision");
    @SuppressWarnings("unchecked")
    var properties = (Map<String, Map<String, Object>>) tool.inputSchema().get("properties");
    assertThat(properties.get("target")).containsEntry("enum", List.of("intended", "directives"));
    assertThat(properties.get("model")).containsEntry("type", "string");
    assertThat(tool.inputSchema()).containsEntry("required", List.of("projectKey", "target", "model", "dryRun"));
  }

  @SonarQubeMcpServerTest
  void it_should_require_the_advertised_model_before_network(SonarQubeMcpServerTestHarness harness) {
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "intended", "projectKey", "my_project", "dryRun", true));

    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("model");
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/architecture/")).isFalse();
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/api/project_branches/")).isFalse();
    assertWrites(harness, 0, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_warn_about_incomplete_exports_in_a_dry_run_without_writes(SonarQubeMcpServerTestHarness harness) {
    stored(harness, INTENDED_JAVA_MODEL);
    var result = set(harness, NEXT, true, null);
    assertThat(result.isError()).isFalse();
    assertThat(textOf(result)).contains("dry-run", "java group app/domain", "java group app/infra", "patternId",
      "child groups must remain unedited or be omitted", "Keep patternId");
    assertWrites(harness, 0, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_reject_a_stale_revision_without_writes(SonarQubeMcpServerTestHarness harness) {
    stored(harness, INTENDED_JAVA_MODEL);
    var result = set(harness, NEXT, false, "stale");
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("changed since revision");
    assertWrites(harness, 0, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_reject_a_stale_embedded_revision_without_writes(SonarQubeMcpServerTestHarness harness) {
    stored(harness, INTENDED_JAVA_MODEL);
    var result = set(harness, "{\"revision\":\"stale\",\"models\":" + NEXT + "}", false, null);
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("changed since revision stale");
    assertWrites(harness, 0, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_accept_a_matching_embedded_revision_without_an_explicit_argument(SonarQubeMcpServerTestHarness harness) {
    stored(harness, INTENDED_JAVA_MODEL);
    var result = set(harness, "{\"revision\":\"" + revision() + "\",\"models\":" + INTENDED_JAVA_MODEL + "}", false, null);
    assertThat(result.isError()).isFalse();
    assertThat(textOf(result)).contains("unchanged").doesNotContain("No revision was supplied");
    assertWrites(harness, 0, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_reject_conflicting_explicit_and_embedded_revisions(SonarQubeMcpServerTestHarness harness) {
    stored(harness, INTENDED_JAVA_MODEL);
    var result = set(harness, "{\"revision\":\"stale\",\"models\":" + NEXT + "}", false, revision());
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("explicit revision differs");
    assertWrites(harness, 0, 0);
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining(ArchitectureApi.INTENDED_ARCHITECTURES_PATH)).isFalse();
  }

  @Test
  void it_should_reject_invalid_embedded_revisions() {
    for (var value : List.of("null", "42", "true", "{}", "[]", "\"\"", "\" \"")) {
      assertThatThrownBy(() -> IntendedModels.embeddedRevision("{\"revision\":" + value + ",\"models\":" + NEXT + "}"))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("revision must be a nonblank string");
    }
  }

  @SonarQubeMcpServerTest
  void it_should_reject_invalid_explicit_revisions(SonarQubeMcpServerTestHarness harness) {
    stored(harness, INTENDED_JAVA_MODEL);
    var client = harness.newClient(CLOUD_ENV);
    for (var value : List.of(42, true, Map.of(), List.of(), "", " ")) {
      var result = client.callTool(ArchitectureSetTool.TOOL_NAME,
        Map.of("target", "intended", "projectKey", "my_project", "model", NEXT, "dryRun", false, "revision", value));
      assertThat(result.isError()).isTrue();
      assertThat(textOf(result)).contains("revision", value instanceof String ? "must be a nonblank string" : "string expected");
    }
    assertWrites(harness, 0, 0);
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining(ArchitectureApi.INTENDED_ARCHITECTURES_PATH)).isFalse();
  }

  @SonarQubeMcpServerTest
  void it_should_warn_when_a_models_array_has_no_revision(SonarQubeMcpServerTestHarness harness) {
    stored(harness, INTENDED_JAVA_MODEL);
    var result = set(harness, NEXT, true, null);
    assertThat(result.isError()).isFalse();
    assertThat(textOf(result)).contains("No revision was supplied");
    assertWrites(harness, 0, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_accept_group_patterns_in_a_whole_export_dry_run(SonarQubeMcpServerTestHarness harness) {
    var models = INTENDED_JAVA_MODEL.replace("\"from\": \"app/web\"", "\"from\": \"app/*\"");
    stored(harness, models);
    var exportedRevision = IntendedRevision.of(IntendedModels.parse(models));
    var result = set(harness, "{\"revision\":\"" + exportedRevision + "\",\"models\":" + models + "}", true, null);
    assertThat(result.isError()).isFalse();
    assertThat(textOf(result)).contains("dry-run");
    assertWrites(harness, 0, 0);
  }

  @Test
  void it_should_accept_public_constraint_strings_and_reject_duplicate_constraints() {
    for (var endpoint : new String[] {"", " ", "app/missing", "app/*"}) {
      var models = JsonParser.parseString(INTENDED_JAVA_MODEL).getAsJsonArray();
      models.get(0).getAsJsonObject().getAsJsonArray("constraints").get(0).getAsJsonObject().addProperty("from", endpoint);
      assertThat(IntendedModels.invalidReason(IntendedModels.parse(models.toString()))).isNull();
    }
    var models = JsonParser.parseString(INTENDED_JAVA_MODEL.replace("\"from\": \"app/web\"", "\"from\": \"app/*\"")).getAsJsonArray();
    var constraints = models.get(0).getAsJsonObject().getAsJsonArray("constraints");
    constraints.add(constraints.get(0).deepCopy());
    assertThat(IntendedModels.invalidReason(IntendedModels.parse(models.toString()))).contains("appears more than once");
  }

  @Test
  void it_should_accept_optional_patterns_without_derived_pattern_rules() {
    for (var patterns : List.of("", ",\"patterns\":[]", ",\"patterns\":[\"component**\"]")) {
      var model = "[{\"type\":\"namespace\",\"language\":\"java\",\"groups\":[{\"label\":\"app\"" + patterns + "}],\"constraints\":[]}]";
      assertThat(IntendedModels.invalidReason(IntendedModels.parse(model))).isNull();
    }
  }

  @Test
  void it_should_require_public_field_types_without_coercing_values() {
    for (var invalid : List.of(
      INTENDED_JAVA_MODEL.replace("\"type\": \"namespace\",", ""),
      INTENDED_JAVA_MODEL.replace("\"type\": \"namespace\"", "\"type\": 42"),
      INTENDED_JAVA_MODEL.replace("\"language\": \"java\"", "\"language\": true"),
      INTENDED_JAVA_MODEL.replace("\"label\": \"app\"", "\"label\": 42"),
      INTENDED_JAVA_MODEL.replace("\"app:**\"", "null"),
      INTENDED_JAVA_MODEL.replace("\"interface\": true", "\"interface\": \"true\""),
      INTENDED_JAVA_MODEL.replace("\"from\": \"app/web\"", "\"from\": null"),
      INTENDED_JAVA_MODEL.replace("\"to\": \"app/domain\"", "\"to\": 42"))) {
      assertThatThrownBy(() -> IntendedModels.parse(invalid)).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @SonarQubeMcpServerTest
  void it_should_replace_using_only_the_public_patch_payload(SonarQubeMcpServerTestHarness harness) {
    stored(harness, INTENDED_JAVA_MODEL);
    stubPatch(harness, NEXT);
    var result = set(harness, NEXT, false, revision());
    assertThat(result.isError()).as(textOf(result)).isFalse();
    assertThat(textOf(result)).contains("replaced", "patternId");
    assertWrites(harness, 1, 0);
    var body = patchBody(harness);
    assertThat(body.keySet()).containsExactly("intendedArchitectures");
    assertThat(body.getAsJsonArray("intendedArchitectures")).hasSize(1);
    var perspective = body.getAsJsonArray("intendedArchitectures").get(0).getAsJsonObject();
    assertThat(perspective.keySet()).containsExactlyInAnyOrder("qualifiers", "language", "groups", "constraints");
    assertThat(perspective.toString()).contains("infra").doesNotContain("id", "projectId");
    assertNoPrivateRequests(harness);
  }



  @SonarQubeMcpServerTest
  void it_should_create_directly_when_the_export_is_empty(SonarQubeMcpServerTestHarness harness) {
    stored(harness, "[]");
    var multiple = multiple(NEXT, "ts");
    stubPost(harness, multiple);
    var result = set(harness, multiple, false, IntendedRevision.of(List.of()));
    assertThat(result.isError()).as(textOf(result)).isFalse();
    assertThat(textOf(result)).contains("created");
    assertWrites(harness, 0, 1);
    var body = JsonParser.parseString(harness.getMockSonarQubeServer()
      .getReceivedBodies("POST", ArchitectureApi.INTENDED_ARCHITECTURES_PATH).getFirst()).getAsJsonObject();
    assertThat(body.keySet()).containsExactlyInAnyOrder("projectId", "intendedArchitectures");
    assertThat(body.get("projectId").getAsString()).isEqualTo(MAIN_BRANCH_ID);
    assertThat(body.getAsJsonArray("intendedArchitectures")).hasSize(2);
    body.getAsJsonArray("intendedArchitectures").forEach(item -> assertThat(item.getAsJsonObject().has("id")).isFalse());
  }

  @SonarQubeMcpServerTest
  void it_should_preserve_an_existing_container_hidden_by_an_empty_public_export(SonarQubeMcpServerTestHarness harness) {
    stored(harness, "[]");
    harness.getMockSonarQubeServer().stubFor(post(ArchitectureApi.INTENDED_ARCHITECTURES_PATH)
      .willReturn(aResponse().withStatus(400).withBody("{\"message\":\"A model already exists\"}")));
    var result = set(harness, NEXT, false, IntendedRevision.of(List.of()));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("Error 400", "A model already exists").doesNotContain("Previous exported model", "No automatic rollback");
    assertWrites(harness, 0, 1);
  }

  private static final String FLAT_SLASH_MODEL = """
    [{"id":"11111111-1111-4111-8111-111111111111","type":"namespace","language":"java","groups":[{"label":"UI"},{"label":"UI/API","patterns":["app:**"]}],"constraints":[]}]""";
  private static final String NESTED_SLASH_MODEL = """
    [{"id":"11111111-1111-4111-8111-111111111111","type":"namespace","language":"java","groups":[{"label":"UI","groups":[{"label":"API","patterns":["app:**"]}]}],"constraints":[]}]""";

  @SonarQubeMcpServerTest
  void it_should_write_when_a_slash_label_becomes_a_nested_group(SonarQubeMcpServerTestHarness harness) {
    assertHierarchyReplacement(harness, FLAT_SLASH_MODEL, NESTED_SLASH_MODEL);
  }

  @SonarQubeMcpServerTest
  void it_should_write_when_a_nested_group_becomes_a_slash_label(SonarQubeMcpServerTestHarness harness) {
    assertHierarchyReplacement(harness, NESTED_SLASH_MODEL, FLAT_SLASH_MODEL);
  }

  @SonarQubeMcpServerTest
  void it_should_preview_hierarchy_and_settings_changes_together_without_writes(SonarQubeMcpServerTestHarness harness) {
    stored(harness, FLAT_SLASH_MODEL);
    var next = NESTED_SLASH_MODEL.replace("\"patterns\":[\"app:**\"]", "\"patterns\":[\"api:**\"],\"interface\":true,\"interfaceAccess\":true");

    var result = set(harness, next, true, IntendedRevision.of(IntendedModels.parse(FLAT_SLASH_MODEL)));

    assertThat(result.isError()).as(textOf(result)).isFalse();
    assertThat(textOf(result)).contains("dry-run");
    var diff = JsonParser.parseString(textOf(result)).getAsJsonObject().getAsJsonObject("diff");
    assertThat(diff.getAsJsonArray("removed").toString()).contains("java group UI/API");
    assertThat(diff.getAsJsonArray("added").toString()).contains("java group UI/API");
    assertWrites(harness, 0, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_not_write_for_unchanged_slash_label_hierarchy(SonarQubeMcpServerTestHarness harness) {
    stored(harness, FLAT_SLASH_MODEL);

    var result = set(harness, FLAT_SLASH_MODEL, false, IntendedRevision.of(IntendedModels.parse(FLAT_SLASH_MODEL)));

    assertThat(result.isError()).isFalse();
    assertThat(textOf(result)).contains("unchanged");
    assertWrites(harness, 0, 0);
  }

  private static void assertHierarchyReplacement(SonarQubeMcpServerTestHarness harness, String before, String after) {
    stored(harness, before);
    stubPatch(harness, after);

    var result = set(harness, after, false, IntendedRevision.of(IntendedModels.parse(before)));

    assertThat(result.isError()).as(textOf(result)).isFalse();
    assertThat(textOf(result)).contains("replaced", "java group UI/API").doesNotContain("unchanged");
    var diff = JsonParser.parseString(textOf(result)).getAsJsonObject().getAsJsonObject("diff");
    assertThat(diff.getAsJsonArray("removed").toString()).contains("java group UI/API");
    assertThat(diff.getAsJsonArray("added").toString()).contains("java group UI/API");
    assertWrites(harness, 1, 0);
    var expected = JsonParser.parseString(created(after)).getAsJsonObject();
    expected.remove("id");
    expected.add("qualifiers", expected.remove("type"));
    assertThat(patchBody(harness).getAsJsonArray("intendedArchitectures").get(0)).isEqualTo(expected);
  }

  @SonarQubeMcpServerTest
  void it_should_round_trip_an_unchanged_imported_expansion(SonarQubeMcpServerTestHarness harness) {
    var before = INTENDED_JAVA_MODEL.replace("\"label\": \"app\"", "\"patternId\":\"33333333-3333-4333-8333-333333333333\",\"label\": \"app\"");
    stored(harness, before);
    var read = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "intended", "projectKey", "my_project"));
    for (var dryRun : List.of(true, false)) {
      var result = set(harness, textOf(read), dryRun, null);
      assertThat(result.isError()).as(textOf(result)).isFalse();
      assertThat(textOf(result)).contains(dryRun ? "dry-run" : "unchanged");
    }
    assertWrites(harness, 0, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_edit_a_sibling_while_preserving_an_imported_expansion(SonarQubeMcpServerTestHarness harness) {
    var before = INTENDED_JAVA_MODEL.replace("\"label\": \"app\"", "\"patternId\":\"33333333-3333-4333-8333-333333333333\",\"description\":\"Imported group\",\"label\": \"app\"");
    stored(harness, before);
    var read = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "intended", "projectKey", "my_project"));
    var exported = JsonParser.parseString(textOf(read)).getAsJsonObject();
    exported.getAsJsonArray("models").get(0).getAsJsonObject().getAsJsonArray("groups").add(JsonParser.parseString("{\"label\":\"Storage\"}"));
    var next = exported.getAsJsonArray("models").toString();
    var preview = set(harness, exported.toString(), true, null);
    assertThat(preview.isError()).as(textOf(preview)).isFalse();
    assertThat(textOf(preview)).contains("dry-run", "Storage");
    assertWrites(harness, 0, 0);
    stubPatch(harness, next);
    var applied = set(harness, exported.toString(), false, null);
    assertThat(applied.isError()).as(textOf(applied)).isFalse();
    assertThat(textOf(applied)).contains("replaced");
    assertWrites(harness, 1, 0);
    var imported = patchBody(harness).getAsJsonArray("intendedArchitectures").get(0).getAsJsonObject().getAsJsonArray("groups").get(0).getAsJsonObject();
    assertThat(imported.has("groups")).isFalse();
    assertThat(imported.get("patternId").getAsString()).isEqualTo("33333333-3333-4333-8333-333333333333");
    assertThat(imported.get("description").getAsString()).isEqualTo("Imported group");
  }

  @SonarQubeMcpServerTest
  void it_should_report_stale_revisions_before_imported_content_edits(SonarQubeMcpServerTestHarness harness) {
    var before = INTENDED_JAVA_MODEL.replace("\"label\": \"app\"", "\"patternId\":\"33333333-3333-4333-8333-333333333333\",\"label\": \"app\"");
    stored(harness, before);
    var result = set(harness, before.replace("app:com.acme.domain.**", "ignored:**"), true, "stale");
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("changed since revision stale").doesNotContain("imports a pattern and cannot declare child groups");
    assertWrites(harness, 0, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_restore_writable_recovery_fields_after_an_imported_pattern_changes(SonarQubeMcpServerTestHarness harness) {
    var before = INTENDED_JAVA_MODEL.replace("\"label\": \"app\"", "\"patternId\":\"33333333-3333-4333-8333-333333333333\",\"label\": \"app\"");
    stored(harness, before);
    harness.getMockSonarQubeServer().stubFor(patch(MODEL_PATH).willReturn(aResponse().withStatus(500)));
    var failure = set(harness, withoutImportedChildren(before).replace("app:**", "proposed:**"), false, null);
    assertThat(failure.isError()).isTrue();
    var message = textOf(failure);
    var recovery = message.substring(message.indexOf("Previous exported model for manual recovery: ") + "Previous exported model for manual recovery: ".length());
    var backupGroup = JsonParser.parseString(recovery).getAsJsonObject().getAsJsonArray("models").get(0).getAsJsonObject().getAsJsonArray("groups").get(0).getAsJsonObject();
    assertThat(backupGroup.has("groups")).isFalse();
    assertThat(backupGroup.get("patternId").getAsString()).isEqualTo("33333333-3333-4333-8333-333333333333");
    var changedExpansion = before.replace("app:com.acme.domain.**", "organization:new.**");
    stored(harness, changedExpansion.replace("app:**", "partial:**"));
    stubPatch(harness, changedExpansion);
    var restored = set(harness, recovery, false, null);
    assertThat(restored.isError()).as(textOf(restored)).isFalse();
    assertThat(textOf(restored)).contains("replaced");
    assertThat(JsonParser.parseString(textOf(restored)).getAsJsonObject().getAsJsonObject("diff").getAsJsonArray("removed")).isEmpty();
    assertWrites(harness, 2, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_accept_nullable_public_fields_and_an_unchanged_linked_leaf_in_a_complete_export(SonarQubeMcpServerTestHarness harness) {
    var before = """
      [{"id":"11111111-1111-4111-8111-111111111111","type":"namespace","language":"java","label":null,"description":null,
      "groups":[{"label":"Imported","groups":[],"description":null,"patterns":null,"interface":null,"interfaceAccess":false,
      "patternId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"}],"constraints":[]}]
      """;
    stored(harness, before);
    var client = harness.newClient(CLOUD_ENV);
    var read = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "intended", "projectKey", "my_project"));
    assertThat(read.isError()).isFalse();
    var exported = JsonParser.parseString(textOf(read)).getAsJsonObject();
    var group = exported.getAsJsonArray("models").get(0).getAsJsonObject().getAsJsonArray("groups").get(0).getAsJsonObject();
    group.addProperty("patternId", "AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA");
    exported.getAsJsonArray("models").get(0).getAsJsonObject().getAsJsonArray("groups")
      .add(JsonParser.parseString("{\"label\":\"Storage\"}"));
    var preview = set(harness, exported.toString(), true, null);
    assertThat(preview.isError()).as(textOf(preview)).isFalse();
    assertThat(textOf(preview)).contains("dry-run", "Storage");
    group.add("groups", JsonParser.parseString("[{\"label\":\"Ignored child edit\"}]"));
    var refused = set(harness, exported.toString(), true, null);
    assertThat(refused.isError()).isTrue();
    assertThat(textOf(refused)).contains("imports a pattern and cannot declare child groups");
    assertWrites(harness, 0, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_refuse_edits_to_an_imported_expansion_without_writing(SonarQubeMcpServerTestHarness harness) {
    var before = INTENDED_JAVA_MODEL.replace("\"label\": \"app\"", "\"patternId\":\"33333333-3333-4333-8333-333333333333\",\"label\": \"app\"");
    stored(harness, before);
    var next = before.replace("app:com.acme.domain.**", "ignored:**");
    for (var dryRun : List.of(true, false)) {
      var result = set(harness, next, dryRun, null);
      assertThat(result.isError()).isTrue();
      assertThat(textOf(result)).contains("imports a pattern and cannot declare child groups", "Nothing was changed");
    }
    assertWrites(harness, 0, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_reject_children_on_new_pattern_links_in_preview_and_write(SonarQubeMcpServerTestHarness harness) {
    stored(harness, INTENDED_JAVA_MODEL);
    for (var nested : List.of(false, true)) {
      var next = JsonParser.parseString(INTENDED_JAVA_MODEL).getAsJsonArray();
      var groups = next.get(0).getAsJsonObject().getAsJsonArray("groups");
      if (nested) {
        groups = groups.get(0).getAsJsonObject().getAsJsonArray("groups");
      }
      groups.add(JsonParser.parseString("""
        {"label":"New import","patternId":"33333333-3333-4333-8333-333333333333","groups":[{"label":"Child"}]}
        """));
      for (var dryRun : List.of(true, false)) {
        var result = set(harness, next.toString(), dryRun, null);
        assertThat(result.isError()).isTrue();
        assertThat(textOf(result)).contains("Group 'New import' imports a pattern and cannot declare child groups", "Nothing was changed");
      }
    }
    assertWrites(harness, 0, 0);
  }

  @Test
  void it_should_compare_all_imported_descendants_with_semantic_defaults() {
    var before = """
      [{"id":"11111111-1111-4111-8111-111111111111","type":"namespace","language":"java","groups":[
        {"label":"Parent","groups":[{"label":"Imported","patternId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","groups":[
          {"label":"Nested import","patternId":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb","patterns":["a:**","b:**"],"groups":[
            {"label":"Derived","description":"Stored description"}]}]}]}],"constraints":[]}]
      """;
    var previous = IntendedModels.parse(before);
    var equivalent = IntendedModels.parse(before.replace("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA")
      .replace("\"a:**\",\"b:**\"", "\"b:**\",\"a:**\"")
      .replace("\"description\":\"Stored description\"", "\"description\":\"Stored description\",\"patterns\":[],\"groups\":[],\"interface\":false,\"interfaceAccess\":null"));
    IntendedArchitectureReplacement.checkWritableModels(previous, equivalent);
    for (var changed : List.of(before.replace("Stored description", "Edited description"), before.replace("Derived", "Added child"),
      before.replace("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", "cccccccc-cccc-4ccc-8ccc-cccccccccccc"),
      before.replace("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "cccccccc-cccc-4ccc-8ccc-cccccccccccc"),
      before.replace("namespace", "file"), before.replace("java", "js"))) {
      var next = IntendedModels.parse(changed);
      assertThatThrownBy(() -> IntendedArchitectureReplacement.checkWritableModels(previous, next))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("imports a pattern and cannot declare child groups");
    }
  }

  @SonarQubeMcpServerTest
  void it_should_distinguish_import_paths_from_literal_slashes(SonarQubeMcpServerTestHarness harness) {
    var before = """
      [{"id":"11111111-1111-4111-8111-111111111111","type":"namespace","language":"java","groups":[
        {"label":"UI","groups":[{"label":"API","patternId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","groups":[{"label":"Nested"}]}]},
        {"label":"UI/API","patternId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","groups":[{"label":"Literal"}]}],"constraints":[]}]
      """;
    stored(harness, before);
    var unchanged = set(harness, before, true, null);
    assertThat(unchanged.isError()).as(textOf(unchanged)).isFalse();
    for (var dryRun : List.of(true, false)) {
      var changed = set(harness, before.replace("Nested", "Literal"), dryRun, null);
      assertThat(changed.isError()).isTrue();
      assertThat(textOf(changed)).contains("Group 'API' imports a pattern and cannot declare child groups");
    }
    assertWrites(harness, 0, 0);
  }

  private static String withoutImportedChildren(String model) {
    var models = JsonParser.parseString(model).getAsJsonArray();
    models.get(0).getAsJsonObject().getAsJsonArray("groups").get(0).getAsJsonObject().remove("groups");
    return models.toString();
  }

  @SonarQubeMcpServerTest
  void it_should_verify_a_changed_import_link_using_its_writable_fields(SonarQubeMcpServerTestHarness harness) {
    var before = INTENDED_JAVA_MODEL.replace("\"label\": \"app\"", "\"patternId\":\"33333333-3333-4333-8333-333333333333\",\"label\": \"app\"");
    var next = withoutImportedChildren(before).replace("33333333-3333-4333-8333-333333333333", "44444444-4444-4444-8444-444444444444");
    var resolved = before.replace("33333333-3333-4333-8333-333333333333", "44444444-4444-4444-8444-444444444444")
      .replace("app:com.acme.domain.**", "resolved:new.**");
    stored(harness, before);
    stubPatch(harness, resolved);
    var result = set(harness, next, false, IntendedRevision.of(IntendedModels.parse(before)));
    assertThat(result.isError()).as(textOf(result)).isFalse();
    assertThat(textOf(result)).contains("replaced");
    assertWrites(harness, 1, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_preserve_public_metadata_and_apply_metadata_only_changes(SonarQubeMcpServerTestHarness harness) {
    var before = INTENDED_JAVA_MODEL.replace("\"type\": \"namespace\"", "\"label\":\"Perspective\",\"description\":\"Before\",\"type\": \"namespace\"")
      .replace("\"label\": \"app\"", "\"description\":\"Group description\",\"patternId\":\"33333333-3333-4333-8333-333333333333\",\"label\": \"app\"");
    var next = withoutImportedChildren(before).replace("Before", "After");
    stored(harness, before);
    stubPatch(harness, next);
    assertThat(IntendedRevision.of(IntendedModels.parse(next))).isNotEqualTo(IntendedRevision.of(IntendedModels.parse(before)));
    var result = set(harness, next, false, IntendedRevision.of(IntendedModels.parse(before)));
    assertThat(result.isError()).as(textOf(result)).isFalse();
    assertThat(textOf(result)).contains("replaced", "perspective metadata");
    var body = patchBody(harness).getAsJsonArray("intendedArchitectures").get(0).getAsJsonObject();
    assertThat(body.get("label").getAsString()).isEqualTo("Perspective");
    assertThat(body.get("description").getAsString()).isEqualTo("After");
    var group = body.getAsJsonArray("groups").get(0).getAsJsonObject();
    assertThat(group.get("description").getAsString()).isEqualTo("Group description");
    assertThat(group.get("patternId").getAsString()).isEqualTo("33333333-3333-4333-8333-333333333333");
    assertWrites(harness, 1, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_keep_literal_slash_and_nested_group_identities_distinct(SonarQubeMcpServerTestHarness harness) {
    var before = """
      [{"id":"11111111-1111-4111-8111-111111111111","type":"namespace","language":"java","groups":[
        {"label":"UI","groups":[{"label":"API","patterns":["nested:**"]}]},
        {"label":"UI/API","patterns":["literal:**"]}],"constraints":[]}]
      """;
    var next = before.replace("nested:**", "changed:**");
    assertThat(IntendedModels.invalidReason(IntendedModels.parse(before))).isNull();
    stored(harness, before);
    stubPatch(harness, next);

    var result = set(harness, next, false, IntendedRevision.of(IntendedModels.parse(before)));

    assertThat(result.isError()).as(textOf(result)).isFalse();
    assertThat(textOf(result)).contains("replaced", "patterns [nested:**] -> patterns [changed:**]");
    assertWrites(harness, 1, 0);
    var expected = JsonParser.parseString(next).getAsJsonArray().get(0).getAsJsonObject();
    expected.remove("id");
    expected.add("qualifiers", expected.remove("type"));
    assertThat(patchBody(harness).getAsJsonArray("intendedArchitectures").get(0)).isEqualTo(expected);
  }

  @SonarQubeMcpServerTest
  void it_should_lead_with_the_partial_view_reason(SonarQubeMcpServerTestHarness harness) {
    var result = set(harness, "{\"partial\":true,\"models\":" + NEXT + "}", true, null);
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("This is a partial architecture view", "Read the complete model")
      .doesNotContain("model must be a JSON array");
    assertWrites(harness, 0, 0);
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/architecture/")).isFalse();
  }

  @SonarQubeMcpServerTest
  void it_should_not_write_for_an_unchanged_model(SonarQubeMcpServerTestHarness harness) {
    stored(harness, INTENDED_JAVA_MODEL);
    assertThat(textOf(set(harness, INTENDED_JAVA_MODEL, false, revision()))).contains("unchanged");
    assertWrites(harness, 0, 0);
  }


  @SonarQubeMcpServerTest
  void it_should_verify_a_committed_patch_after_a_failed_response(SonarQubeMcpServerTestHarness harness) {
    stored(harness, INTENDED_JAVA_MODEL);
    harness.getMockSonarQubeServer().stubFor(patch(MODEL_PATH).inScenario("committed").whenScenarioStateIs("Started")
      .willSetStateTo("saved").willReturn(aResponse().withStatus(500)));
    harness.getMockSonarQubeServer().stubFor(get(PATH).inScenario("committed").whenScenarioStateIs("saved")
      .willReturn(okJson(intendedArchitectures(NEXT))));
    var result = set(harness, NEXT, false, revision());
    assertThat(result.isError()).as(textOf(result)).isFalse();
    assertThat(textOf(result)).contains("replaced");
    assertWrites(harness, 1, 0);
  }


  @SonarQubeMcpServerTest
  void it_should_reject_invalid_or_partial_models_before_network(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient(CLOUD_ENV);
    for (var input : List.of("not json", "[]", "[{\"language\":\"bad\"}]", "{\"partial\":true,\"models\":" + NEXT + "}")) {
      var result = client.callTool(ArchitectureSetTool.TOOL_NAME, Map.of("target", "intended", "projectKey", "my_project", "model", input, "dryRun", true));
      assertThat(result.isError()).isTrue();
    }
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/architecture/")).isFalse();
    assertWrites(harness, 0, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_reject_unknown_write_targets_before_network(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient(CLOUD_ENV);
    for (var target : List.of("patterns", "current", "unsupported")) {
      var result = client.callTool(ArchitectureSetTool.TOOL_NAME, Map.of("target", target, "projectKey", "my_project", "dryRun", false));
      assertThat(result.isError()).isTrue();
      assertThat(textOf(result)).contains("intended");
    }
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/architecture/")).isFalse();
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/api/project_branches/")).isFalse();
  }

  @SonarQubeMcpServerTest
  void it_should_reject_server_intended_writes_before_private_id_discovery(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient();
    assertThatThrownBy(() -> client.callTool(ArchitectureSetTool.TOOL_NAME,
      Map.of("target", "intended", "projectKey", "my_project", "model", NEXT, "dryRun", true)))
      .isInstanceOf(io.modelcontextprotocol.spec.McpError.class).hasMessage("Unknown tool: invalid_tool_name");
    assertNoPrivateRequests(harness);
    assertWrites(harness, 0, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_mark_filtered_reads_as_partial_and_refuse_their_models_array(SonarQubeMcpServerTestHarness harness) {
    stored(harness, INTENDED_JAVA_MODEL);
    var client = harness.newClient(CLOUD_ENV);
    for (var filter : List.of(Map.of("language", "java"), Map.of("component", "app"), Map.of("depth", 1))) {
      var args = new java.util.HashMap<String, Object>(Map.of("target", "intended", "projectKey", "my_project"));
      args.putAll(filter);
      var read = client.callTool(ArchitectureGetTool.TOOL_NAME, args);
      var envelope = JsonParser.parseString(textOf(read)).getAsJsonObject();
      assertThat(envelope.get("partial").getAsBoolean()).isTrue();
      var models = envelope.getAsJsonArray("models");
      assertThat(models.get(0).getAsJsonObject().get("partial").getAsBoolean()).isTrue();
      assertThat(textOf(set(harness, models.toString(), false, revision()))).contains("partial");
    }
    assertWrites(harness, 0, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_mark_whole_filtered_envelopes_partial_even_when_no_language_matches(SonarQubeMcpServerTestHarness harness) {
    stored(harness, multiple(INTENDED_JAVA_MODEL, "ts"));
    var client = harness.newClient(CLOUD_ENV);
    for (var language : List.of("java", "cs")) {
      var read = client.callTool(ArchitectureGetTool.TOOL_NAME,
        Map.of("target", "intended", "projectKey", "my_project", "language", language));
      var json = JsonParser.parseString(textOf(read)).getAsJsonObject();
      assertThat(json.get("partial").getAsBoolean()).isTrue();
      assertThat(json.getAsJsonArray("models")).hasSize(language.equals("java") ? 1 : 0);
      var exportedModel = textOf(read);
      assertThatThrownBy(() -> IntendedModels.parse(exportedModel))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("partial");
      assertThat(textOf(set(harness, textOf(read), false, null))).contains("partial");
    }
    assertWrites(harness, 0, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_round_trip_an_unfiltered_multi_perspective_envelope(SonarQubeMcpServerTestHarness harness) {
    stored(harness, multiple(INTENDED_JAVA_MODEL, "ts"));
    var read = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME,
      Map.of("target", "intended", "projectKey", "my_project"));
    var json = JsonParser.parseString(textOf(read)).getAsJsonObject();

    assertThat(json.has("partial")).isFalse();
    assertThat(IntendedModels.parse(textOf(read))).hasSize(2);
    var result = set(harness, textOf(read), false, null);
    assertThat(result.isError()).as(textOf(result)).isFalse();
    assertThat(textOf(result)).contains("unchanged");
    assertWrites(harness, 0, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_reconcile_an_initial_create_committed_before_a_failed_response(SonarQubeMcpServerTestHarness harness) {
    stored(harness, "[]");
    harness.getMockSonarQubeServer().stubFor(post(ArchitectureApi.INTENDED_ARCHITECTURES_PATH)
      .inScenario("create").whenScenarioStateIs("Started").willSetStateTo("committed").willReturn(aResponse().withStatus(500)));
    harness.getMockSonarQubeServer().stubFor(get(PATH).inScenario("create").whenScenarioStateIs("committed")
      .willReturn(okJson(intendedArchitectures(NEXT))));
    var result = set(harness, NEXT, false, IntendedRevision.of(List.of()));
    assertThat(result.isError()).as(textOf(result)).isFalse();
    assertThat(textOf(result)).contains("created");
    assertWrites(harness, 0, 1);
  }


  @SonarQubeMcpServerTest
  void it_should_return_recovery_data_to_the_caller_without_logging_it(SonarQubeMcpServerTestHarness harness) {
    var sentinel = "private-recovery-pattern-sentinel";
    var previous = INTENDED_JAVA_MODEL.replace("app:com.acme.domain.**", sentinel);
    stored(harness, previous);
    harness.getMockSonarQubeServer().stubFor(patch(MODEL_PATH).inScenario("privacy").whenScenarioStateIs("Started")
      .willSetStateTo("survivor").willReturn(aResponse().withStatus(500)));
    harness.getMockSonarQubeServer().stubFor(get(PATH).inScenario("privacy").whenScenarioStateIs("survivor")
      .willReturn(okJson(intendedArchitectures(NEXT.replace("infra", "concurrent-edit")))));
    var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.sonarsource.sonarqube.mcp.log.McpLogger.class);
    var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    var originalErr = System.err;
    var stderr = new java.io.ByteArrayOutputStream();
    try (var capturedErr = new java.io.PrintStream(stderr, true, java.nio.charset.StandardCharsets.UTF_8)) {
      System.setErr(capturedErr);
      var result = set(harness, NEXT, false, IntendedRevision.of(IntendedModels.parse(previous)));
      assertThat(result.isError()).isTrue();
      assertThat(textOf(result)).contains("Previous exported model", sentinel);
      assertThat(stderr.toString(java.nio.charset.StandardCharsets.UTF_8)).doesNotContain(sentinel, "Previous exported model");
      assertThat(appender.list).allSatisfy(event -> {
        assertThat(event.getFormattedMessage()).doesNotContain(sentinel, "Previous exported model");
        assertThat(event.getThrowableProxy()).isNull();
      });
    } finally {
      System.setErr(originalErr);
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @SonarQubeMcpServerTest
  void it_should_replace_all_perspectives_with_their_shared_model_id(SonarQubeMcpServerTestHarness harness) {
    var before = multiple(INTENDED_JAVA_MODEL, "ts");
    var after = multiple(NEXT, "ts");
    stored(harness, before);
    assertThat(textOf(set(harness, after, true, null))).contains("dry-run", "java group", "ts group");
    assertWrites(harness, 0, 0);
    stubPatch(harness, after);
    var result = set(harness, after, false, IntendedRevision.of(IntendedModels.parse(before)));
    assertThat(result.isError()).as(textOf(result)).isFalse();
    assertWrites(harness, 1, 0);
    assertThat(patchBody(harness).getAsJsonArray("intendedArchitectures")).hasSize(2);
  }

  @SonarQubeMcpServerTest
  void it_should_refuse_missing_or_invalid_server_id_before_patch(SonarQubeMcpServerTestHarness harness) {
    stored(harness, INTENDED_JAVA_MODEL.replace("11111111-1111-4111-8111-111111111111", "unknown/id"));
    assertThat(textOf(set(harness, NEXT, false, null))).contains("incomplete intended architecture");
    assertWrites(harness, 0, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_not_claim_success_for_a_malformed_created_resource(SonarQubeMcpServerTestHarness harness) {
    stored(harness, "[]");
    harness.getMockSonarQubeServer().stubFor(post(ArchitectureApi.INTENDED_ARCHITECTURES_PATH)
      .willReturn(aResponse().withStatus(201).withBody("{}")));
    var result = set(harness, NEXT, false, null);
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("No automatic rollback", "Previous exported model");
    assertWrites(harness, 0, 1);
  }

  @SonarQubeMcpServerTest
  void it_should_refuse_a_public_model_without_a_resource_id(SonarQubeMcpServerTestHarness harness) {
    stored(harness, INTENDED_JAVA_MODEL);
    var response = JsonParser.parseString(intendedArchitectures(INTENDED_JAVA_MODEL)).getAsJsonObject();
    response.remove("id");
    harness.getMockSonarQubeServer().stubFor(get(PATH).willReturn(okJson(response.toString())));
    assertThat(textOf(set(harness, NEXT, false, null))).contains("incomplete intended architecture");
    assertWrites(harness, 0, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_detect_a_changed_server_resource_id_even_when_content_is_identical(SonarQubeMcpServerTestHarness harness) {
    stored(harness, INTENDED_JAVA_MODEL);
    harness.getMockSonarQubeServer().stubFor(get(PATH).inScenario("changed-id").whenScenarioStateIs("Started")
      .willSetStateTo("changed").willReturn(okJson(intendedArchitectures(INTENDED_JAVA_MODEL))));
    harness.getMockSonarQubeServer().stubFor(get(PATH).inScenario("changed-id").whenScenarioStateIs("changed")
      .willReturn(okJson(intendedArchitectures(INTENDED_JAVA_MODEL.replace("11111111-1111-4111-8111-111111111111", "22222222-2222-4222-8222-222222222222")))));
    assertThat(textOf(set(harness, NEXT, false, null))).contains("changed before replacement");
    assertWrites(harness, 0, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_verify_readback_after_a_successful_patch_response(SonarQubeMcpServerTestHarness harness) {
    stored(harness, INTENDED_JAVA_MODEL);
    harness.getMockSonarQubeServer().stubFor(patch(MODEL_PATH).willReturn(okJson(intendedArchitectures(NEXT))));

    var result = set(harness, NEXT, false, revision());

    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("No automatic rollback", "Previous exported model", "domain");
    assertWrites(harness, 1, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_refuse_an_unsupported_patch_without_a_delete_or_post_fallback(SonarQubeMcpServerTestHarness harness) {
    stored(harness, INTENDED_JAVA_MODEL);
    harness.getMockSonarQubeServer().stubFor(patch(MODEL_PATH).willReturn(aResponse().withStatus(405)));

    var result = set(harness, NEXT, false, revision());

    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("Error 405").doesNotContain("Previous exported model", "No automatic rollback");
    assertWrites(harness, 1, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_not_clobber_a_recreated_model_after_a_failed_patch(SonarQubeMcpServerTestHarness harness) {
    stored(harness, INTENDED_JAVA_MODEL);
    harness.getMockSonarQubeServer().stubFor(patch(MODEL_PATH).inScenario("recreated").whenScenarioStateIs("Started")
      .willSetStateTo("new-id").willReturn(aResponse().withStatus(500)));
    var recreated = NEXT.replace("11111111-1111-4111-8111-111111111111", "22222222-2222-4222-8222-222222222222");
    harness.getMockSonarQubeServer().stubFor(get(PATH).inScenario("recreated").whenScenarioStateIs("new-id")
      .willReturn(okJson(intendedArchitectures(recreated))));

    var result = set(harness, NEXT, false, revision());

    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("No automatic rollback", "Previous exported model");
    assertWrites(harness, 1, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_refuse_a_partial_response_after_a_multi_perspective_patch(SonarQubeMcpServerTestHarness harness) {
    var before = multiple(INTENDED_JAVA_MODEL, "ts");
    var after = multiple(NEXT, "ts");
    stored(harness, before);
    stubPatch(harness, NEXT);

    var result = set(harness, after, false, IntendedRevision.of(IntendedModels.parse(before)));

    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("No automatic rollback", "Previous exported model", "ts");
    assertWrites(harness, 1, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_not_repeat_a_write_when_the_resulting_state_is_unknown(SonarQubeMcpServerTestHarness harness) {
    stored(harness, INTENDED_JAVA_MODEL);
    harness.getMockSonarQubeServer().stubFor(patch(MODEL_PATH).inScenario("unknown").whenScenarioStateIs("Started")
      .willSetStateTo("unreadable").willReturn(aResponse().withStatus(500)));
    harness.getMockSonarQubeServer().stubFor(get(PATH).inScenario("unknown").whenScenarioStateIs("unreadable")
      .willReturn(aResponse().withStatus(503)));

    var result = set(harness, NEXT, false, revision());

    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("resulting state could not be read", "No automatic rollback", "Previous exported model");
    assertWrites(harness, 1, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_verify_created_models_in_a_public_read(SonarQubeMcpServerTestHarness harness) {
    stored(harness, "[]");
    harness.getMockSonarQubeServer().stubFor(post(ArchitectureApi.INTENDED_ARCHITECTURES_PATH)
      .willReturn(aResponse().withStatus(201).withBody(intendedArchitectures(NEXT))));

    var result = set(harness, NEXT, false, IntendedRevision.of(List.of()));

    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("No automatic rollback", "Previous exported model");
    assertWrites(harness, 0, 1);
  }

  @Test
  void it_should_distinguish_qualifiers_with_the_same_language() {
    var before = multiple(INTENDED_JAVA_MODEL, "java");
    var models = JsonParser.parseString(before).getAsJsonArray();
    models.get(1).getAsJsonObject().addProperty("type", "file");
    var parsed = IntendedModels.parse(models.toString());
    assertThat(IntendedModels.invalidReason(parsed)).isNull();
    models.get(1).getAsJsonObject().getAsJsonArray("groups").get(0).getAsJsonObject().addProperty("label", "changed");

    var diff = IntendedModels.diff(parsed, IntendedModels.parse(models.toString()));

    assertThat(diff.removed()).allMatch(value -> value.startsWith("java/file"));
    assertThat(diff.added()).anyMatch(value -> value.startsWith("java/file"));
    assertThat(diff.isEmpty()).isFalse();
    assertThat(IntendedModels.invalidReason(IntendedModels.parse(before))).contains("Perspective java/namespace appears more than once");
  }

  private static String created(String models) {
    return JsonParser.parseString(models).getAsJsonArray().get(0).toString();
  }

  private static String revision() {
    return IntendedRevision.of(IntendedModels.parse(INTENDED_JAVA_MODEL));
  }

  private static io.modelcontextprotocol.spec.McpSchema.CallToolResult set(SonarQubeMcpServerTestHarness harness, String model, boolean dryRun, String revision) {
    var args = new java.util.HashMap<String, Object>(Map.of("target", "intended", "projectKey", "my_project", "model", model, "dryRun", dryRun));
    if (revision != null) {
      args.put("revision", revision);
    }
    return harness.newClient(CLOUD_ENV).callTool(ArchitectureSetTool.TOOL_NAME, args);
  }

  private static void stored(SonarQubeMcpServerTestHarness harness, String models) {
    stubMainBranch(harness);
    harness.getMockSonarQubeServer().stubFor(get(PATH).willReturn(okJson(intendedArchitectures(models))));
  }

  private static void assertWrites(SonarQubeMcpServerTestHarness harness, int patches, int posts) {
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("DELETE", "/architecture/")).isEmpty();
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("PATCH", ArchitectureApi.INTENDED_ARCHITECTURES_PATH)).hasSize(patches);
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("POST", ArchitectureApi.INTENDED_ARCHITECTURES_PATH)).hasSize(posts);
  }

  private static void stubPatch(SonarQubeMcpServerTestHarness harness, String models) {
    harness.getMockSonarQubeServer().stubFor(patch(MODEL_PATH).inScenario("write").whenScenarioStateIs("Started")
      .willSetStateTo("saved").willReturn(okJson(intendedArchitectures(models))));
    harness.getMockSonarQubeServer().stubFor(get(PATH).inScenario("write").whenScenarioStateIs("saved")
      .willReturn(okJson(intendedArchitectures(models))));
  }

  private static void stubPost(SonarQubeMcpServerTestHarness harness, String models) {
    harness.getMockSonarQubeServer().stubFor(post(ArchitectureApi.INTENDED_ARCHITECTURES_PATH)
      .inScenario("write").whenScenarioStateIs("Started").willSetStateTo("saved")
      .willReturn(aResponse().withStatus(201).withBody(intendedArchitectures(models))));
    harness.getMockSonarQubeServer().stubFor(get(PATH).inScenario("write").whenScenarioStateIs("saved")
      .willReturn(okJson(intendedArchitectures(models))));
  }

  private static com.google.gson.JsonObject patchBody(SonarQubeMcpServerTestHarness harness) {
    return JsonParser.parseString(harness.getMockSonarQubeServer().getReceivedBodies("PATCH", MODEL_PATH).getFirst()).getAsJsonObject();
  }

  private static String multiple(String models, String otherLanguage) {
    return "[" + created(models) + "," + created(models).replace("java", otherLanguage) + "]";
  }

  private static void assertNoPrivateRequests(SonarQubeMcpServerTestHarness harness) {
    for (var path : List.of("/architecture/structure", "/architecture/intended?", "/architecture/models", "/architecture/directives", "/architecture/graphs", "/api/components/search_projects")) {
      assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining(path)).isFalse();
    }
  }

}
