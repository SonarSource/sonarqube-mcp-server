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
import org.sonarsource.sonarqube.mcp.tools.Tool.Arguments;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.*;

class ArchitectureValidationTests {
  private static final String MODEL = """
    [{"type":"namespace","language":"java","groups":[
    {"label":"UI","groups":[{"label":"Shared"}]},
    {"label":"Services","groups":[{"label":"Shared"}]}],"constraints":[]}]
    """;

  @Test
  void it_should_allow_reused_labels_outside_siblings() {
    assertThat(IntendedModels.invalidReason(IntendedModels.parse(MODEL))).isNull();
    var duplicate = MODEL.replace("{\"label\":\"Shared\"}", "{\"label\":\"Shared\"},{\"label\":\"Shared\"}");
    assertThat(IntendedModels.invalidReason(IntendedModels.parse(duplicate))).contains("appears more than once");
  }

  @Test
  void it_should_validate_perspective_labels_by_language() {
    var model = JsonParser.parseString(MODEL).getAsJsonArray();
    var first = model.get(0).getAsJsonObject();
    first.addProperty("label", "Main");
    var second = first.deepCopy();
    second.addProperty("type", "file");
    model.add(second);
    assertThat(IntendedModels.invalidReason(IntendedModels.parse(model.toString()))).contains("Perspective label 'Main'");
    second.addProperty("label", "Files");
    assertThat(IntendedModels.invalidReason(IntendedModels.parse(model.toString()))).isNull();
    second.addProperty("label", "Files/Invalid");
    assertThat(IntendedModels.invalidReason(IntendedModels.parse(model.toString()))).contains("must not contain '/'");
    for (var label : List.of("", " ")) {
      first.addProperty("label", label);
      second.addProperty("label", label);
      assertThat(IntendedModels.invalidReason(IntendedModels.parse(model.toString()))).isNull();
    }
  }

  @Test
  void it_should_allow_constraints_to_an_import_but_not_its_children() {
    var model = MODEL.replace("{\"label\":\"UI\",", "{\"label\":\"UI\",\"patternId\":\"33333333-3333-4333-8333-333333333333\",")
      .replace("\"constraints\":[]", "\"constraints\":[{\"from\":\"UI\",\"to\":\"Services\"}]");
    assertThat(IntendedModels.invalidReason(IntendedModels.parse(model))).isNull();
    var descendant = model.replace("\"from\":\"UI\"", "\"from\":\"UI/Shared\"");
    assertThat(IntendedModels.invalidReason(IntendedModels.parse(descendant))).contains("does not name a unique group");
  }

  @SonarQubeMcpServerTest
  void it_should_preserve_invalid_stored_constraints_and_warn_without_marking_partial(SonarQubeMcpServerTestHarness harness) {
    stubMainBranch(harness);
    var model = MODEL.replace("\"constraints\":[]", "\"constraints\":[{\"from\":\"UI/*\",\"to\":\"Services\"}]");
    harness.getMockSonarQubeServer().stubFor(get(ArchitectureApi.INTENDED_ARCHITECTURES_PATH + "?projectId=" + MAIN_BRANCH_ID)
      .willReturn(okJson(intendedArchitectures(model))));
    var result = harness.newClient(CLOUD_ENV).callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "intended", "projectKey", "my_project"));
    assertThat(result.isError()).isFalse();
    var response = JsonParser.parseString(textOf(result)).getAsJsonObject();
    assertThat(response.has("partial")).isFalse();
    assertThat(response.get("message").getAsString()).contains("corrected before saving");
    assertThat(response.getAsJsonArray("models").get(0).getAsJsonObject().getAsJsonArray("constraints"))
      .isEqualTo(JsonParser.parseString(model).getAsJsonArray().get(0).getAsJsonObject().getAsJsonArray("constraints"));
  }

  @Test
  void it_should_confine_strict_integer_types_to_architecture() {
    for (var value : List.of(true, List.of(), Map.of())) {
      var raw = new Arguments(Map.of("depth", value), null);
      var strict = new ArchitectureArguments(raw);
      assertThat(raw.getOptionalInteger("depth")).isNull();
      assertThatThrownBy(() -> strict.getOptionalInteger("depth")).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void it_should_reject_deep_models_with_a_validation_error() {
    var group = "{\"label\":\"Group\",\"groups\":[".repeat(65) + "{\"label\":\"Leaf\"}" + "]}".repeat(65);
    var model = MODEL.replace("{\"label\":\"Shared\"}", group);
    assertThatThrownBy(() -> IntendedModels.parse(model)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("depth limit");
    var partial = "{\"models\":" + model + ",\"partial\":true}";
    assertThatThrownBy(() -> IntendedModels.parse(partial)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("partial architecture view");
  }
}
