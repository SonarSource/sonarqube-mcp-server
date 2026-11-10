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
package org.sonarsource.sonarqube.mcp.serverapi.architecture;

import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentGraphShapeTests {
  private static final String GRAPH = """
    {"currentArchitectures":[{"nodeCount":1,"edgeCount":1,"nodes":[
      {"id":0,"name":"a","kind":"class","edges":[{"toId":0,"weight":0}]}]}]}
    """;

  @Test
  void it_should_require_all_fields_with_the_correct_types_before_conversion() {
    for (var owner : List.of("graph", "node", "edge")) {
      var fields = switch (owner) {
        case "graph" -> List.of("nodeCount", "edgeCount", "nodes");
        case "node" -> List.of("id", "name", "kind", "edges");
        default -> List.of("toId", "weight");
      };
      for (var field : fields) {
        for (var replacement : new String[] {null, "null", "{}", "true", "1.5", "2147483648", "\"text\""}) {
          var root = JsonParser.parseString(GRAPH);
          var graph = root.getAsJsonObject().getAsJsonArray("currentArchitectures").get(0).getAsJsonObject();
          var node = graph.getAsJsonArray("nodes").get(0).getAsJsonObject();
          var target = switch (owner) {
            case "graph" -> graph;
            case "node" -> node;
            default -> node.getAsJsonArray("edges").get(0).getAsJsonObject();
          };
          if ((field.equals("name") || field.equals("kind")) && "\"text\"".equals(replacement)) {
            continue;
          }
          if (replacement == null) {
            target.remove(field);
          } else {
            target.add(field, JsonParser.parseString(replacement));
          }
          assertThatThrownBy(() -> CurrentGraphShape.validate(root)).as("%s.%s=%s", owner, field, replacement).isInstanceOf(RuntimeException.class);
        }
      }
    }
  }

  @Test
  void it_should_accept_explicit_zero_integers_empty_arrays_and_optional_children() {
    var nonempty = JsonParser.parseString(GRAPH);
    assertThatCode(() -> CurrentGraphShape.validate(nonempty)).doesNotThrowAnyException();
    var empty = JsonParser.parseString("{\"currentArchitectures\":[{\"nodeCount\":0,\"edgeCount\":0,\"nodes\":[]}]}");
    assertThatCode(() -> CurrentGraphShape.validate(empty)).doesNotThrowAnyException();
    var node = nonempty.getAsJsonObject().getAsJsonArray("currentArchitectures").get(0).getAsJsonObject().getAsJsonArray("nodes").get(0).getAsJsonObject();
    node.add("nodes", JsonNull.INSTANCE);
    assertThatThrownBy(() -> CurrentGraphShape.validate(nonempty)).isInstanceOf(IllegalStateException.class);
  }
}
