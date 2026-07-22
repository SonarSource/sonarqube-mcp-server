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
import org.sonarsource.sonarqube.mcp.tools.sources.GetFileCoverageDetailsTool;

import static org.sonarsource.sonarqube.mcp.its.sonarcloud.harness.SonarQubeMcpTestClient.assertStructuredContentContains;

@Tag("SonarCloud")
class GetFileCoverageDetailsSonarCloudIT extends AbstractSonarCloudStagingIT {
  @Test
  void should_call_get_file_coverage_details_against_staging() {
    var result = mcpClient.callTool(GetFileCoverageDetailsTool.TOOL_NAME, Map.of(
      GetFileCoverageDetailsTool.KEY_PROPERTY, fixture.mainFileKey()));

    assertStructuredContentContains(result, """
      {
        "fileKey" : "%s",
        "summary" : {
          "coverableLines" : 1,
          "coveredLines" : 0
        }
      }""".formatted(fixture.mainFileKey()));
  }
}
