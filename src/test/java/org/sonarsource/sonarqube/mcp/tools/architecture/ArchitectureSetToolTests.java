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
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
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

    harness.getMockSonarQubeServer().stubFor(delete(DELETE_PATH).inScenario("constraint-edit").whenScenarioStateIs("Started")
      .willSetStateTo("deleted").willReturn(aResponse().withStatus(204)));
    harness.getMockSonarQubeServer().stubFor(get(PATH).inScenario("constraint-edit").whenScenarioStateIs("deleted")
      .willReturn(okJson(intendedArchitectures("[]"))));
    harness.getMockSonarQubeServer().stubFor(post(PATH).willReturn(aResponse().withStatus(201).withBody(created(next))));

    var applied = set(harness, next, false, revision);
    assertThat(applied.isError()).withFailMessage(textOf(applied)).isFalse();
    assertThat(textOf(applied)).contains("replaced").doesNotContain("unchanged");
    assertWrites(harness, 1, 1);
    var body = JsonParser.parseString(harness.getMockSonarQubeServer().getReceivedBodies("POST", PATH).getFirst()).getAsJsonObject();
    assertThat(body.getAsJsonArray("constraints")).isEqualTo(JsonParser.parseString(next).getAsJsonArray().get(0).getAsJsonObject().get("constraints"));
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
  private static final String DELETE_PATH = ArchitectureApi.INTENDED_ARCHITECTURES_PATH + "/11111111-1111-4111-8111-111111111111";
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
    assertThat(properties.get("target")).containsEntry("enum", List.of("intended"));
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
  void it_should_warn_about_flattening_in_a_dry_run_without_writes(SonarQubeMcpServerTestHarness harness) {
    stored(harness, INTENDED_JAVA_MODEL);
    var result = set(harness, NEXT, true, null);
    assertThat(result.isError()).isFalse();
    assertThat(textOf(result)).contains("dry-run", "java group app/domain", "java group app/infra", "import links", "descriptions");
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
  void it_should_replace_using_only_public_delete_and_post(SonarQubeMcpServerTestHarness harness) {
    stored(harness, INTENDED_JAVA_MODEL);
    harness.getMockSonarQubeServer().stubFor(delete(DELETE_PATH).inScenario("replace").whenScenarioStateIs("Started").willSetStateTo("deleted").willReturn(aResponse().withStatus(204)));
    harness.getMockSonarQubeServer().stubFor(get(PATH).inScenario("replace").whenScenarioStateIs("deleted").willReturn(okJson(intendedArchitectures("[]"))));
    harness.getMockSonarQubeServer().stubFor(post(PATH).willReturn(aResponse().withStatus(201).withBody(created(NEXT))));
    var result = set(harness, NEXT, false, revision());
    assertThat(result.isError()).isFalse();
    assertThat(textOf(result)).contains("replaced", "import links");
    assertWrites(harness, 1, 1);
    var body = harness.getMockSonarQubeServer().getReceivedBodies("POST", PATH).getFirst();
    var request = JsonParser.parseString(body).getAsJsonObject();
    assertThat(request.has("groups")).isTrue();
    assertThat(request.has("constraints")).isTrue();
    assertThat(request.has("id")).isFalse();
    assertThat(request.has("intendedArchitectures")).isFalse();
    assertThat(body).contains("infra").doesNotContain("perspectives", "projectId");
    assertNoPrivateRequests(harness);
  }

  @SonarQubeMcpServerTest
  void it_should_abort_after_one_delete_if_another_model_becomes_visible(SonarQubeMcpServerTestHarness harness) {
    stored(harness, INTENDED_JAVA_MODEL);
    harness.getMockSonarQubeServer().stubFor(delete(DELETE_PATH).inScenario("replace").whenScenarioStateIs("Started").willSetStateTo("survivor").willReturn(aResponse().withStatus(204)));
    harness.getMockSonarQubeServer().stubFor(get(PATH).inScenario("replace").whenScenarioStateIs("survivor").willReturn(okJson(intendedArchitectures(NEXT))));
    var result = set(harness, NEXT, false, revision());
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("surviving data", "Previous exported model", "domain");
    assertWrites(harness, 1, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_not_duplicate_a_model_when_delete_reports_success_without_removing_it(SonarQubeMcpServerTestHarness harness) {
    stored(harness, INTENDED_JAVA_MODEL);
    harness.getMockSonarQubeServer().stubFor(delete(DELETE_PATH).willReturn(aResponse().withStatus(204)));
    var result = set(harness, NEXT, false, revision());
    assertThat(result.isError()).isTrue();
    assertWrites(harness, 1, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_create_directly_when_the_export_is_empty(SonarQubeMcpServerTestHarness harness) {
    stored(harness, "[]");
    harness.getMockSonarQubeServer().stubFor(delete(DELETE_PATH).willReturn(aResponse().withStatus(404)));
    harness.getMockSonarQubeServer().stubFor(post(PATH).willReturn(aResponse().withStatus(201).withBody(created(NEXT))));
    var result = set(harness, NEXT, false, IntendedRevision.of(List.of()));
    assertThat(result.isError()).isFalse();
    assertThat(textOf(result)).contains("created");
    assertWrites(harness, 0, 1);
  }

  @SonarQubeMcpServerTest
  void it_should_preserve_an_existing_container_hidden_by_an_empty_public_export(SonarQubeMcpServerTestHarness harness) {
    stored(harness, "[]");
    harness.getMockSonarQubeServer().stubFor(post(PATH).willReturn(aResponse().withStatus(400).withBody("{\"message\":\"A model already exists\"}")));
    var result = set(harness, NEXT, false, IntendedRevision.of(List.of()));
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("No previous exported model exists", "A model already exists");
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
    assertThat(textOf(result)).contains("dry-run", "java group UI/API hierarchy", "patterns [app:**] -> patterns [api:**], interface, interfaceAccess");
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
    harness.getMockSonarQubeServer().stubFor(delete(DELETE_PATH).inScenario("hierarchy").whenScenarioStateIs("Started")
      .willSetStateTo("deleted").willReturn(aResponse().withStatus(204)));
    harness.getMockSonarQubeServer().stubFor(get(PATH).inScenario("hierarchy").whenScenarioStateIs("deleted")
      .willReturn(okJson(intendedArchitectures("[]"))));
    harness.getMockSonarQubeServer().stubFor(post(PATH).willReturn(aResponse().withStatus(201).withBody(created(after))));

    var result = set(harness, after, false, IntendedRevision.of(IntendedModels.parse(before)));

    assertThat(result.isError()).as(textOf(result)).isFalse();
    assertThat(textOf(result)).contains("replaced", "java group UI/API hierarchy").doesNotContain("unchanged");
    assertWrites(harness, 1, 1);
    var expected = JsonParser.parseString(created(after)).getAsJsonObject();
    expected.remove("id");
    assertThat(JsonParser.parseString(harness.getMockSonarQubeServer().getReceivedBodies("POST", PATH).getFirst())).isEqualTo(expected);
  }

  @SonarQubeMcpServerTest
  void it_should_not_write_for_an_unchanged_model(SonarQubeMcpServerTestHarness harness) {
    stored(harness, INTENDED_JAVA_MODEL);
    assertThat(textOf(set(harness, INTENDED_JAVA_MODEL, false, revision()))).contains("unchanged");
    assertWrites(harness, 0, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_restore_only_after_verifying_empty_and_verify_the_export(SonarQubeMcpServerTestHarness harness) {
    stored(harness, INTENDED_JAVA_MODEL);
    harness.getMockSonarQubeServer().stubFor(delete(DELETE_PATH).inScenario("replace").whenScenarioStateIs("Started").willSetStateTo("deleted").willReturn(aResponse().withStatus(204)));
    harness.getMockSonarQubeServer().stubFor(get(PATH).inScenario("replace").whenScenarioStateIs("deleted").willReturn(okJson(intendedArchitectures("[]"))));
    harness.getMockSonarQubeServer().stubFor(post(PATH).withRequestBody(containing("infra")).willReturn(aResponse().withStatus(400)));
    harness.getMockSonarQubeServer().stubFor(post(PATH).withRequestBody(containing("domain")).inScenario("replace").whenScenarioStateIs("deleted")
      .willSetStateTo("restored").willReturn(aResponse().withStatus(201).withBody(created(INTENDED_JAVA_MODEL))));
    harness.getMockSonarQubeServer().stubFor(get(PATH).inScenario("replace").whenScenarioStateIs("restored").willReturn(okJson(intendedArchitectures(INTENDED_JAVA_MODEL))));
    var result = set(harness, NEXT, false, revision());
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("recreated and verified", "import links");
    assertWrites(harness, 1, 2);
  }

  @SonarQubeMcpServerTest
  void it_should_not_restore_beside_a_committed_new_model_after_a_failed_response(SonarQubeMcpServerTestHarness harness) {
    stored(harness, INTENDED_JAVA_MODEL);
    harness.getMockSonarQubeServer().stubFor(delete(DELETE_PATH).inScenario("replace").whenScenarioStateIs("Started").willSetStateTo("deleted").willReturn(aResponse().withStatus(204)));
    harness.getMockSonarQubeServer().stubFor(get(PATH).inScenario("replace").whenScenarioStateIs("deleted").willReturn(okJson(intendedArchitectures("[]"))));
    harness.getMockSonarQubeServer().stubFor(post(PATH).inScenario("replace").whenScenarioStateIs("deleted").willSetStateTo("committed").willReturn(aResponse().withStatus(500)));
    harness.getMockSonarQubeServer().stubFor(get(PATH).inScenario("replace").whenScenarioStateIs("committed").willReturn(okJson(intendedArchitectures(NEXT))));
    var result = set(harness, NEXT, false, revision());
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("requested exported model is present", "No restoration");
    assertWrites(harness, 1, 1);
  }

  @SonarQubeMcpServerTest
  void it_should_not_restore_when_delete_state_cannot_be_read(SonarQubeMcpServerTestHarness harness) {
    stored(harness, INTENDED_JAVA_MODEL);
    harness.getMockSonarQubeServer().stubFor(delete(DELETE_PATH).inScenario("replace").whenScenarioStateIs("Started").willSetStateTo("unknown").willReturn(aResponse().withStatus(500)));
    harness.getMockSonarQubeServer().stubFor(get(PATH).inScenario("replace").whenScenarioStateIs("unknown").willReturn(aResponse().withStatus(500)));
    var result = set(harness, NEXT, false, revision());
    assertThat(textOf(result)).contains("could not be read", "No automatic restoration", "Previous exported model");
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
    for (var target : List.of("directives", "current", "unsupported")) {
      var result = client.callTool(ArchitectureSetTool.TOOL_NAME, Map.of("target", target, "projectKey", "my_project", "dryRun", false));
      assertThat(result.isError()).isTrue();
      assertThat(textOf(result)).contains("intended");
    }
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/architecture/")).isFalse();
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/api/project_branches/")).isFalse();
  }

  @SonarQubeMcpServerTest
  void it_should_reject_server_intended_writes_before_private_id_discovery(SonarQubeMcpServerTestHarness harness) {
    var result = harness.newClient().callTool(ArchitectureSetTool.TOOL_NAME, Map.of("target", "intended", "projectKey", "my_project", "model", NEXT, "dryRun", true));
    assertThat(textOf(result)).contains("no supported public API resolves it");
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
      var models = JsonParser.parseString(textOf(read)).getAsJsonObject().getAsJsonArray("models");
      assertThat(models.get(0).getAsJsonObject().get("partial").getAsBoolean()).isTrue();
      assertThat(textOf(set(harness, models.toString(), false, revision()))).contains("partial");
    }
    assertWrites(harness, 0, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_reconcile_an_initial_create_committed_before_a_failed_response(SonarQubeMcpServerTestHarness harness) {
    stored(harness, "[]");
    harness.getMockSonarQubeServer().stubFor(delete(DELETE_PATH).willReturn(aResponse().withStatus(404)));
    harness.getMockSonarQubeServer().stubFor(post(PATH).inScenario("create").whenScenarioStateIs("Started").willSetStateTo("committed").willReturn(aResponse().withStatus(500)));
    harness.getMockSonarQubeServer().stubFor(get(PATH).inScenario("create").whenScenarioStateIs("committed").willReturn(okJson(intendedArchitectures(NEXT))));
    var result = set(harness, NEXT, false, IntendedRevision.of(List.of()));
    assertThat(textOf(result)).contains("requested exported model is present", "No restoration");
    assertWrites(harness, 0, 1);
  }

  @SonarQubeMcpServerTest
  void it_should_report_restore_failure_with_backup_without_claiming_success(SonarQubeMcpServerTestHarness harness) {
    stored(harness, INTENDED_JAVA_MODEL);
    harness.getMockSonarQubeServer().stubFor(delete(DELETE_PATH).inScenario("replace").whenScenarioStateIs("Started").willSetStateTo("deleted").willReturn(aResponse().withStatus(204)));
    harness.getMockSonarQubeServer().stubFor(get(PATH).inScenario("replace").whenScenarioStateIs("deleted").willReturn(okJson(intendedArchitectures("[]"))));
    harness.getMockSonarQubeServer().stubFor(post(PATH).willReturn(aResponse().withStatus(500)));
    var result = set(harness, NEXT, false, revision());
    assertThat(textOf(result)).contains("restoration could not be verified", "Previous exported model", "domain").doesNotContain("recreated and verified");
    assertWrites(harness, 1, 2);
  }

  @SonarQubeMcpServerTest
  void it_should_return_recovery_data_to_the_caller_without_logging_it(SonarQubeMcpServerTestHarness harness) {
    var sentinel = "private-recovery-pattern-sentinel";
    var previous = INTENDED_JAVA_MODEL.replace("app:com.acme.domain.**", sentinel);
    stored(harness, previous);
    harness.getMockSonarQubeServer().stubFor(delete(DELETE_PATH).inScenario("privacy").whenScenarioStateIs("Started")
      .willSetStateTo("survivor").willReturn(aResponse().withStatus(204)));
    harness.getMockSonarQubeServer().stubFor(get(PATH).inScenario("privacy").whenScenarioStateIs("survivor")
      .willReturn(okJson(intendedArchitectures(NEXT))));
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
  void it_should_refuse_multi_perspective_changes_before_dry_run_or_deletion(SonarQubeMcpServerTestHarness harness) {
    var multiple = "[" + created(INTENDED_JAVA_MODEL) + "," + created(INTENDED_JAVA_MODEL).replace("java", "ts") + "]";
    stored(harness, multiple);
    assertThat(textOf(set(harness, NEXT, true, null))).contains("only one intended architecture perspective");
    assertThat(textOf(set(harness, NEXT, false, null))).contains("Nothing was changed");
    assertWrites(harness, 0, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_refuse_missing_or_invalid_server_id_before_deletion(SonarQubeMcpServerTestHarness harness) {
    stored(harness, INTENDED_JAVA_MODEL.replace("11111111-1111-4111-8111-111111111111", "unknown/id"));
    assertThat(textOf(set(harness, NEXT, false, null))).contains("No unknown resource will be deleted");
    assertWrites(harness, 0, 0);
  }

  @SonarQubeMcpServerTest
  void it_should_not_claim_success_for_a_malformed_created_resource(SonarQubeMcpServerTestHarness harness) {
    stored(harness, "[]");
    harness.getMockSonarQubeServer().stubFor(post(PATH).willReturn(aResponse().withStatus(201).withBody("{}")));
    var result = set(harness, NEXT, false, null);
    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("No unknown resource will be deleted");
    assertWrites(harness, 0, 1);
  }

  @SonarQubeMcpServerTest
  void it_should_refuse_creating_multiple_perspectives_without_mutation(SonarQubeMcpServerTestHarness harness) {
    stored(harness, "[]");
    var multiple = "[" + created(NEXT) + "," + created(NEXT).replace("java", "ts") + "]";
    assertThat(textOf(set(harness, multiple, true, null))).contains("only one intended architecture perspective");
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

  private static void assertWrites(SonarQubeMcpServerTestHarness harness, int deletes, int posts) {
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("DELETE", DELETE_PATH)).hasSize(deletes);
    assertThat(harness.getMockSonarQubeServer().getReceivedBodies("POST", PATH)).hasSize(posts);
  }

  private static void assertNoPrivateRequests(SonarQubeMcpServerTestHarness harness) {
    for (var path : List.of("/architecture/structure", "/architecture/intended?", "/architecture/models", "/architecture/directives", "/architecture/graphs", "/api/components/search_projects")) {
      assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining(path)).isFalse();
    }
  }

}
