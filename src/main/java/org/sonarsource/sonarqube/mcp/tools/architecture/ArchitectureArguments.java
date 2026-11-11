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
import org.sonarsource.sonarqube.mcp.tools.Tool.Arguments;

/** Keeps strict numeric validation confined to the opt-in Architecture tools. */
final class ArchitectureArguments extends Arguments {
  ArchitectureArguments(Arguments arguments) {
    super(arguments.toMap(), arguments.getMeta());
  }

  @Override
  @Nullable
  public Integer getOptionalInteger(String name) {
    var value = toMap().get(name);
    if (value != null && !(value instanceof String) && !(value instanceof Number)) {
      throw new IllegalArgumentException(name + " must be a 32-bit integer");
    }
    return super.getOptionalInteger(name);
  }
}
