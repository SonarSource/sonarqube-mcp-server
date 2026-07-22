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
package org.sonarsource.sonarqube.mcp.its.sonarcloud.tools;

import java.util.Map;
import org.sonarsource.sonarqube.mcp.its.sonarcloud.AbstractSonarCloudStagingIT;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.tools.qualitygates.ProjectStatusTool;

import static org.sonarsource.sonarqube.mcp.its.sonarcloud.harness.SonarQubeMcpTestClient.assertResultEquals;

@Tag("SonarCloud")
class ProjectQualityGateSonarCloudIT extends AbstractSonarCloudStagingIT {

  @Test
  void should_call_get_project_quality_gate_status_against_staging() {
    var result = mcpClient.callTool(ProjectStatusTool.TOOL_NAME, Map.of("projectKey", fixture.projectKey()));

    // Fresh sonarlint-it IT projects on staging report no evaluated quality gate yet (see SearchIssuesSonarCloudIT for java:S1118).
    assertResultEquals(result, """
      {
        "status" : "NONE",
        "conditions" : [ ]
      }""");
  }
}
