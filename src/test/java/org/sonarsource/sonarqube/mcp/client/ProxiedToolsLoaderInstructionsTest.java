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
package org.sonarsource.sonarqube.mcp.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProxiedToolsLoaderInstructionsTest {

  private static final String BASE_INSTRUCTIONS = "Base instructions for SonarQube MCP Server.";

  @Test
  void composeInstructions_should_return_base_when_no_proxied_instructions() {
    var composed = ProxiedToolsLoader.composeInstructions(BASE_INSTRUCTIONS, List.of());

    assertThat(composed).isEqualTo(BASE_INSTRUCTIONS);
  }

  @Test
  void composeInstructions_should_skip_blank_proxied_instructions() {
    var composed = ProxiedToolsLoader.composeInstructions(BASE_INSTRUCTIONS, List.of("   "));

    assertThat(composed).isEqualTo(BASE_INSTRUCTIONS);
  }

  @Test
  void composeInstructions_should_skip_null_proxied_instructions() {
    var composed = ProxiedToolsLoader.composeInstructions(BASE_INSTRUCTIONS, Collections.singletonList(null));

    assertThat(composed).isEqualTo(BASE_INSTRUCTIONS);
  }

  @Test
  void composeInstructions_should_append_single_proxied_instruction() {
    var composed = ProxiedToolsLoader.composeInstructions(BASE_INSTRUCTIONS,
      List.of("Use context_get_context before analyzing code."));

    assertThat(composed).isEqualTo(BASE_INSTRUCTIONS + "\n\nUse context_get_context before analyzing code.");
  }

  @Test
  void composeInstructions_should_append_multiple_proxied_instructions_in_order() {
    var composed = ProxiedToolsLoader.composeInstructions(BASE_INSTRUCTIONS, List.of(
      "Use context tools for code analysis.",
      "Use security tools for vulnerability scanning."
    ));

    assertThat(composed).isEqualTo(
      BASE_INSTRUCTIONS + "\n\nUse context tools for code analysis.\n\nUse security tools for vulnerability scanning.");
  }

  @Test
  void composeInstructions_should_skip_blank_entries_in_mixed_list() {
    var composed = ProxiedToolsLoader.composeInstructions(BASE_INSTRUCTIONS, Arrays.asList(
      "Provider 1 instructions.",
      null,
      "   ",
      "Provider 3 instructions."
    ));

    assertThat(composed).isEqualTo(BASE_INSTRUCTIONS + "\n\nProvider 1 instructions.\n\nProvider 3 instructions.");
  }
}
