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
import com.google.gson.JsonParser;
import com.google.gson.JsonNull;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.serverapi.architecture.response.ProjectFinding;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureFindingDtoTests {
  private final Gson gson = new Gson();

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
}
