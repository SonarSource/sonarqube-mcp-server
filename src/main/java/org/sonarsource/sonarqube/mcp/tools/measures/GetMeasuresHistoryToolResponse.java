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
package org.sonarsource.sonarqube.mcp.tools.measures;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.annotation.Nullable;
import java.util.List;

public record GetMeasuresHistoryToolResponse(
  @JsonPropertyDescription("Measures history for the resolved target entity") List<MeasuresHistory> measuresHistory
) {

  public record MeasuresHistory(
    @JsonPropertyDescription("Date the measures were recorded") String date,
    @JsonPropertyDescription("Measures recorded at this date") List<Measure> measures
  ) {
  }

  public record Measure(
    @JsonPropertyDescription("Metric key") String metric,
    @JsonPropertyDescription("Metric value type, when returned by SonarQube Cloud") @Nullable String type,
    @JsonPropertyDescription("Metric value") String value
  ) {
  }
}
