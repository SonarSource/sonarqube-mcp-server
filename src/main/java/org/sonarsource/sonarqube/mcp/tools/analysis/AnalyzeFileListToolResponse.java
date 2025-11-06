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
package org.sonarsource.sonarqube.mcp.tools.analysis;
import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.sonarsource.sonarqube.mcp.tools.Description;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalyzeFileListToolResponse(
  @Description("List of findings from the analysis") List<Finding> findings,
  @Description("Total number of findings") int findingsCount
) {
  
  public record Finding(
    @Description("Severity level of the finding") @Nullable String severity,
    @Description("Description of the finding") String message,
    @Description("File path where the finding was detected") @Nullable String filePath,
    @Description("Location in the source file") @Nullable TextRange textRange
  ) {}
  
  public record TextRange(
    @Description("Starting line number") int startLine,
    @Description("Ending line number") int endLine
  ) {}
}


