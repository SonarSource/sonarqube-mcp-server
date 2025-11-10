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
package org.sonarsource.sonarqube.mcp.tools.dependencyrisks;

import org.sonarsource.sonarqube.mcp.SonarQubeVersionChecker;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.features.Feature;
import org.sonarsource.sonarqube.mcp.serverapi.sca.response.DependencyRisksResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public class SearchDependencyRisksTool extends Tool {

  public static final String TOOL_NAME = "search_dependency_risks";
  public static final String PROJECT_KEY_PROPERTY = "projectKey";
  public static final String BRANCH_KEY_PROPERTY = "branchKey";
  public static final String PULL_REQUEST_KEY_PROPERTY = "pullRequestKey";

  private final ServerApiProvider serverApiProvider;
  private final SonarQubeVersionChecker sonarQubeVersionChecker;

  public SearchDependencyRisksTool(ServerApiProvider serverApiProvider, SonarQubeVersionChecker sonarQubeVersionChecker) {
    super(SchemaToolBuilder.forOutput(SearchDependencyRisksToolResponse.class)
      .setName(TOOL_NAME)
      .setTitle("Search Dependency Risks")
      .setDescription("Search for software composition analysis issues (dependency risks) of a SonarQube project, " +
        "paired with releases that appear in the analyzed project, application, or portfolio.")
      .addRequiredStringProperty(PROJECT_KEY_PROPERTY, "The project key")
      .addStringProperty(BRANCH_KEY_PROPERTY, "The branch key")
      .addStringProperty(PULL_REQUEST_KEY_PROPERTY, "The pull request key")
      .build());
    this.serverApiProvider = serverApiProvider;
    this.sonarQubeVersionChecker = sonarQubeVersionChecker;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var provider = serverApiProvider.get();
    if (!provider.isSonarQubeCloud() && !sonarQubeVersionChecker.isSonarQubeServerVersionHigherOrEqualsThan("2025.4")) {
      return Tool.Result.failure("Search Dependency Risks tool is not available because it requires SonarQube Server 2025.4 Enterprise or higher.");
    }
    if (provider.isSonarQubeCloud() && !provider.scaApi().isScaEnabled()) {
      return Tool.Result.failure("Search Dependency Risks tool is not available in your SonarQube Cloud organization because Advanced Security is not enabled.");
    }
    if (!provider.isSonarQubeCloud() && !provider.featuresApi().listFeatures().contains(Feature.SCA)) {
      return Tool.Result.failure("Search Dependency Risks tool is not available for SonarQube Server because Advanced Security is not enabled.");
    }
    var projectKey = arguments.getStringOrThrow(PROJECT_KEY_PROPERTY);
    var branchKey = arguments.getOptionalString(BRANCH_KEY_PROPERTY);
    var pullRequestKey = arguments.getOptionalString(PULL_REQUEST_KEY_PROPERTY);

    var response = provider.scaApi().getDependencyRisks(projectKey, branchKey, pullRequestKey);
    var toolResponse = buildStructuredContent(response);
    return Tool.Result.success(toolResponse);
  }

  private static SearchDependencyRisksToolResponse buildStructuredContent(DependencyRisksResponse response) {
    var issuesReleases = response.issuesReleases().stream()
      .map(ir -> {
        SearchDependencyRisksToolResponse.Release release = null;
        if (ir.release() != null) {
          var r = ir.release();
          release = new SearchDependencyRisksToolResponse.Release(
            r.packageName(), r.version(), r.packageManager(),
            r.newlyIntroduced(), r.directSummary(), r.productionScopeSummary()
          );
        }
        
        SearchDependencyRisksToolResponse.Assignee assignee = null;
        if (ir.assignee() != null) {
          assignee = new SearchDependencyRisksToolResponse.Assignee(ir.assignee().name());
        }
        
        return new SearchDependencyRisksToolResponse.IssueRelease(
          ir.key(), ir.severity(), ir.type(), ir.quality(), ir.status(), ir.createdAt(),
          ir.vulnerabilityId(), ir.cvssScore(), release, assignee
        );
      })
      .toList();

    return new SearchDependencyRisksToolResponse(issuesReleases);
  }

}
