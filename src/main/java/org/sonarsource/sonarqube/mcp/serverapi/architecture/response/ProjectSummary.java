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
package org.sonarsource.sonarqube.mcp.serverapi.architecture.response;

import com.google.gson.annotations.JsonAdapter;
import jakarta.annotation.Nullable;

public record ProjectSummary(String language, @Nullable DeviationCounts deviations, @Nullable FlawCounts flaws, @Nullable SmellCounts smells) {
  public record DeviationCounts(@JsonAdapter(SummaryCountAdapter.class) Long total, @JsonAdapter(SummaryCountAdapter.class) Long structuralDeviation,
                                @JsonAdapter(SummaryCountAdapter.class) Long relationshipDeviation) { }
  public record FlawCounts(@JsonAdapter(SummaryCountAdapter.class) Long total, @JsonAdapter(SummaryCountAdapter.class) Long tangle,
                           @JsonAdapter(SummaryCountAdapter.class) Long oversizedComponent) { }
  public record SmellCounts(@JsonAdapter(SummaryCountAdapter.class) Long total, @JsonAdapter(SummaryCountAdapter.class) Long weakTangle,
                            @JsonAdapter(SummaryCountAdapter.class) Long splitResponsibility) { }
}
