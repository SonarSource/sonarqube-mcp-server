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

import java.util.Map;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureServerTests {
  @RegisterExtension
  final SonarQubeMcpServerTestHarness harness = new SonarQubeMcpServerTestHarness();

  @ParameterizedTest
  @ValueSource(strings = {"2025.1", "2026.5.4", "2026.6", "2027.1", "26.11"})
  void it_should_hide_and_reject_all_architecture_tools_on_server(String version) {
    var client = harness.newClient(Map.of("SONARQUBE_TOOLSETS", "architecture,issues", "SONARQUBE_VERSION", version));
    assertThat(client.listTools()).extracting(io.modelcontextprotocol.spec.McpSchema.Tool::name)
      .doesNotContain("architecture_get", "architecture_set").contains("search_sonar_issues_in_projects");
    for (var name : new String[] {"architecture_get", "architecture_set"}) {
      org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.callTool(name,
        Map.of("target", "intended", "projectKey", "my_project", "model", "[]", "dryRun", false)))
        .isInstanceOf(io.modelcontextprotocol.spec.McpError.class);
    }
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/architecture/")).isFalse();
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/api/v2/components/projects")).isFalse();
  }
}
