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
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.textOf;

class ArchitectureGetDirectivesToolTests {

  @SonarQubeMcpServerTest
  void it_should_say_directives_are_not_available_without_calling_sonarqube(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient();

    var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "directives", "projectKey", "my_project"));

    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).isEqualTo("Directives are not available yet: waiting for the public architecture API. Nothing was read.");
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/architecture/")).isFalse();
  }

  @SonarQubeMcpServerTest
  void it_should_refuse_current_and_intended_parameters(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient();

    var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", "directives", "projectKey", "my_project", "depth", 2));

    assertThat(result.isError()).isTrue();
    assertThat(textOf(result)).contains("component, depth and language apply to target current and intended, not directives.");
  }

}
