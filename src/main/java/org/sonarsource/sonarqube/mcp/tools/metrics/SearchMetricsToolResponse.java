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
import org.sonarsource.sonarqube.mcp.tools.Description;

public record SearchMetricsToolResponse(
  @Description("List of metrics matching the search") List<Metric> metrics,
  @Description("Total number of metrics") int total,
  @Description("Current page number") int page,
  @Description("Number of items per page") int pageSize
) {
  
  public record Metric(
    @Description("Metric unique identifier") String id,
    @Description("Metric key") String key,
    @Description("Metric display name") String name,
    @Description("Metric description") String description,
    @Description("Metric domain/category") String domain,
    @Description("Metric value type") String type,
    @Description("Direction for metric improvement") int direction,
    @Description("Whether this is a qualitative metric") boolean qualitative,
    @Description("Whether the metric is hidden") boolean hidden,
    @Description("Whether this is a custom metric") boolean custom
  ) {}
}


