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
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class ToolOAuthScopeTest {

  @ParameterizedTest
  @MethodSource("scopeRequirements")
  void it_should_derive_default_scopes_from_the_read_only_hint(McpSchema.ToolAnnotations annotations, String expectedScope) {
    var definition = McpSchema.Tool.builder("scope_test", Map.of("type", "object")).annotations(annotations).build();
    var tool = new Tool(definition, ToolCategory.ISSUES) {
      @Override
      public Result execute(Tool.Arguments arguments) {
        return Result.failure("unused");
      }
    };

    assertThat(tool.requiredOAuthScopes()).containsExactly(expectedScope);
  }

  private static Stream<Arguments> scopeRequirements() {
    return Stream.of(
      Arguments.of(null, "write:all"),
      Arguments.of(new McpSchema.ToolAnnotations(null, null, null, null, null, null), "write:all"),
      Arguments.of(new McpSchema.ToolAnnotations(null, false, null, null, null, null), "write:all"),
      Arguments.of(new McpSchema.ToolAnnotations(null, true, null, null, null, null), "read:all"));
  }
}
