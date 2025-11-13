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
package org.sonarsource.sonarqube.mcp.tools.sources;

import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.sources.response.ScmResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public class GetScmInfoTool extends Tool {

  public static final String TOOL_NAME = "get_scm_info";
  public static final String KEY_PROPERTY = "key";
  public static final String COMMITS_BY_LINE_PROPERTY = "commits_by_line";
  public static final String FROM_PROPERTY = "from";
  public static final String TO_PROPERTY = "to";

  private final ServerApiProvider serverApiProvider;

  public GetScmInfoTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(GetScmInfoToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Get SonarQube SCM Information of Source File")
      .setDescription("Get SCM information of SonarQube source files. Require See Source Code permission on file's project")
      .addRequiredStringProperty(KEY_PROPERTY, "File key (e.g. my_project:src/foo/Bar.php)")
      .addBooleanProperty(COMMITS_BY_LINE_PROPERTY, "Group lines by SCM commit if value is false, else display commits for each line (true/false)")
      .addNumberProperty(FROM_PROPERTY, "First line to return. Starts at 1")
      .addNumberProperty(TO_PROPERTY, "Last line to return (inclusive)")
      .setReadOnlyHint()
      .setOpenWorldHint()
      .build());
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var key = arguments.getStringOrThrow(KEY_PROPERTY);
    var commitsByLine = arguments.getOptionalBoolean(COMMITS_BY_LINE_PROPERTY);
    var from = arguments.getOptionalInteger(FROM_PROPERTY);
    var to = arguments.getOptionalInteger(TO_PROPERTY);
    
    try {
      var scmInfo = serverApiProvider.get().sourcesApi().getScmInfo(key, commitsByLine, from, to);
      var toolResponse = buildStructuredContent(scmInfo);
      return Tool.Result.success(toolResponse);
    } catch (Exception e) {
      return Tool.Result.failure("Failed to retrieve SCM information: " + e.getMessage());
    }
  }

  private static GetScmInfoToolResponse buildStructuredContent(ScmResponse scmResponse) {
    var scmLines = scmResponse.getScmLines().stream()
      .map(line -> new GetScmInfoToolResponse.ScmLine(line.lineNumber(), line.author(), line.datetime(), line.revision()))
      .toList();

    return new GetScmInfoToolResponse(scmLines);
  }

}
