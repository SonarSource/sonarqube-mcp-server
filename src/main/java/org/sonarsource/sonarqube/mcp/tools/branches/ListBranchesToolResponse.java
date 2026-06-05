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
package org.sonarsource.sonarqube.mcp.tools.branches;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import jakarta.annotation.Nullable;
import org.sonarsource.sonarqube.mcp.tools.branches.BranchTypes.BranchType;
import org.sonarsource.sonarqube.mcp.tools.branches.BranchTypes.QualityGateStatus;

public record ListBranchesToolResponse(
  @JsonPropertyDescription("Project key") String projectKey,
  @JsonPropertyDescription("Total number of branches") int totalBranches,
  @JsonPropertyDescription("List of branches for this project") List<Branch> branches
) {

  public record Branch(
    @JsonPropertyDescription("Branch name that can be used with other tools as the branch parameter") String name,
    @JsonPropertyDescription("Whether this is the main branch") boolean isMain,
    @JsonPropertyDescription("Branch type in SonarQube (LONG on SonarQube Cloud, BRANCH on SonarQube Server)") @Nullable BranchType type,
    @JsonPropertyDescription("Quality gate status for this branch") @Nullable QualityGateStatus qualityGateStatus,
    @JsonPropertyDescription("Date of the last analysis") @Nullable String analysisDate,
    @JsonPropertyDescription("Internal branch identifier") String branchId
  ) {
  }

}
