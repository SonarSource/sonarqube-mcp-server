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
package org.sonarsource.sonarqube.mcp.tools.duplications;

import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.duplications.response.DuplicationsResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class GetDuplicationsTool extends Tool {

  public static final String TOOL_NAME = "get_duplications";
  public static final String KEY_PROPERTY = "key";
  public static final String PULL_REQUEST_PROPERTY = "pullRequest";

  private final ServerApiProvider serverApiProvider;

  public GetDuplicationsTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(GetDuplicationsToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Get SonarQube Code Duplications")
      .setDescription("Get duplications for a file. Requires Browse permission on file's project")
      .addRequiredStringProperty(KEY_PROPERTY, "File key (e.g. my_project:src/foo/Bar.php)")
      .addStringProperty(PULL_REQUEST_PROPERTY, "Pull request id")
      .setReadOnlyHint()
      .build(),
      ToolCategory.DUPLICATIONS);
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var key = arguments.getStringOrThrow(KEY_PROPERTY);
    var pullRequest = arguments.getOptionalString(PULL_REQUEST_PROPERTY);
    
    var duplicationsResponse = serverApiProvider.get().duplicationsApi().getDuplications(key, null, pullRequest);
    var response = buildStructuredContent(duplicationsResponse);
    return Tool.Result.success(response);
  }

  private static GetDuplicationsToolResponse buildStructuredContent(DuplicationsResponse duplicationsResponse) {
    var duplications = duplicationsResponse.duplications().stream()
      .map(duplication -> {
        var blocks = duplication.blocks().stream()
          .map(block -> {
            var fileInfo = duplicationsResponse.files().get(block._ref());
            return new GetDuplicationsToolResponse.Block(
              block.from(),
              block.size(),
              fileInfo != null ? fileInfo.name() : "",
              fileInfo != null ? fileInfo.key() : ""
            );
          })
          .toList();
        return new GetDuplicationsToolResponse.Duplication(blocks);
      })
      .toList();

    var files = duplicationsResponse.files().values().stream()
      .map(fileInfo -> new GetDuplicationsToolResponse.FileInfo(
        fileInfo.key(),
        fileInfo.name()
      ))
      .toList();

    return new GetDuplicationsToolResponse(duplications, files);
  }

}
