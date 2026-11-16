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
package org.sonarsource.sonarqube.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolRegistrarTest {

  @Test
  void it_should_accept_selected_tools_in_registration_order() {
    var registrar = new ToolRegistrar(Set.of(ToolCategory.ANALYSIS), false);
    var project = tool("project", ToolCategory.PROJECTS, null);
    var vortex = tool("vortex", ToolCategory.VORTEX, null);
    var ide = tool("ide", ToolCategory.IDE, null);
    var rejected = tool("issues", ToolCategory.ISSUES, null);

    assertThat(registrar.register(rejected)).isFalse();
    assertThat(registrar.register(project)).isTrue();
    assertThat(registrar.registerAll(List.of(vortex, rejected, ide))).isTrue();
    assertThat(registrar.getRegisteredTools()).containsExactly(project, vortex, ide);
    assertThat(registrar.registerAll(List.of(rejected))).isFalse();
    assertThat(registrar.registerAll(List.of())).isFalse();
  }

  @Test
  void it_should_require_an_explicit_read_only_hint_including_projects() {
    var registrar = new ToolRegistrar(Set.of(), true);
    var noAnnotations = tool("no_annotations", ToolCategory.PROJECTS, null);
    var emptyAnnotations = tool("empty_annotations", ToolCategory.PROJECTS, new McpSchema.ToolAnnotations(null, null, null, null, null, null));
    var falseHint = tool("false_hint", ToolCategory.PROJECTS, new McpSchema.ToolAnnotations(null, false, null, null, null, null));
    var trueHint = tool("true_hint", ToolCategory.PROJECTS, new McpSchema.ToolAnnotations(null, true, null, null, null, null));

    assertThat(registrar.registerAll(List.of(noAnnotations, emptyAnnotations, falseHint, trueHint))).isTrue();
    assertThat(registrar.getRegisteredTools()).containsExactly(trueHint);
  }

  @Test
  void it_should_return_immutable_independent_snapshots() {
    var registrar = new ToolRegistrar(Set.of(ToolCategory.PROJECTS), false);
    var first = tool("first", ToolCategory.PROJECTS, null);
    var second = tool("second", ToolCategory.PROJECTS, null);
    registrar.register(first);
    var snapshot = registrar.getRegisteredTools();
    registrar.register(second);

    assertThat(snapshot).containsExactly(first);
    assertThat(registrar.getRegisteredTools()).containsExactly(first, second);
    assertThatThrownBy(() -> snapshot.add(second)).isInstanceOf(UnsupportedOperationException.class);
  }

  private static Tool tool(String name, ToolCategory category, @Nullable McpSchema.ToolAnnotations annotations) {
    var definition = McpSchema.Tool.builder(name, Map.of("type", "object")).annotations(annotations).build();
    return new Tool(definition, category) {
      @Override
      public Result execute(Arguments arguments) {
        return Result.failure("unused");
      }
    };
  }
}
