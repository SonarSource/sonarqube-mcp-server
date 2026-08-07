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
import org.sonarsource.sonarqube.mcp.tools.rules.ShowRuleTool;

import static org.sonarsource.sonarqube.mcp.its.sonarcloud.harness.SonarQubeMcpTestClient.assertStructuredContentContains;

@Tag("SonarCloud")
class ShowRuleSonarCloudIT extends AbstractSonarCloudStagingIT {

  @Test
  void should_call_show_rule_against_staging() {
    var result = mcpClient.callTool(ShowRuleTool.TOOL_NAME, Map.of("key", "java:S1541"));

    assertStructuredContentContains(result, """
      {
        "key" : "java:S1541",
        "name" : "Methods should not be too complex",
        "severity" : "CRITICAL",
        "type" : "CODE_SMELL",
        "lang" : "java",
        "langName" : "Java"
      }""");
  }
}
