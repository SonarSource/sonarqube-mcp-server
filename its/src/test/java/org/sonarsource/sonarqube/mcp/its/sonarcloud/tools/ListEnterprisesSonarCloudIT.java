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

import org.sonarsource.sonarqube.mcp.its.sonarcloud.AbstractSonarCloudStagingIT;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.sonarsource.sonarqube.mcp.tools.enterprises.ListEnterprisesTool;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("SonarCloud")
class ListEnterprisesSonarCloudIT extends AbstractSonarCloudStagingIT {

  @Test
  void should_call_list_enterprises_against_staging() {
    var result = mcpClient.callTool(ListEnterprisesTool.TOOL_NAME);
    var content = structuredContent(result);

    assertThat(content).containsKey("enterprises");
    assertThat(content.get("enterprises")).isInstanceOf(java.util.List.class);
  }
}
