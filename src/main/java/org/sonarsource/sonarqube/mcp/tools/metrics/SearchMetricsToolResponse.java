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
package org.sonarsource.sonarqube.mcp.tools.metrics;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record SearchMetricsToolResponse(
  @JsonPropertyDescription("List of metrics matching the search") List<Metric> metrics,
  @JsonPropertyDescription("Total number of metrics") int total,
  @JsonPropertyDescription("Current page number") int page,
  @JsonPropertyDescription("Number of items per page") int pageSize
) {
  
  public record Metric(
    @JsonPropertyDescription("Metric unique identifier") String id,
    @JsonPropertyDescription("Metric key") String key,
    @JsonPropertyDescription("Metric display name") String name,
    @JsonPropertyDescription("Metric description") String description,
    @JsonPropertyDescription("Metric domain/category") String domain,
    @JsonPropertyDescription("Metric value type") String type,
    @JsonPropertyDescription("Direction for metric improvement") int direction,
    @JsonPropertyDescription("Whether this is a qualitative metric") boolean qualitative,
    @JsonPropertyDescription("Whether the metric is hidden") boolean hidden,
    @JsonPropertyDescription("Whether this is a custom metric") boolean custom
  ) {}
}


