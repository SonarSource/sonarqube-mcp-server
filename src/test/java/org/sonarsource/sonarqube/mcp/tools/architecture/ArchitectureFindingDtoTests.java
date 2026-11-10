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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.JsonNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectFinding;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectFindingDeserializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchitectureFindingDtoTests {
  private final Gson gson = new GsonBuilder().registerTypeAdapter(ProjectFinding.class, new ProjectFindingDeserializer()).create();

  @ParameterizedTest
  @ValueSource(strings = {
    "{\"type\":\"structuralDeviation\",\"category\":\"deviation\",\"language\":\"java\""
      + ",\"details\":{\"reason\":\"misplacedComponent\",\"nodeKey\":\"app:web\",\"misplacedComponentCount\":2}}",
    "{\"type\":\"relationshipDeviation\",\"category\":\"deviation\",\"language\":\"java\""
      + ",\"details\":{\"reason\":\"disallowedDependency\",\"fromNodeKey\":\"app:web\",\"toNodeKey\":\"app:db\",\"dependencyCount\":3}}",
    "{\"type\":\"tangle\",\"category\":\"flaw\",\"language\":\"java\",\"details\":{\"nodeKeys\":[\"a\",\"b\"],\"nodeCount\":2}}",
    "{\"type\":\"weakTangle\",\"category\":\"smell\",\"language\":\"ts\",\"details\":{\"nodeKeys\":[\"a\",\"b\"],\"nodeCount\":2}}",
    "{\"type\":\"oversizedComponent\",\"category\":\"flaw\",\"language\":\"java\",\"details\":{\"nodeKey\":\"app\",\"leafArtifactCount\":10}}",
    "{\"type\":\"splitResponsibility\",\"category\":\"smell\",\"language\":\"ts\",\"details\":{\"nodeKey\":\"app\",\"fragmentCount\":3}}"
  })
  void it_should_deserialize_the_public_contract_directly(String json) {
    var expected = JsonParser.parseString(json).getAsJsonObject();
    expected.add("files", JsonParser.parseString("[\"src/App.java\"]"));
    var payload = expected.deepCopy();
    var details = payload.getAsJsonObject("details");
    for (var field : new String[] {"reason", "nodeKey", "fromNodeKey", "toNodeKey", "nodeKeys", "nodeCount", "misplacedComponentCount",
      "dependencyCount", "leafArtifactCount", "fragmentCount"}) {
      if (!details.has(field)) {
        details.add(field, JsonNull.INSTANCE);
      }
    }
    var response = gson.fromJson(payload, ProjectFinding.class);
    assertThat(gson.toJsonTree(response)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"structuralDeviation,interfaceViolation", "relationshipDeviation,misplacedComponent"})
  void it_should_reject_a_known_reason_for_a_different_finding_type(String type, String reason) {
    var json = """
      {"type":"%s","category":"deviation","language":"java",
        "details":{"reason":"%s","nodeKey":"app:web","fromNodeKey":"app:web","toNodeKey":"app:db"},"files":[]}
      """.formatted(type, reason);
    assertThatThrownBy(() -> gson.fromJson(json, ProjectFinding.class))
      .hasRootCauseInstanceOf(IllegalStateException.class).hasRootCauseMessage("Invalid public finding reason for type.");
  }

  @ParameterizedTest
  @ValueSource(strings = {"structuralDeviation", "relationshipDeviation"})
  void it_should_preserve_metadata_for_an_unsupported_reason_without_decoding_details(String type) {
    var json = """
      {"type":"%s","category":"deviation","language":"java",
        "details":{"reason":"futureReason","nodeKey":{},"fromNodeKey":{}},"files":{}}
      """.formatted(type);
    var finding = gson.fromJson(json, ProjectFinding.class);
    assertThat(finding.supported()).isFalse();
    assertThat(finding).extracting(ProjectFinding::type, ProjectFinding::category, ProjectFinding::language)
      .containsExactly(type, "deviation", "java");
  }

  @ParameterizedTest
  @ValueSource(strings = {"2.5", "-1", "2147483648", "1e30"})
  void it_should_reject_non_integer_or_out_of_range_counts(String count) {
    var json = "{\"type\":\"tangle\",\"category\":\"flaw\",\"language\":\"java\","
      + "\"details\":{\"nodeKeys\":[],\"nodeCount\":" + count + "},\"files\":[]}";
    assertThatThrownBy(() -> gson.fromJson(json, ProjectFinding.class)).isInstanceOf(RuntimeException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"2", "2.0", "2e0"})
  void it_should_preserve_integer_count_output(String count) {
    var json = "{\"type\":\"tangle\",\"category\":\"flaw\",\"language\":\"java\","
      + "\"details\":{\"nodeKeys\":[],\"nodeCount\":" + count + "},\"files\":[]}";
    var finding = gson.fromJson(json, ProjectFinding.class);
    assertThat(gson.toJson(finding)).contains("\"nodeCount\":2}");
  }

}
