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

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BranchPullRequestContextTest {

  @Test
  void validateMutualExclusion_should_return_empty_when_both_are_null() {
    assertThat(BranchPullRequestContext.validateMutualExclusion(null, null)).isEmpty();
  }

  @Test
  void validateMutualExclusion_should_return_empty_when_only_branch_is_set() {
    assertThat(BranchPullRequestContext.validateMutualExclusion("main", null)).isEmpty();
  }

  @Test
  void validateMutualExclusion_should_return_empty_when_only_pull_request_is_set() {
    assertThat(BranchPullRequestContext.validateMutualExclusion(null, "5461")).isEmpty();
  }

  @Test
  void validateMutualExclusion_should_return_error_when_both_are_set() {
    var result = BranchPullRequestContext.validateMutualExclusion("main", "5461");

    assertThat(result).isPresent();
    assertThat(result.get().isError()).isTrue();
  }

  @Test
  void params_from_should_extract_branch_and_pull_request() {
    var arguments = new Tool.Arguments(Map.of(
      BranchPullRequestContext.BRANCH_PROPERTY, "develop",
      BranchPullRequestContext.PULL_REQUEST_PROPERTY, "42"), null);

    var params = BranchPullRequestContext.from(arguments);

    assertThat(params.branch()).isEqualTo("develop");
    assertThat(params.pullRequest()).isEqualTo("42");
    assertThat(params.validationError()).isPresent();
  }

  @Test
  void params_validationError_should_return_empty_when_only_branch_is_set() {
    var arguments = new Tool.Arguments(Map.of(BranchPullRequestContext.BRANCH_PROPERTY, "main"), null);

    assertThat(BranchPullRequestContext.from(arguments).validationError()).isEmpty();
  }

}
