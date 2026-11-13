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

  public static final String INSTRUCTIONS = """

    ## Architecture
    Before your first code edit in a task, call architecture_get with target intended. \
    The project can have an intended architecture in SonarQube that says which parts of the code may depend on which.
    1. Before you add a dependency between folders, packages or modules, or create a new one, call architecture_get \
    with target intended and follow its constraints. A dependency that no constraint allows is a deviation. \
    Call it again with component set to each group you change to get only the constraints that involve it.
    2. Before you finish a code change, compare every dependency you added with those constraints and fix any that are not allowed.
    Do not change the intended architecture unless the user asks.""";

  private ArchitectureTools() {
  }

  public static List<Tool> create(ServerApiProvider provider, boolean isSonarQubeCloud, @Nullable String configuredProjectKey) {
    var tools = new ArrayList<Tool>();
    var currentReads = new CurrentGraphReads();
    tools.add(new ArchitectureGetTool(provider, isSonarQubeCloud, configuredProjectKey, currentReads));
    tools.add(new ArchitectureSetTool(provider, isSonarQubeCloud, configuredProjectKey, currentReads));
    return tools;
  }
}
