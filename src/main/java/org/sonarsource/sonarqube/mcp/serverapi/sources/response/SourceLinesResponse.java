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
package org.sonarsource.sonarqube.mcp.serverapi.sources.response;

import java.util.List;
import javax.annotation.Nullable;

public record SourceLinesResponse(List<SourceLine> sources) {

  public record SourceLine(
    int line,
    String code,
    @Nullable Integer lineHits,
    @Nullable Integer conditions,
    @Nullable Integer coveredConditions,
    @Nullable String scmAuthor,
    @Nullable String scmDate,
    @Nullable String scmRevision,
    @Nullable Boolean isNew
  ) {

    public boolean isCoverable() {
      return lineHits != null;
    }

    public boolean isUncovered() {
      return lineHits != null && lineHits == 0;
    }

    public boolean hasPartialBranchCoverage() {
      return conditions != null && conditions > 0 && coveredConditions != null && coveredConditions > 0 && coveredConditions < conditions;
    }

    public boolean hasNoBranchCoverage() {
      return conditions != null && conditions > 0 && (coveredConditions == null || coveredConditions == 0);
    }
  }
}
