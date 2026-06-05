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
package org.sonarsource.sonarqube.mcp.tools;

import java.util.Optional;
import jakarta.annotation.Nullable;

public final class BranchPullRequestContext {

  public static final String BRANCH_PROPERTY = "branch";
  public static final String PULL_REQUEST_PROPERTY = "pullRequest";

  public static final String BRANCH_PROPERTY_DESCRIPTION = """
    Long-lived branch name in SonarQube (e.g. 'main', 'develop'). Use list_branches to discover valid names. \
    Not for feature branches or pull request analysis — use pullRequest instead.""";

  public static final String PULL_REQUEST_PROPERTY_DESCRIPTION = """
    Pull request key/ID in SonarQube. Use list_pull_requests to discover valid keys. \
    Not for long-lived branches — use branch instead. Must be the SonarQube PR key, not a git branch name.""";

  private BranchPullRequestContext() {
    // utility class
  }

  public record Params(@Nullable String branch, @Nullable String pullRequest) {
    public Optional<Tool.Result> validationError() {
      return validateMutualExclusion(branch, pullRequest);
    }
  }

  public static Params from(Tool.Arguments arguments) {
    return new Params(
      arguments.getOptionalString(BRANCH_PROPERTY),
      arguments.getOptionalString(PULL_REQUEST_PROPERTY));
  }

  public static Optional<Tool.Result> validateMutualExclusion(@Nullable String branch, @Nullable String pullRequest) {
    if (branch != null && pullRequest != null) {
      return Optional.of(Tool.Result.failure(
        "Cannot use 'branch' and 'pullRequest' together. Use 'branch' for long-lived branches (see list_branches) "
          + "or 'pullRequest' for pull requests (see list_pull_requests)."));
    }
    return Optional.empty();
  }

}
