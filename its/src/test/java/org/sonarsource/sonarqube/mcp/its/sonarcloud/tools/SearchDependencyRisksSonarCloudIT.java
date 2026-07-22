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
import org.sonarsource.sonarqube.mcp.tools.dependencyrisks.SearchDependencyRisksTool;

import static org.sonarsource.sonarqube.mcp.its.sonarcloud.harness.SonarQubeMcpTestClient.assertResultEquals;

@Tag("SonarCloud")
class SearchDependencyRisksSonarCloudIT extends AbstractSonarCloudStagingIT {

  @Test
  void should_call_search_dependency_risks_against_staging() {
    mcpClient.assertToolRegistered(SearchDependencyRisksTool.TOOL_NAME);

    var result = mcpClient.callTool(SearchDependencyRisksTool.TOOL_NAME, Map.of(
      SearchDependencyRisksTool.PROJECT_KEY_PROPERTY, fixture.projectKey(),
      "pageSize", 1));

    assertResultEquals(result, """
      {
        "issuesReleases" : [ ],
        "paging" : {
          "pageIndex" : 1,
          "pageSize" : 1,
          "total" : 0
        }
      }""");
  }
}
