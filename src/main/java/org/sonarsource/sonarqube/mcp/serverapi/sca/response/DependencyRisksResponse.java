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
package org.sonarsource.sonarqube.mcp.serverapi.sca.response;

import java.util.List;

public record DependencyRisksResponse(List<IssueRelease> issuesReleases, List<Branch> branches, Integer countWithoutFilters, Page page) {

  public record IssueRelease(String key, String severity, String originalSeverity, String manualSeverity, Boolean showIncreasedSeverityWarning,
                             Release release, String type, String quality, String status, String createdAt, Assignee assignee,
                             Integer commentCount, String vulnerabilityId, List<String> cweIds, String cvssScore, Boolean withdrawn,
                             String spdxLicenseId, List<String> transitions, List<String> actions) {
  }

  public record Release(String key, String branchUuid, String packageUrl, String packageManager, String packageName, String version,
                        String licenseExpression, Boolean known, Boolean knownPackage, Boolean newlyIntroduced, Boolean directSummary,
                        String scopeSummary, Boolean productionScopeSummary, List<String> dependencyFilePaths) {
  }

  public record Assignee(String login, String name, String avatar, Boolean active) {
  }

  public record Branch(String uuid, String key, Boolean isPullRequest, String projectKey, String projectName) {
  }

  public record Page(Integer pageIndex, Integer pageSize, Integer total) {
  }

}
