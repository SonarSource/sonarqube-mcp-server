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
import org.sonarsource.sonarqube.mcp.tools.analysis.AnalyzeCodeSnippetTool;

import static org.sonarsource.sonarqube.mcp.its.sonarcloud.harness.SonarQubeMcpTestClient.assertResultEquals;

@Tag("SonarCloud")
class AnalyzeCodeSnippetSonarCloudIT extends AbstractSonarCloudStagingIT {

  @Test
  void should_call_analyze_code_snippet_against_staging() {
    assumeToolRegistered(AnalyzeCodeSnippetTool.TOOL_NAME);

    var result = mcpClient.callTool(AnalyzeCodeSnippetTool.TOOL_NAME, Map.of(
      AnalyzeCodeSnippetTool.FILE_CONTENT_PROPERTY, "",
      AnalyzeCodeSnippetTool.LANGUAGE_PROPERTY, "java",
      AnalyzeCodeSnippetTool.SCOPE_PROPERTY, "MAIN"));

    assertResultEquals(result, """
      {
        "issues" : [ ],
        "issueCount" : 0
      }""");
  }
}
