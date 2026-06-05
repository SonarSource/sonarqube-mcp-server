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
package org.sonarsource.sonarqube.mcp.serverapi.branches.response;

import java.util.List;
import jakarta.annotation.Nullable;

public record BranchesListResponse(List<Branch> branches) {

  public record Branch(
    String name,
    boolean isMain,
    String type,
    @Nullable Status status,
    @Nullable String analysisDate,
    String branchId
  ) {
  }

  public record Status(@Nullable String qualityGateStatus) {
  }

}
