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
package org.sonarsource.sonarqube.mcp.transport;

import java.util.Set;
import org.sonarsource.sonarqube.mcp.tools.ToolAccessMode;

/** Independent delegation scopes exposed to MCP clients and enforced by Cloud APIs. */
public enum OAuthScope {
  READ("mcp:tools:read", "read:all"),
  WRITE("mcp:tools:write", "write:all");

  private final String mcpScope;
  private final String cloudScope;

  OAuthScope(String mcpScope, String cloudScope) {
    this.mcpScope = mcpScope;
    this.cloudScope = cloudScope;
  }

  public String getMcpScope() {
    return mcpScope;
  }

  public String getCloudScope() {
    return cloudScope;
  }

  public static Set<OAuthScope> requiredBy(ToolAccessMode accessMode) {
    return switch (accessMode) {
      case READ -> Set.of(READ);
      case WRITE -> Set.of(WRITE);
      case READ_WRITE -> Set.of(READ, WRITE);
    };
  }
}
