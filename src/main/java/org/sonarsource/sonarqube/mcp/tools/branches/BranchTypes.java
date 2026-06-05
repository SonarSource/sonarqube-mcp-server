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

import jakarta.annotation.Nullable;

public final class BranchTypes {

  public enum BranchType {
    LONG, SHORT, BRANCH
  }

  public enum QualityGateStatus {
    OK, ERROR, WARN, NONE
  }

  private BranchTypes() {
    // utility class
  }

  public static boolean isLongLivedBranchType(@Nullable String type) {
    return "LONG".equals(type) || "BRANCH".equals(type);
  }

  @Nullable
  public static BranchType parseBranchType(@Nullable String type) {
    if (type == null) {
      return null;
    }
    try {
      return BranchType.valueOf(type);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  @Nullable
  public static QualityGateStatus parseQualityGateStatus(@Nullable String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    try {
      return QualityGateStatus.valueOf(status);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

}
