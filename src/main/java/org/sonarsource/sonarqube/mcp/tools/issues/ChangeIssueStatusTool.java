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
package org.sonarsource.sonarqube.mcp.tools.issues;

import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.issues.Transition;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public class ChangeIssueStatusTool extends Tool {

  public static final String TOOL_NAME = "change_sonar_issue_status";
  public static final String KEY_PROPERTY = "key";
  public static final String STATUS_PROPERTY = "status";

  private final ServerApiProvider serverApiProvider;

  public ChangeIssueStatusTool(ServerApiProvider serverApiProvider) {
    super(SchemaToolBuilder.forOutput(ChangeIssueStatusToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Change SonarQube Issue Status")
      .setDescription("""
        Change the status of a Sonar issue. This tool can be used to change the status of an issue to "accept", "falsepositive" or to "reopen" an issue.
        An example request could be: I would like to accept the issue having the key "AX-HMISMFixnZED\"""")
      .addRequiredStringProperty(KEY_PROPERTY, "The key of the issue which status should be changed")
      .addRequiredEnumProperty(STATUS_PROPERTY, new String[] {"accept", "falsepositive", "reopen"}, "The new status of the issue")
      .build());
    this.serverApiProvider = serverApiProvider;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var key = arguments.getStringOrThrow(KEY_PROPERTY);
    var statusString = arguments.getStringListOrThrow(STATUS_PROPERTY).getFirst();
    var status = Transition.fromStatus(statusString);
    if (status.isEmpty()) {
      return Tool.Result.failure("Status is unknown: " + statusString);
    }

    serverApiProvider.get().issuesApi().doTransition(key, status.get());
    var message = "The issue status was successfully changed.";
    var response = new ChangeIssueStatusToolResponse(true, message, key, statusString);
    return Tool.Result.success(response);
  }

}
