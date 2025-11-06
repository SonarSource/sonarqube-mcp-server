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
package org.sonarsource.sonarqube.mcp.tools.dependencyrisks;

import jakarta.annotation.Nullable;
import java.util.Map;
import org.sonarsource.sonarqube.mcp.SonarQubeVersionChecker;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.features.Feature;
import org.sonarsource.sonarqube.mcp.serverapi.sca.response.DependencyRisksResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.SchemaUtils;
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
    var textResponse = buildResponseFromDependencyRisksResponse(response);
    var structuredContent = buildStructuredContent(response);
    return Tool.Result.success(textResponse, structuredContent);
  }

  private static Map<String, Object> buildStructuredContent(DependencyRisksResponse response) {
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

    var toolResponse = new SearchDependencyRisksToolResponse(issuesReleases);
    return SchemaUtils.toStructuredContent(toolResponse);
  }

  private static String buildResponseFromDependencyRisksResponse(DependencyRisksResponse response) {
    var issuesReleases = response.issuesReleases();

    if (issuesReleases.isEmpty()) {
      return "No dependency risks were found.";
    }

    var stringBuilder = new StringBuilder();
    stringBuilder.append("Found ").append(issuesReleases.size()).append(" dependency risks.\n");

    appendPaginationInfo(stringBuilder, response.page());

    for (var issueRelease : issuesReleases) {
      appendIssueReleaseInfo(stringBuilder, issueRelease);
    }

    return stringBuilder.toString().trim();
  }

  private static void appendPaginationInfo(StringBuilder stringBuilder, @Nullable DependencyRisksResponse.Page page) {
    if (page != null) {
      var totalPages = (int) Math.ceil((double) page.total() / page.pageSize());
      stringBuilder.append("This response is paginated and this is the page ").append(page.pageIndex())
        .append(" out of ").append(totalPages).append(" total pages. There is a maximum of ")
        .append(page.pageSize()).append(" items per page.\n");
    }
  }

  private static void appendIssueReleaseInfo(StringBuilder stringBuilder, DependencyRisksResponse.IssueRelease issueRelease) {
    stringBuilder.append("Issue key: ").append(issueRelease.key())
      .append(" | Severity: ").append(issueRelease.severity())
      .append(" | Type: ").append(issueRelease.type())
      .append(" | Quality: ").append(issueRelease.quality())
      .append(" | Status: ").append(issueRelease.status());

    appendOptionalFields(stringBuilder, issueRelease);
    appendReleaseInfo(stringBuilder, issueRelease.release());
    appendAssigneeInfo(stringBuilder, issueRelease.assignee());

    stringBuilder.append(" | Created: ").append(issueRelease.createdAt());
    stringBuilder.append("\n");
  }

  private static void appendOptionalFields(StringBuilder stringBuilder, DependencyRisksResponse.IssueRelease issueRelease) {
    if (issueRelease.vulnerabilityId() != null) {
      stringBuilder.append(" | Vulnerability ID: ").append(issueRelease.vulnerabilityId());
    }

    if (issueRelease.cvssScore() != null) {
      stringBuilder.append(" | CVSS Score: ").append(issueRelease.cvssScore());
    }
  }

  private static void appendReleaseInfo(StringBuilder stringBuilder, @Nullable DependencyRisksResponse.Release release) {
    if (release != null) {
      stringBuilder.append(" | Package: ").append(release.packageName())
        .append(" | Version: ").append(release.version())
        .append(" | Package Manager: ").append(release.packageManager());

      if (release.newlyIntroduced() != null && release.newlyIntroduced()) {
        stringBuilder.append(" | Newly Introduced: Yes");
      }

      if (release.directSummary() != null && release.directSummary()) {
        stringBuilder.append(" | Direct Dependency: Yes");
      }

      if (release.productionScopeSummary() != null && release.productionScopeSummary()) {
        stringBuilder.append(" | Production Scope: Yes");
      }
    }
  }

  private static void appendAssigneeInfo(StringBuilder stringBuilder, @Nullable DependencyRisksResponse.Assignee assignee) {
    if (assignee != null) {
      stringBuilder.append(" | Assignee: ").append(assignee.name());
    }
  }

}
