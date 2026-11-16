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
package org.sonarsource.sonarqube.mcp.tools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class ToolRegistrar {

  private final Set<ToolCategory> selectedToolsets;
  private final boolean readOnly;
  private final List<Tool> registeredTools = new ArrayList<>();

  public ToolRegistrar(Set<ToolCategory> selectedToolsets, boolean readOnly) {
    this.selectedToolsets = Set.copyOf(selectedToolsets);
    this.readOnly = readOnly;
  }

  public boolean register(Tool tool) {
    if (!tool.matchesFilters(selectedToolsets, readOnly)) {
      return false;
    }
    registeredTools.add(tool);
    return true;
  }

  public boolean registerAll(Collection<? extends Tool> tools) {
    var accepted = false;
    for (var tool : tools) {
      if (register(tool)) {
        accepted = true;
      }
    }
    return accepted;
  }

  public List<Tool> getRegisteredTools() {
    return List.copyOf(registeredTools);
  }
}
