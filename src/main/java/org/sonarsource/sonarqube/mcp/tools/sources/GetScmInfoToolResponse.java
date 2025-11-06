/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonarsource.sonarqube.mcp.tools.sources;

import java.util.List;
import org.sonarsource.sonarqube.mcp.tools.Description;

public record GetScmInfoToolResponse(
  @Description("SCM information for each line") List<ScmLine> scmLines
) {
  
  public record ScmLine(
    @Description("Line number in the file") int lineNumber,
    @Description("Author who last modified this line") String author,
    @Description("Date and time of last modification") String datetime,
    @Description("SCM revision/commit identifier") String revision
  ) {}
}


