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
package org.sonarsource.sonarqube.mcp.serverapi.measures.response;

import jakarta.annotation.Nullable;
import java.util.List;

public record ComponentMeasuresResponse(Component component, List<Metric> metrics, List<Period> periods) {

  public record Component(String key, String name, String description, String qualifier, String language, String path, List<Measure> measures) {
  }

  public record Measure(String metric, @Nullable String value, @Nullable List<MeasurePeriod> periods, @Nullable MeasurePeriod period) {

    @Nullable
    public MeasurePeriod newCodePeriod() {
      if (period != null) {
        return period;
      }
      if (periods == null || periods.isEmpty()) {
        return null;
      }
      return periods.stream()
        .filter(p -> p.index() != null && p.index() == 1)
        .findFirst()
        .orElse(periods.getFirst());
    }
  }

  public record MeasurePeriod(@Nullable Integer index, @Nullable String value, @Nullable Boolean bestValue) {
  }

  public record Metric(String key, String name, String description, String domain, String type, 
                      boolean higherValuesAreBetter, boolean qualitative, boolean hidden, boolean custom) {
  }

  public record Period(int index, String mode, String date, String parameter) {
  }

}
