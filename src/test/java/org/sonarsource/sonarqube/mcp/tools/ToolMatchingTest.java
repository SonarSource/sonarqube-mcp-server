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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolMatchingTest {

  @ParameterizedTest
  @CsvSource({
    "analysis;ide,analysis,true", "analysis;ide,ide,true", "analysis;ide,vortex,false",
    "analysis;cag;vortex,analysis,true", "analysis;cag;vortex,cag,true", "analysis;cag;vortex,vortex,true",
    "analysis,ide,false", "ide,analysis,false", "vortex,analysis,false", "cag,vortex,false",
    "issues,issues,true", "issues,analysis,false"
  })
  void it_should_match_only_explicit_tool_memberships(String memberships, String selected, boolean expected) {
    var categories = ToolCategory.parseCategories(memberships.replace(';', ','));
    var tool = toolWithCategories(categories.toArray(ToolCategory[]::new));

    assertThat(tool.getCategories()).containsExactlyInAnyOrderElementsOf(categories);
    assertThat(tool.matchesFilters(ToolCategory.parseCategories(selected), false)).isEqualTo(expected);
  }

  @Test
  void it_should_keep_tool_memberships_immutable() {
    var categories = toolWithCategories(ToolCategory.ANALYSIS, ToolCategory.IDE).getCategories();

    assertThatThrownBy(() -> categories.add(ToolCategory.VORTEX)).isInstanceOf(UnsupportedOperationException.class);
  }

  private static Tool toolWithCategories(ToolCategory... categories) {
    return new Tool(McpSchema.Tool.builder("test", Map.of("type", "object")).build(), categories) {
      @Override
      public Result execute(Arguments arguments) {
        return Result.failure("unused");
      }
    };
  }
}
