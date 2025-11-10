/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
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
package org.sonarsource.sonarqube.mcp.serverapi.sources.response;

import java.util.List;

public record ScmResponse(List<List<Object>> scm) {

  public record ScmLine(int lineNumber, String author, String datetime, String revision) {

    public static ScmLine fromArray(List<Object> array) {
      return new ScmLine(
        ((Number) array.get(0)).intValue(),
        (String) array.get(1),
        (String) array.get(2),
        (String) array.get(3)
      );
    }
  }

  public List<ScmLine> getScmLines() {
    return scm.stream()
      .map(ScmLine::fromArray)
      .toList();
  }

}
