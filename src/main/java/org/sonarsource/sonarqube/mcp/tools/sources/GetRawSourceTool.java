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
package org.sonarsource.sonarqube.mcp.tools.sources;

import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.tools.BranchPullRequestContext;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;
import org.sonarsource.sonarqube.mcp.tools.ToolCategory;

public class GetRawSourceTool extends Tool {

  public static final String TOOL_NAME = "get_raw_source";
  public static final String KEY_PROPERTY = "key";
  public static final String BRANCH_PROPERTY = BranchPullRequestContext.BRANCH_PROPERTY;
  public static final String PULL_REQUEST_PROPERTY = BranchPullRequestContext.PULL_REQUEST_PROPERTY;

  private final ServerApiProvider serverApiProvider;

  public GetRawSourceTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(GetRawSourceToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Get SonarQube Raw Source Code")
      .setDescription("Get source code as raw text. Requires 'See Source Code' permission on file.")
      .addRequiredStringProperty(KEY_PROPERTY, "File key (e.g. my_project:src/foo/Bar.php)")
      .addBranchAndPullRequestProperties()
      .setReadOnlyHint()
      .build(),
      ToolCategory.SOURCES);
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var key = arguments.getStringOrThrow(KEY_PROPERTY);
    var branchPullRequest = BranchPullRequestContext.from(arguments);
    var validationError = branchPullRequest.validationError();
    if (validationError.isPresent()) {
      return validationError.get();
    }

    try {
      var rawSource = serverApiProvider.get().sourcesApi().getRawSource(key, branchPullRequest.branch(), branchPullRequest.pullRequest());
      var response = new GetRawSourceToolResponse(key, rawSource);
      return Tool.Result.success(response);
    } catch (Exception e) {
      return Tool.Result.failure("Failed to retrieve source code: " + e.getMessage());
    }
  }

}
