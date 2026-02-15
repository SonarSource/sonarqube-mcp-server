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
package org.sonarsource.sonarqube.mcp.tools.measures;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.Nullable;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetComponentMeasuresToolResponse(
  @JsonPropertyDescription("Component information") Component component,
  @JsonPropertyDescription("List of measures for the component") List<Measure> measures,
  @JsonPropertyDescription("Metadata about the metrics") @Nullable List<Metric> metrics
) {
  
  public record Component(
    @JsonPropertyDescription("Component key") String key,
    @JsonPropertyDescription("Component display name") String name,
    @JsonPropertyDescription("Component qualifier (TRK for project, FIL for file, etc.)") String qualifier,
    @JsonPropertyDescription("Component description") @Nullable String description,
    @JsonPropertyDescription("Programming language") @Nullable String language,
    @JsonPropertyDescription("Component path") @Nullable String path
  ) {}
  
  public record Measure(
    @JsonPropertyDescription("Metric key") String metric,
    @JsonPropertyDescription("Measure value") @Nullable String value
  ) {}
  
  public record Metric(
    @JsonPropertyDescription("Metric key") String key,
    @JsonPropertyDescription("Metric display name") String name,
    @JsonPropertyDescription("Metric description") String description,
    @JsonPropertyDescription("Metric domain/category") String domain,
    @JsonPropertyDescription("Metric value type") String type,
    @JsonPropertyDescription("Whether the metric is hidden") boolean hidden,
    @JsonPropertyDescription("Whether this is a custom metric") boolean custom
  ) {}
}

