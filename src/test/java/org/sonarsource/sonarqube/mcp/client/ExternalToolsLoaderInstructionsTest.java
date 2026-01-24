/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalToolsLoaderInstructionsTest {

  private static final String BASE_INSTRUCTIONS = "Base instructions for SonarQube MCP Server.";

  @Test
  void composeInstructions_should_return_base_when_no_providers() {
    var composed = ExternalToolsLoader.composeInstructions(BASE_INSTRUCTIONS, List.of());

    assertThat(composed).isEqualTo(BASE_INSTRUCTIONS);
  }

  @Test
  void composeInstructions_should_return_base_when_provider_has_no_instructions() {
    var config = new ExternalMcpServerConfig("test", "test", "cmd", List.of(), Map.of(), Set.of(TransportMode.STDIO), null);

    var composed = ExternalToolsLoader.composeInstructions(BASE_INSTRUCTIONS, List.of(config));

    assertThat(composed).isEqualTo(BASE_INSTRUCTIONS);
  }

  @Test
  void composeInstructions_should_return_base_when_provider_has_blank_instructions() {
    var config = new ExternalMcpServerConfig("test", "test", "cmd", List.of(), Map.of(), Set.of(TransportMode.STDIO), "   ");

    var composed = ExternalToolsLoader.composeInstructions(BASE_INSTRUCTIONS, List.of(config));

    assertThat(composed).isEqualTo(BASE_INSTRUCTIONS);
  }

  @Test
  void composeInstructions_should_append_provider_instructions() {
    var config = new ExternalMcpServerConfig("code-context", "context", "cmd", List.of(), Map.of(), Set.of(TransportMode.STDIO), 
      "Use context_get_context before analyzing code.");

    var composed = ExternalToolsLoader.composeInstructions(BASE_INSTRUCTIONS, List.of(config));

    assertThat(composed)
      .contains(BASE_INSTRUCTIONS)
      .contains("## External Tool Providers")
      .contains("### code-context (context_*)")
      .contains("Use context_get_context before analyzing code.");
  }

  @Test
  void composeInstructions_should_append_multiple_providers() {
    var config1 = new ExternalMcpServerConfig("code-context", "context", "cmd", List.of(), Map.of(), Set.of(TransportMode.STDIO), 
      "Use context tools for code analysis.");
    var config2 = new ExternalMcpServerConfig("security", "sec", "cmd", List.of(), Map.of(), Set.of(TransportMode.STDIO), 
      "Use security tools for vulnerability scanning.");

    var composed = ExternalToolsLoader.composeInstructions(BASE_INSTRUCTIONS, List.of(config1, config2));

    assertThat(composed)
      .contains("### code-context (context_*)")
      .contains("Use context tools for code analysis.")
      .contains("### security (sec_*)")
      .contains("Use security tools for vulnerability scanning.");
  }

  @Test
  void composeInstructions_should_skip_providers_without_instructions() {
    var config1 = new ExternalMcpServerConfig("provider1", "p1", "cmd", List.of(), Map.of(), Set.of(TransportMode.STDIO), 
      "Provider 1 instructions.");
    var config2 = new ExternalMcpServerConfig("provider2", "p2", "cmd", List.of(), Map.of(), Set.of(TransportMode.STDIO), null);
    var config3 = new ExternalMcpServerConfig("provider3", "p3", "cmd", List.of(), Map.of(), Set.of(TransportMode.STDIO), 
      "Provider 3 instructions.");

    var composed = ExternalToolsLoader.composeInstructions(BASE_INSTRUCTIONS, List.of(config1, config2, config3));

    assertThat(composed)
      .contains("### provider1 (p1_*)")
      .contains("Provider 1 instructions.")
      .doesNotContain("provider2")
      .contains("### provider3 (p3_*)")
      .contains("Provider 3 instructions.");
  }

}
