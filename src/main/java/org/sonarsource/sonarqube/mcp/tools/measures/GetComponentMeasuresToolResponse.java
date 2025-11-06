/*
 * SonarQube MCP Server
 * Copyright (C) 2025 SonarSource
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
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

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.Nullable;
import java.util.List;
import org.sonarsource.sonarqube.mcp.tools.Description;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetComponentMeasuresToolResponse(
  @Description("Component information") Component component,
  @Description("List of measures for the component") List<Measure> measures,
  @Description("Metadata about the metrics") @Nullable List<Metric> metrics
) {
  
  public record Component(
    @Description("Component key") String key,
    @Description("Component display name") String name,
    @Description("Component qualifier (TRK for project, FIL for file, etc.)") String qualifier,
    @Description("Component description") @Nullable String description,
    @Description("Programming language") @Nullable String language,
    @Description("Component path") @Nullable String path
  ) {}
  
  public record Measure(
    @Description("Metric key") String metric,
    @Description("Measure value") @Nullable String value,
    @Description("Historical period values") @Nullable List<Period> periods
  ) {}
  
  public record Period(
    @Description("Period value") String value
  ) {}
  
  public record Metric(
    @Description("Metric key") String key,
    @Description("Metric display name") String name,
    @Description("Metric description") String description,
    @Description("Metric domain/category") String domain,
    @Description("Metric value type") String type,
    @Description("Whether higher values are better") boolean higherValuesAreBetter,
    @Description("Whether this is a qualitative metric") boolean qualitative,
    @Description("Whether the metric is hidden") boolean hidden,
    @Description("Whether this is a custom metric") boolean custom
  ) {}
}

