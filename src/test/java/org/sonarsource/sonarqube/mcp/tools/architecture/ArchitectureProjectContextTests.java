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
package org.sonarsource.sonarqube.mcp.tools.architecture;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchitectureProjectContextTests {
  @ParameterizedTest
  @ValueSource(strings = {"main-id", "feature-branch-id", "pull-request-id"})
  void it_should_retain_the_selected_analyzed_branch(String branchId) {
    assertThat(ArchitectureProjectContext.requireAnalyzedBranch("project", branchId, "2026-11-06T12:00:00Z")).isEqualTo(branchId);
  }

  @ParameterizedTest
  @CsvSource(value = {"NULL,2026-11-06", "' ',2026-11-06", "feature-id,NULL", "pull-request-id,' '"}, nullValues = "NULL")
  void it_should_reject_a_missing_selected_branch_or_analysis(String branchId, String analysisDate) {
    assertThatThrownBy(() -> ArchitectureProjectContext.requireAnalyzedBranch("project", branchId, analysisDate))
      .isInstanceOf(IllegalStateException.class).hasMessageContaining("selected branch").hasMessageContaining("has not been analyzed");
  }
}
