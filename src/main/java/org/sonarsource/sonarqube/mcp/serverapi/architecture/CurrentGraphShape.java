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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jakarta.annotation.Nullable;
import java.util.ArrayDeque;

/** Checks required wire fields before Gson can substitute zero for absent primitive values. */
final class CurrentGraphShape {
  private static final String NODES_PROPERTY = "nodes";

  private CurrentGraphShape() {
  }

  static void validate(JsonElement response) {
    var graphs = array(object(response), "currentArchitectures");
    for (var value : graphs) {
      var graph = object(value);
      integer(graph, "nodeCount");
      integer(graph, "edgeCount");
      var pending = new ArrayDeque<JsonElement>(array(graph, NODES_PROPERTY).asList());
      while (!pending.isEmpty()) {
        var node = object(pending.removeLast());
        integer(node, "id");
        string(node, "name");
        string(node, "kind");
        for (var edge : array(node, "edges")) {
          integer(object(edge), "toId");
          integer(object(edge), "weight");
        }
        if (node.has(NODES_PROPERTY)) {
          pending.addAll(array(node, NODES_PROPERTY).asList());
        }
      }
    }
  }

  private static JsonObject object(@Nullable JsonElement value) {
    if (value == null || !value.isJsonObject()) {
      throw invalid();
    }
    return value.getAsJsonObject();
  }

  private static JsonArray array(JsonObject object, String name) {
    var value = object.get(name);
    if (value == null || !value.isJsonArray()) {
      throw invalid();
    }
    return value.getAsJsonArray();
  }

  private static int integer(JsonObject object, String name) {
    var value = object.get(name);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
      throw invalid();
    }
    return value.getAsBigDecimal().intValueExact();
  }

  private static void string(JsonObject object, String name) {
    var value = object.get(name);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
      throw invalid();
    }
  }

  private static IllegalStateException invalid() {
    return new IllegalStateException("Current architecture is missing a required field or contains an invalid field type.");
  }
}
