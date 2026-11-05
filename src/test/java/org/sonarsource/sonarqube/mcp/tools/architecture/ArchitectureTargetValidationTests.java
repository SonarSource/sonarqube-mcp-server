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

import java.util.List;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTest;
import org.sonarsource.sonarqube.mcp.harness.SonarQubeMcpServerTestHarness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonarsource.sonarqube.mcp.tools.architecture.ArchitectureFixtures.textOf;

class ArchitectureTargetValidationTests {

  @SonarQubeMcpServerTest
  void it_should_reject_unknown_read_targets_before_network(SonarQubeMcpServerTestHarness harness) {
    var client = harness.newClient(ArchitectureFixtures.CLOUD_ENV);

    for (var target : List.of("patterns", "unsupported")) {
      var result = client.callTool(ArchitectureGetTool.TOOL_NAME, Map.of("target", target, "projectKey", "my_project"));

      assertThat(result.isError()).isTrue();
      assertThat(textOf(result)).contains("current", "intended");
    }
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/architecture/")).isFalse();
    assertThat(harness.getMockSonarQubeServer().hasReceivedRequestContaining("/api/project_branches/")).isFalse();
  }

}
