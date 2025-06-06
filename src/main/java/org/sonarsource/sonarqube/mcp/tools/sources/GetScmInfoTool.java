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

import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.sources.response.ScmResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public class GetScmInfoTool extends Tool {

  public static final String TOOL_NAME = "get_scm_info";
  public static final String KEY_PROPERTY = "key";
  public static final String COMMITS_BY_LINE_PROPERTY = "commits_by_line";
  public static final String FROM_PROPERTY = "from";
  public static final String TO_PROPERTY = "to";

  private final ServerApi serverApi;

  public GetScmInfoTool(ServerApi serverApi) {
    super(new SchemaToolBuilder()
      .setName(TOOL_NAME)
      .setDescription("Get SCM information of source files. Require See Source Code permission on file's project")
      .addRequiredStringProperty(KEY_PROPERTY, "File key (e.g. my_project:src/foo/Bar.php)")
      .addBooleanProperty(COMMITS_BY_LINE_PROPERTY, "Group lines by SCM commit if value is false, else display commits for each line (true/false)")
      .addNumberProperty(FROM_PROPERTY, "First line to return. Starts at 1")
      .addNumberProperty(TO_PROPERTY, "Last line to return (inclusive)")
      .build());
    this.serverApi = serverApi;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    if (!serverApi.isAuthenticationSet()) {
      return Tool.Result.failure("Not connected to SonarQube, please provide valid credentials");
    }

    var key = arguments.getStringOrThrow(KEY_PROPERTY);
    var commitsByLine = arguments.getOptionalBoolean(COMMITS_BY_LINE_PROPERTY);
    var from = arguments.getOptionalInteger(FROM_PROPERTY);
    var to = arguments.getOptionalInteger(TO_PROPERTY);
    
    try {
      var scmInfo = serverApi.sourcesApi().getScmInfo(key, commitsByLine, from, to);
      return Tool.Result.success(buildResponseFromScmInfo(scmInfo));
    } catch (Exception e) {
      return Tool.Result.failure("Failed to retrieve SCM information: " + e.getMessage());
    }
  }

  private static String buildResponseFromScmInfo(ScmResponse scmResponse) {
    var responseBuilder = new StringBuilder();
    responseBuilder.append("SCM Information:\n");
    responseBuilder.append("================\n\n");
    
    var scmLines = scmResponse.getScmLines();
    if (scmLines.isEmpty()) {
      responseBuilder.append("No SCM information available for this file.\n");
    } else {
      responseBuilder.append("Line | Author      | Date                    | Revision\n");
      responseBuilder.append("-----|-------------|-------------------------|----------------\n");
      
      for (var scmLine : scmLines) {
        responseBuilder.append(String.format("%-4d | %-11s | %-23s | %s%n",
          scmLine.lineNumber(),
          scmLine.author(),
          scmLine.datetime(),
          scmLine.revision()));
      }
    }
    
    return responseBuilder.toString().trim();
  }

}
