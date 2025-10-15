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

import org.sonarsource.sonarqube.mcp.serverapi.ServerApi;
import org.sonarsource.sonarqube.mcp.serverapi.metrics.response.SearchMetricsResponse;
import org.sonarsource.sonarqube.mcp.tools.SchemaToolBuilder;
import org.sonarsource.sonarqube.mcp.tools.Tool;

public class SearchMetricsTool extends Tool {

  public static final String TOOL_NAME = "search_metrics";
  public static final String PAGE_PROPERTY = "p";
  public static final String PAGE_SIZE_PROPERTY = "ps";

  private final ServerApi serverApi;

  public SearchMetricsTool(ServerApi serverApi) {
    super(new SchemaToolBuilder()
      .setName(TOOL_NAME)
      .setTitle("Search SonarQube Metrics")
      .setDescription("Search for SonarQube metrics")
      .addNumberProperty(PAGE_PROPERTY, "1-based page number (default: 1)")
      .addNumberProperty(PAGE_SIZE_PROPERTY, "Page size. Must be greater than 0 and less than or equal to 500 (default: 100)")
      .build());
    this.serverApi = serverApi;
  }

  @Override
  public Tool.Result execute(Tool.Arguments arguments) {
    var page = arguments.getOptionalInteger(PAGE_PROPERTY);
    var pageSize = arguments.getOptionalInteger(PAGE_SIZE_PROPERTY);
    
    var response = serverApi.metricsApi().searchMetrics(page, pageSize);
    return Tool.Result.success(buildResponseFromSearchMetrics(response));
  }

  private static String buildResponseFromSearchMetrics(SearchMetricsResponse response) {
    var stringBuilder = new StringBuilder();
    var metrics = response.metrics();

    stringBuilder.append("Search Results: ").append(response.total()).append(" total metrics\n");
    stringBuilder.append("Page: ").append(response.p()).append(" | Page Size: ").append(response.ps()).append("\n\n");

    if (metrics == null || metrics.isEmpty()) {
      stringBuilder.append("No metrics found.");
      return stringBuilder.toString();
    }

    stringBuilder.append("Metrics:\n");
    for (var metric : metrics) {
      stringBuilder.append("  - ").append(metric.name()).append(" (").append(metric.key()).append(")\n");
      stringBuilder.append("    ID: ").append(metric.id()).append("\n");
      stringBuilder.append("    Description: ").append(metric.description()).append("\n");
      stringBuilder.append("    Domain: ").append(metric.domain()).append("\n");
      stringBuilder.append("    Type: ").append(metric.type()).append("\n");
      stringBuilder.append("    Direction: ").append(getDirectionDescription(metric.direction())).append("\n");
      stringBuilder.append("    Qualitative: ").append(metric.qualitative()).append("\n");
      stringBuilder.append("    Hidden: ").append(metric.hidden()).append("\n");
      stringBuilder.append("    Custom: ").append(metric.custom()).append("\n");
      stringBuilder.append("\n");
    }

    return stringBuilder.toString().trim();
  }

  private static String getDirectionDescription(int direction) {
    return switch (direction) {
      case -1 -> "-1 (lower values are better)";
      case 0 -> "0 (no direction)";
      case 1 -> "1 (higher values are better)";
      default -> String.valueOf(direction);
    };
  }

}
