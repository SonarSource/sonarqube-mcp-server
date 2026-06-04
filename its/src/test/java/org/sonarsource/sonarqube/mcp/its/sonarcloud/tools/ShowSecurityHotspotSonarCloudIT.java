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
import org.sonarsource.sonarqube.mcp.tools.hotspots.SearchSecurityHotspotsTool;
import org.sonarsource.sonarqube.mcp.tools.hotspots.ShowSecurityHotspotTool;

import static org.sonarsource.sonarqube.mcp.its.sonarcloud.harness.SonarQubeMcpTestClient.assertStructuredContentContains;

@Tag("SonarCloud")
class ShowSecurityHotspotSonarCloudIT extends AbstractSonarCloudStagingIT {

  @Test
  void should_call_show_security_hotspot_against_staging() {
    var searchResult = mcpClient.callTool(SearchSecurityHotspotsTool.TOOL_NAME, Map.of(
      SearchSecurityHotspotsTool.PROJECT_KEY_PROPERTY, fixture.projectKey(),
      "ps", 1));
    var searchContent = structuredContent(searchResult);
    @SuppressWarnings("unchecked")
    var hotspots = (java.util.List<Map<String, Object>>) searchContent.get("hotspots");
    var hotspotKey = (String) hotspots.getFirst().get("key");

    var result = mcpClient.callTool(ShowSecurityHotspotTool.TOOL_NAME, Map.of(
      ShowSecurityHotspotTool.HOTSPOT_KEY_PROPERTY, hotspotKey));

    assertStructuredContentContains(result, """
      {
        "status" : "TO_REVIEW",
        "rule" : {
          "key" : "java:S4792"
        }
      }""");
  }
}
