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
package org.sonarsource.sonarqube.mcp.tools.history;

import jakarta.annotation.Nullable;
import java.util.List;
import org.sonarsource.sonarqube.mcp.serverapi.ServerApiProvider;
import org.sonarsource.sonarqube.mcp.serverapi.branches.response.BranchesListResponse;
import org.sonarsource.sonarqube.mcp.serverapi.enterprises.response.PortfoliosResponse;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public class HistoryTargetResolver {

  public static final String TARGET_TYPE_PROPERTY = "targetType";
  public static final String PROJECT_KEY_PROPERTY = "projectKey";
  public static final String BRANCH_PROPERTY = "branch";
  public static final String PORTFOLIO_ID_PROPERTY = "portfolioId";
  public static final String PORTFOLIO_NAME_PROPERTY = "portfolioName";
  public static final String ENTERPRISE_ID_PROPERTY = "enterpriseId";
  public static final String PROJECT_BRANCH = "PROJECT_BRANCH";
  public static final String PORTFOLIO = "PORTFOLIO";
  public static final String[] VALID_TARGET_TYPES = {PROJECT_BRANCH, PORTFOLIO};

  private final ServerApiProvider serverApiProvider;

  public HistoryTargetResolver(ServerApiProvider serverApiProvider) {
    this.serverApiProvider = serverApiProvider;
  }

  public Resolution resolve(Tool.Arguments arguments) {
    var targetType = arguments.getEnumOrThrow(TARGET_TYPE_PROPERTY, VALID_TARGET_TYPES);
    return switch (targetType) {
      case PROJECT_BRANCH -> resolveProjectBranch(arguments);
      case PORTFOLIO -> resolvePortfolio(arguments);
      default -> Resolution.failure("Invalid targetType: " + targetType);
    };
  }

  private Resolution resolveProjectBranch(Tool.Arguments arguments) {
    var projectKey = arguments.getStringOrThrow(PROJECT_KEY_PROPERTY);
    var branchName = arguments.getOptionalString(BRANCH_PROPERTY);
    var branches = serverApiProvider.get().projectBranchesApi().listBranches(projectKey).branches();

    BranchesListResponse.Branch branch;
    if (branchName == null) {
      branch = branches.stream()
        .filter(BranchesListResponse.Branch::isMain)
        .findFirst()
        .orElse(null);
      if (branch == null) {
        return Resolution.failure("No main branch found for project '" + projectKey + "'. Use list_branches to discover available branches.");
      }
    } else {
      branch = branches.stream()
        .filter(candidate -> branchName.equals(candidate.name()))
        .findFirst()
        .orElse(null);
      if (branch == null) {
        return Resolution.failure("Branch '" + branchName + "' was not found for project '" + projectKey + "'. Use list_branches to discover available branches.");
      }
    }

    if (branch.branchId() == null || branch.branchId().isBlank()) {
      return Resolution.failure("Branch '" + branch.name() + "' for project '" + projectKey + "' does not expose a branchId required by history endpoints.");
    }
    return Resolution.success(PROJECT_BRANCH, branch.branchId());
  }

  private Resolution resolvePortfolio(Tool.Arguments arguments) {
    var portfolioId = arguments.getOptionalString(PORTFOLIO_ID_PROPERTY);
    if (portfolioId != null) {
      return Resolution.success(PORTFOLIO, portfolioId);
    }

    var portfolioName = arguments.getOptionalString(PORTFOLIO_NAME_PROPERTY);
    if (portfolioName == null) {
      return Resolution.failure("For targetType PORTFOLIO, provide either portfolioId or portfolioName. Use list_portfolios to discover portfolio IDs.");
    }

    var enterpriseId = arguments.getOptionalString(ENTERPRISE_ID_PROPERTY);
    var response = serverApiProvider.get().enterprisesApi().listPortfolios(enterpriseId, portfolioName, enterpriseId == null ? Boolean.TRUE : null, null, null, null);
    var matches = response.portfolios().stream()
      .filter(portfolio -> portfolioName.equals(portfolio.name()))
      .toList();

    if (matches.isEmpty()) {
      var scope = enterpriseId == null ? "favorite portfolios" : "enterprise '" + enterpriseId + "'";
      return Resolution.failure("No portfolio named '" + portfolioName + "' was found in " + scope + ". Use list_portfolios to discover portfolio IDs.");
    }
    if (matches.size() > 1) {
      return Resolution.failure("Multiple portfolios named '" + portfolioName + "' were found. Retry with portfolioId. Candidates: " + formatPortfolioCandidates(matches));
    }

    return Resolution.success(PORTFOLIO, matches.getFirst().id());
  }

  private static String formatPortfolioCandidates(List<PortfoliosResponse.Portfolio> portfolios) {
    return String.join(", ", portfolios.stream()
      .map(portfolio -> portfolio.name() + " (" + portfolio.id() + ")")
      .toList());
  }

  public record Resolution(@Nullable String entityType, @Nullable String entityId, @Nullable Tool.Result failure) {
    public static Resolution success(String entityType, String entityId) {
      return new Resolution(entityType, entityId, null);
    }

    public static Resolution failure(String message) {
      return new Resolution(null, null, Tool.Result.failure(message));
    }

    public boolean isFailure() {
      return failure != null;
    }
  }
}
