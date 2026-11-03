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

import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public final class ArchitectureTools {

  private ArchitectureTools() {
  }

  public static List<Tool> create(ServerApiProvider provider, boolean isSonarQubeCloud, @Nullable String configuredProjectKey) {
    var tools = new ArrayList<Tool>();
    tools.add(new ArchitectureGetTool(provider, isSonarQubeCloud, configuredProjectKey));
    if (isSonarQubeCloud) {
      tools.add(new ArchitectureSetTool(provider, true, configuredProjectKey));
    }
    return tools;
  }
}
