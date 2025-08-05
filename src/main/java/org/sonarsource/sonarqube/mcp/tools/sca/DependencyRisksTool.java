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
package org.sonarsource.sonarqube.mcp.tools.sca;

import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.sca.response.IssuesReleasesResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public class DependencyRisksTool extends Tool {

  public static final String TOOL_NAME = "get_dependency_risks";
  public static final String PROJECT_KEY_PROPERTY = "projectKey";
  public static final String BRANCH_KEY_PROPERTY = "branchKey";
  public static final String PULL_REQUEST_KEY_PROPERTY = "pullRequestKey";

  private final ServerApi serverApi;

  public DependencyRisksTool(ServerApi serverApi) {
    super(new SchemaToolBuilder()
      .setName(TOOL_NAME)
      .setDescription("Get dependency risks from SonarQube SCA analysis for a project")
      .addStringProperty(PROJECT_KEY_PROPERTY, "Project key to analyze for dependency risks")
      .addStringProperty(BRANCH_KEY_PROPERTY, "Branch key to analyze for dependency risks")
      .addStringProperty(PULL_REQUEST_KEY_PROPERTY, "Pull request key to analyze for dependency risks")
      .build());
    this.serverApi = serverApi;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var projectKey = arguments.getOptionalString(PROJECT_KEY_PROPERTY);
    var branchKey = arguments.getOptionalString(BRANCH_KEY_PROPERTY);
    var pullRequestKey = arguments.getOptionalString(PULL_REQUEST_KEY_PROPERTY);
    
    var response = serverApi.scaApi().getIssuesReleases(projectKey, branchKey, pullRequestKey);
    return Tool.Result.success(buildResponseFromIssuesReleases(response));
  }

  private static String buildResponseFromIssuesReleases(IssuesReleasesResponse response) {
    var stringBuilder = new StringBuilder();
    var dependencyRisks = response.dependencyRisks();

    if (dependencyRisks.isEmpty()) {
      stringBuilder.append("No dependency risks were found.");
      return stringBuilder.toString();
    }

    stringBuilder.append("Found ").append(dependencyRisks.size()).append(" dependency risks.\n\n");

    for (var risk : dependencyRisks) {
      stringBuilder.append("Risk Key: ").append(risk.key())
        .append(" | Package: ").append(risk.packageName())
        .append(" | Version: ").append(risk.packageVersion())
        .append(" | Severity: ").append(risk.severity())
        .append(" | Status: ").append(risk.status())
        .append(" | Type: ").append(risk.riskType())
        .append(" | Component: ").append(risk.component())
        .append(" | Rule: ").append(risk.rule())
        .append(" | Message: ").append(risk.message());

      if (risk.cve() != null && !risk.cve().isEmpty()) {
        stringBuilder.append(" | CVE: ").append(String.join(", ", risk.cve()));
      }

      if (risk.cwe() != null && !risk.cwe().isEmpty()) {
        stringBuilder.append(" | CWE: ").append(String.join(", ", risk.cwe()));
      }

      stringBuilder.append(" | Created: ").append(risk.creationDate())
        .append(" | Updated: ").append(risk.updateDate())
        .append("\n");
    }

    return stringBuilder.toString().trim();
  }
}